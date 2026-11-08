const express = require('express');
const crypto = require('crypto');
const pool = require('../config/database');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { mutation } = require('../idempotency');
const inbox = require('../notifications');

// Student self-service. The student is ALWAYS the one in the verified token (req.user.studentId),
// never an ID from the URL or body, so a student can only ever see or change their own record.
const router = express.Router();
router.use(inbox.schemaReady, authenticateToken, requireRole('STUDENT'));

const NAME_RE = /^[\p{L}\p{M} .'-]+$/u;

async function profile(db, studentId) {
  const [rows] = await db.execute(
    `SELECT s.student_id, s.student_number, s.student_name,
            p.programme_code, p.programme_name, g.group_code,
            s.record_version, s.updated_at
     FROM students s
     JOIN programmes p ON p.programme_id = s.programme_id
     LEFT JOIN lab_groups g ON g.group_id = s.group_id
     WHERE s.student_id = ? AND s.is_deleted = FALSE`,
    [studentId]
  );
  const student = rows[0] || null;
  let group = null;
  if (student && student.group_code) {
    const [g] = await db.execute(
      `SELECT g.group_code, g.capacity, COUNT(s.student_id) AS active_students
       FROM lab_groups g
       LEFT JOIN students s ON s.group_id = g.group_id AND s.is_deleted = FALSE
       WHERE g.group_code = ?
       GROUP BY g.group_id, g.group_code, g.capacity`,
      [student.group_code]
    );
    group = g[0] || null;
  }
  return { student, group };
}

async function latestCorrection(db, studentId) {
  const [rows] = await db.execute(
    `SELECT request_id, requested_number, status, note, created_at, resolved_at
     FROM change_requests WHERE student_id = ?
     ORDER BY created_at DESC LIMIT 1`,
    [studentId]
  );
  return rows[0] || null;
}

async function latestGroupRequest(db, studentId) {
  const [rows] = await db.execute(
    `SELECT request_id, group_code, status, note, created_at, resolved_at
     FROM group_requests WHERE student_id = ?
     ORDER BY created_at DESC LIMIT 1`,
    [studentId]
  );
  return rows[0] || null;
}

// The student row, locked for this transaction, or an outcome to return.
async function lockOwnRow(connection, studentId, expectedVersion) {
  const [rows] = await connection.execute(
    `SELECT student_id, record_version, is_deleted, group_id
     FROM students WHERE student_id = ? FOR UPDATE`,
    [studentId]
  );
  if (!rows.length) return { error: { status: 404, body: { error: 'STUDENT_NOT_FOUND' } } };
  if (rows[0].is_deleted) {
    return { error: { status: 410, body: { error: 'STUDENT_DELETED', message: 'This profile was removed.' } } };
  }
  if (Number(rows[0].record_version) !== expectedVersion) {
    return {
      error: {
        status: 409,
        body: {
          error: 'VERSION_CONFLICT',
          message: 'Your profile changed on the server. Review it and try again.',
          current: (await profile(connection, studentId)).student
        }
      }
    };
  }
  return { row: rows[0] };
}

function versionError(v) {
  if (!Number.isInteger(v) || v < 1) {
    return { status: 400, body: { error: 'EXPECTED_VERSION_REQUIRED', message: 'Provide expected_version.' } };
  }
  return null;
}

// GET /api/me/student - own profile, confirmed group with occupancy, latest correction request.
router.get('/student', async (req, res) => {
  try {
    const out = await profile(pool, req.user.studentId);
    if (!out.student) return res.status(404).json({ error: 'STUDENT_NOT_FOUND' });
    out.correction_request = await latestCorrection(pool, req.user.studentId);
    out.group_request = await latestGroupRequest(pool, req.user.studentId);
    return res.json(out);
  } catch (error) {
    console.error('Profile lookup failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// PUT /api/me/student - a student may change ONLY their name and programme.
// Body: { student_name?, programme_code?, expected_version }
router.put('/student', mutation('Profile update', async (req, connection) => {
  const b = req.body || {};
  if ('student_number' in b || 'group_code' in b) {
    return {
      status: 403,
      body: {
        error: 'FIELD_NOT_PERMITTED',
        message: 'Request a group change or a number correction instead.'
      }
    };
  }
  const bad = versionError(b.expected_version);
  if (bad) return bad;

  const sets = [];
  const values = [];
  if (b.student_name !== undefined) {
    const name = typeof b.student_name === 'string' ? b.student_name.trim() : '';
    if (name.length < 2 || name.length > 100 || !NAME_RE.test(name)) {
      return {
        status: 400,
        body: { error: 'VALIDATION_ERROR', field: 'student_name', message: 'Name must be 2–100 characters.' }
      };
    }
    sets.push('student_name = ?');
    values.push(name);
  }
  if (b.programme_code !== undefined) {
    const [p] = await connection.execute(
      'SELECT programme_id FROM programmes WHERE programme_code = ?',
      [String(b.programme_code).trim()]
    );
    if (!p.length) return { status: 400, body: { error: 'INVALID_PROGRAMME' } };
    sets.push('programme_id = ?');
    values.push(p[0].programme_id);
  }
  if (!sets.length) {
    return { status: 400, body: { error: 'VALIDATION_ERROR', message: 'Nothing to change.' } };
  }

  const own = await lockOwnRow(connection, req.user.studentId, b.expected_version);
  if (own.error) return own.error;

  const newVersion = b.expected_version + 1;
  await connection.execute(
    `UPDATE students SET ${sets.join(', ')}, record_version = ? WHERE student_id = ?`,
    [...values, newVersion, req.user.studentId]
  );
  await connection.execute(
    `INSERT INTO sync_changes (student_id, record_version, change_type) VALUES (?, ?, 'UPSERT')`,
    [req.user.studentId, newVersion]
  );
  return { status: 200, body: await profile(connection, req.user.studentId) };
}));

// POST /api/me/group-request - ask for a place in a group. Body: { group_code }
// The student is NOT moved: the request waits for a lecturer (POST /api/group-requests/:id/resolve),
// who approves it with the usual locked capacity check, or rejects it. One pending request at a time.
// (expected_version is still accepted from older app versions and ignored.)
router.post('/group-request', mutation('Group request', async (req, connection) => {
  const b = req.body || {};
  const code = typeof b.group_code === 'string' ? b.group_code.trim().toUpperCase() : '';
  if (!/^G[0-9]{2,3}$/.test(code)) {
    return { status: 400, body: { error: 'INVALID_GROUP', message: 'Choose a lab group.' } };
  }
  const [groups] = await connection.execute('SELECT group_id FROM lab_groups WHERE group_code = ?', [code]);
  if (!groups.length) return { status: 400, body: { error: 'INVALID_GROUP', message: 'That group does not exist.' } };

  const [me] = await connection.execute(
    'SELECT student_name, group_id, is_deleted FROM students WHERE student_id = ?', [req.user.studentId]);
  if (!me.length || me[0].is_deleted) return { status: 410, body: { error: 'STUDENT_DELETED' } };
  if (me[0].group_id === groups[0].group_id) {
    return { status: 409, body: { error: 'ALREADY_IN_GROUP', message: `You are already in ${code}.` } };
  }
  // Only the request rows are locked here (lecturer approval locks request, then group, then student).
  const [pending] = await connection.execute(
    "SELECT request_id, group_code FROM group_requests WHERE student_id = ? AND status = 'PENDING' FOR UPDATE",
    [req.user.studentId]);
  if (pending.length) {
    return {
      status: 409,
      body: { error: 'REQUEST_PENDING', message: `Your request for ${pending[0].group_code} is still waiting for your lecturer.` }
    };
  }
  const id = crypto.randomUUID();
  await connection.execute(
    'INSERT INTO group_requests (request_id, student_id, group_code) VALUES (?, ?, ?)', [id, req.user.studentId, code]);
  await inbox.notify(connection, await inbox.lecturerAccounts(connection), {
    kind: 'GROUP_REQUEST',
    title: `Group request: ${code}`,
    body: `${me[0].student_name} asked for a place in ${code}.`,
    refId: id
  });
  return { status: 201, body: { request_id: id, status: 'PENDING', group_code: code } };
}));

// POST /api/me/group-request/cancel - withdraw the pending request (nothing else changes).
router.post('/group-request/cancel', mutation('Cancel group request', async (req, connection) => {
  const [rows] = await connection.execute(
    "SELECT request_id FROM group_requests WHERE student_id = ? AND status = 'PENDING' FOR UPDATE",
    [req.user.studentId]);
  if (!rows.length) return { status: 404, body: { error: 'NO_PENDING_REQUEST' } };
  await connection.execute(
    "UPDATE group_requests SET status = 'CANCELLED', resolved_at = CURRENT_TIMESTAMP WHERE request_id = ?",
    [rows[0].request_id]);
  return { status: 200, body: { request_id: rows[0].request_id, status: 'CANCELLED' } };
}));

// POST /api/me/correction-requests - ask the lecturer to correct the student number.
// Body: { requested_student_number }. The number only changes when a lecturer approves.
router.post('/correction-requests', mutation('Correction request', async (req, connection) => {
  const raw = (req.body || {}).requested_student_number;
  const number = typeof raw === 'string' ? raw.trim() : '';
  if (!/^[0-9]{9}$/.test(number)) {
    return {
      status: 400,
      body: { error: 'VALIDATION_ERROR', field: 'requested_student_number', message: 'Enter exactly nine digits.' }
    };
  }
  const [rows] = await connection.execute(
    'SELECT student_number FROM students WHERE student_id = ? AND is_deleted = FALSE FOR UPDATE',
    [req.user.studentId]
  );
  if (!rows.length) return { status: 404, body: { error: 'STUDENT_NOT_FOUND' } };
  if (rows[0].student_number === number) {
    return { status: 400, body: { error: 'SAME_NUMBER', message: 'That is already your student number.' } };
  }
  const [pending] = await connection.execute(
    "SELECT request_id FROM change_requests WHERE student_id = ? AND status = 'PENDING'",
    [req.user.studentId]
  );
  if (pending.length) {
    return {
      status: 409,
      body: { error: 'REQUEST_PENDING', message: 'You already have a correction request waiting for the lecturer.' }
    };
  }

  const id = crypto.randomUUID();
  await connection.execute(
    'INSERT INTO change_requests (request_id, student_id, requested_number) VALUES (?, ?, ?)',
    [id, req.user.studentId, number]
  );
  await inbox.notify(connection, await inbox.lecturerAccounts(connection), {
    kind: 'CORRECTION_REQUEST',
    title: 'Student number correction request',
    body: `A student asked to change their student number to ${number}.`,
    refId: id
  });
  return { status: 201, body: { request_id: id, status: 'PENDING', requested_number: number } };
}));

// GET /api/me/correction-requests - the student's own requests, newest first.
router.get('/correction-requests', async (req, res) => {
  try {
    const [rows] = await pool.execute(
      `SELECT request_id, requested_number, status, note, created_at, resolved_at
       FROM change_requests WHERE student_id = ? ORDER BY created_at DESC`,
      [req.user.studentId]
    );
    return res.json({ data: rows });
  } catch (error) {
    console.error('Correction list failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

module.exports = router;
