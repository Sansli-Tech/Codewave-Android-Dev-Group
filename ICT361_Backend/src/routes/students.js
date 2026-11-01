const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const express = require('express');
const { randomUUID } = require('crypto');
const pool = require('../config/database');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { mutation } = require('../idempotency');

const router = express.Router();
router.use(authenticateToken);

function validateStudent(body) {
  if (!body || typeof body !== 'object') {
    return 'Provide valid student details.';
  }

  const name = body.student_name;
  const number = body.student_number;
  const programme = body.programme_code;

  if (
    typeof name !== 'string' ||
    name.trim().length < 2 ||
    name.trim().length > 100 ||
    !/^[\p{L}\p{M} .'-]+$/u.test(name.trim())
  ) {
    return 'Student name must be 2–100 characters and contain valid name characters.';
  }

  if (
    typeof number !== 'string' ||
    !/^[0-9]{9}$/.test(number.trim())
  ) {
    return 'Student number must contain exactly nine digits.';
  }

  if (
    typeof programme !== 'string' ||
    !programme.trim()
  ) {
    return 'Provide a valid programme code.';
  }

  const groupCode = body.group_code;

  if (
    groupCode !== undefined &&
    groupCode !== null &&
    groupCode !== '' &&
    typeof groupCode !== 'string'
  ) {
    return 'Provide a valid group code or leave it empty.';
  }

  return null;
}

// Resolve a group and enforce the 15-active-student limit.
// Call inside a transaction.
async function resolveGroup(connection, groupCode, studentId) {
  if (
    groupCode === undefined ||
    groupCode === null ||
    groupCode === ''
  ) {
    return { groupId: null };
  }

  if (typeof groupCode !== 'string') {
    return {
      error: {
        status: 400,
        body: { error: 'INVALID_GROUP' }
      }
    };
  }

  const [groups] = await connection.execute(
        `SELECT group_id, capacity
     FROM lab_groups
     WHERE group_code = ?
     FOR UPDATE`,
    [groupCode.trim()]
  );

  if (groups.length === 0) {
    return {
      error: {
        status: 400,
        body: { error: 'INVALID_GROUP' }
      }
    };
  }

  const [countRows] = await connection.execute(
    `SELECT COUNT(*) AS total
     FROM students
     WHERE group_id = ?
       AND is_deleted = FALSE
       AND student_id <> ?`,
    [groups[0].group_id, studentId || '']
  );

   const capacity = Number(groups[0].capacity);

  if (Number(countRows[0].total) >= capacity) {
    return {
      error: {
        status: 409,
        body: {
          error: 'GROUP_FULL',
          message: `That group already has ${capacity} active students.`,
          capacity
        }
      }
    };
  }

  return { groupId: groups[0].group_id };
}

// GET /api/students
// Lecturer: list, search and filter students.
router.get('/', requireRole('LECTURER'), async (req, res) => {
  const {
    search = '',
    programme = '',
    group = '',
    page = '1',
    limit = '20'
  } = req.query;

  const pageNumber = Number(page);
  const pageSize = Number(limit);

  if (
    !Number.isInteger(pageNumber) ||
    pageNumber < 1 ||
    !Number.isInteger(pageSize) ||
    pageSize < 1 ||
    pageSize > 100 ||
    typeof search !== 'string' ||
    typeof programme !== 'string' ||
    typeof group !== 'string'
  ) {
    return res.status(400).json({ error: 'INVALID_QUERY' });
  }

  if (
    group &&
    group !== 'UNASSIGNED' &&
    !/^G[0-9]{2}$/.test(group)
  ) {
    return res.status(400).json({ error: 'INVALID_GROUP' });
  }

  const where = ['s.is_deleted = FALSE'];
  const params = [];

  if (search.trim()) {
    where.push(
      '(s.student_name LIKE ? OR s.student_number LIKE ?)'
    );
    const term = `%${search.trim()}%`;
    params.push(term, term);
  }

  if (programme.trim()) {
    where.push('p.programme_code = ?');
    params.push(programme.trim());
  }

  if (group === 'UNASSIGNED') {
    where.push('s.group_id IS NULL');
  } else if (group) {
    where.push('g.group_code = ?');
    params.push(group);
  }

  try {
    const whereSql = where.join(' AND ');

    const [countRows] = await pool.execute(
      `SELECT COUNT(*) AS total
       FROM students s
       JOIN programmes p ON p.programme_id = s.programme_id
       LEFT JOIN lab_groups g ON g.group_id = s.group_id
       WHERE ${whereSql}`,
      params
    );

    const offset = (pageNumber - 1) * pageSize;

    const [students] = await pool.execute(
      `SELECT s.student_id, s.student_number,
              s.student_name, p.programme_code,
              g.group_code, s.record_version,
              s.created_at, s.updated_at
       FROM students s
       JOIN programmes p ON p.programme_id = s.programme_id
       LEFT JOIN lab_groups g ON g.group_id = s.group_id
       WHERE ${whereSql}
       ORDER BY s.student_name, s.student_id
       LIMIT ? OFFSET ?`,
      [...params, pageSize, offset]
    );

    return res.json({
      data: students,
      pagination: {
        page: pageNumber,
        limit: pageSize,
        total: countRows[0].total
      }
    });
  } catch (error) {
    console.error('Student listing error:', error.message);
    return res.status(500).json({
      error: 'INTERNAL_SERVER_ERROR'
    });
  }
});

// The current server copy of a student, sent back with VERSION_CONFLICT so the
// app can show it for review instead of silently overwriting (Activity E).
async function currentStudent(connection, studentId) {
  const [rows] = await connection.execute(
    `SELECT s.student_id, s.student_number, s.student_name,
            p.programme_code, g.group_code, s.record_version, s.updated_at
     FROM students s
     JOIN programmes p ON p.programme_id = s.programme_id
     LEFT JOIN lab_groups g ON g.group_id = s.group_id
     WHERE s.student_id = ? AND s.is_deleted = FALSE`,
    [studentId]
  );
  return rows[0] || null;
}

// POST /api/students
// Lecturer: create a student and record its sync event.
// Send header Idempotency-Key: <uuid> so a retry after a lost response
// returns the first result instead of failing as a duplicate (Challenge 2).
router.post('/', requireRole('LECTURER'), mutation('Student creation', async (req, connection) => {
  const validationError = validateStudent(req.body);
  if (validationError) {
    return { status: 400, body: { error: 'VALIDATION_ERROR', message: validationError } };
  }

  const studentId = randomUUID();
  const { student_name, student_number, programme_code } = req.body;

  const [programmes] = await connection.execute(
    'SELECT programme_id FROM programmes WHERE programme_code = ?',
    [programme_code.trim()]
  );
  if (!programmes.length) {
    return { status: 400, body: { error: 'INVALID_PROGRAMME' } };
  }

  const group = await resolveGroup(connection, req.body.group_code, studentId);
  if (group.error) return group.error;

  try {
    await connection.execute(
      `INSERT INTO students
       (student_id, student_number, student_name, programme_id, group_id)
       VALUES (?, ?, ?, ?, ?)`,
      [studentId, student_number.trim(), student_name.trim(), programmes[0].programme_id, group.groupId]
    );
  } catch (error) {
    if (error.code === 'ER_DUP_ENTRY') {
      return {
        status: 409,
        body: { error: 'DUPLICATE_STUDENT_NUMBER', message: 'This student number is already registered.' }
      };
    }
    throw error;
  }

  await connection.execute(
    `INSERT INTO sync_changes (student_id, record_version, change_type)
     VALUES (?, ?, 'UPSERT')`,
    [studentId, 1]
  );

  return { status: 201, body: { message: 'Student profile created.', student_id: studentId } };
}));

// GET /api/students/:id
// Lecturer: view one active student.
router.get('/:id', requireRole('LECTURER'), async (req, res) => {
  try {
    const [rows] = await pool.execute(
      `SELECT s.student_id, s.student_number,
              s.student_name, p.programme_code,
              g.group_code, s.record_version
       FROM students s
       JOIN programmes p ON p.programme_id = s.programme_id
       LEFT JOIN lab_groups g ON g.group_id = s.group_id
       WHERE s.student_id = ?
         AND s.is_deleted = FALSE`,
      [req.params.id]
    );

    if (!rows.length) {
      return res.status(404).json({
        error: 'STUDENT_NOT_FOUND'
      });
    }

    return res.json({ data: rows[0] });
  } catch (error) {
    console.error('Student lookup error:', error.message);
    return res.status(500).json({
      error: 'INTERNAL_SERVER_ERROR'
    });
  }
});

// PUT /api/students/:id
// Update a student using optimistic version checking.
router.put('/:id', requireRole('LECTURER'), mutation('Student update', async (req, connection) => {
  const studentId = req.params.id;
  const expectedVersion = req.body?.expected_version;
  const validationError = validateStudent(req.body);

  if (validationError) {
    return { status: 400, body: { error: 'VALIDATION_ERROR', message: validationError } };
  }
  if (!Number.isInteger(expectedVersion) || expectedVersion < 1) {
    return {
      status: 400,
      body: { error: 'EXPECTED_VERSION_REQUIRED', message: 'Provide the current expected_version.' }
    };
  }

  const [students] = await connection.execute(
    `SELECT student_id, record_version, is_deleted
     FROM students WHERE student_id = ? FOR UPDATE`,
    [studentId]
  );
  if (!students.length) {
    return { status: 404, body: { error: 'STUDENT_NOT_FOUND' } };
  }
  // A stale offline edit must never bring a deleted student back.
  if (students[0].is_deleted) {
    return { status: 410, body: { error: 'STUDENT_DELETED', message: 'This student was deleted.' } };
  }
  if (Number(students[0].record_version) !== expectedVersion) {
    return {
      status: 409,
      body: {
        error: 'VERSION_CONFLICT',
        message: 'The student record has changed. Reload it before editing.',
        current_version: students[0].record_version,
        current: await currentStudent(connection, studentId)
      }
    };
  }

  const [programmes] = await connection.execute(
    'SELECT programme_id FROM programmes WHERE programme_code = ?',
    [req.body.programme_code.trim()]
  );
  if (!programmes.length) {
    return { status: 400, body: { error: 'INVALID_PROGRAMME' } };
  }

  const group = await resolveGroup(connection, req.body.group_code, studentId);
  if (group.error) return group.error;

  const newVersion = expectedVersion + 1;
  try {
    await connection.execute(
      `UPDATE students
       SET student_name = ?, student_number = ?, programme_id = ?,
           group_id = ?, record_version = ?
       WHERE student_id = ?`,
      [
        req.body.student_name.trim(),
        req.body.student_number.trim(),
        programmes[0].programme_id,
        group.groupId,
        newVersion,
        studentId
      ]
    );
  } catch (error) {
    if (error.code === 'ER_DUP_ENTRY') {
      return {
        status: 409,
        body: { error: 'DUPLICATE_STUDENT_NUMBER', message: 'That student number is already in use.' }
      };
    }
    throw error;
  }

  await connection.execute(
    `INSERT INTO sync_changes (student_id, record_version, change_type)
     VALUES (?, ?, 'UPSERT')`,
    [studentId, newVersion]
  );

  return {
    status: 200,
    body: { message: 'Student updated successfully.', student_id: studentId, record_version: newVersion }
  };
}));

// DELETE /api/students/:id
// Soft delete: hide the record, release its place once, disable its account,
// keep the row as a deletion marker for sync. The number stays reserved
// because the row (and its UNIQUE student_number) is never removed.
router.delete('/:id', requireRole('LECTURER'), mutation('Student deletion', async (req, connection) => {
  const studentId = req.params.id;
  const expectedVersion = req.body?.expected_version;

  if (!Number.isInteger(expectedVersion) || expectedVersion < 1) {
    return {
      status: 400,
      body: { error: 'EXPECTED_VERSION_REQUIRED', message: 'Provide the current expected_version.' }
    };
  }

  const [students] = await connection.execute(
    `SELECT student_id, record_version, is_deleted
     FROM students WHERE student_id = ? FOR UPDATE`,
    [studentId]
  );
  if (!students.length) {
    return { status: 404, body: { error: 'STUDENT_NOT_FOUND' } };
  }
  // Repeated delete: nothing changes and no second place is released.
  if (students[0].is_deleted) {
    return {
      status: 200,
      body: {
        message: 'Student was already deleted.',
        student_id: studentId,
        record_version: students[0].record_version,
        already_deleted: true
      }
    };
  }
  if (Number(students[0].record_version) !== expectedVersion) {
    return {
      status: 409,
      body: {
        error: 'VERSION_CONFLICT',
        message: 'The student record has changed. Reload it before deleting.',
        current_version: students[0].record_version,
        current: await currentStudent(connection, studentId)
      }
    };
  }

  const newVersion = expectedVersion + 1;
  await connection.execute(
    `UPDATE students
     SET is_deleted = TRUE, deleted_at = CURRENT_TIMESTAMP, record_version = ?
     WHERE student_id = ?`,
    [newVersion, studentId]
  );
  await connection.execute(
    'UPDATE accounts SET is_active = 0 WHERE student_id = ?',
    [studentId]
  );
  await connection.execute(
    `INSERT INTO sync_changes (student_id, record_version, change_type)
     VALUES (?, ?, 'DELETE')`,
    [studentId, newVersion]
  );

  return {
    status: 200,
    body: {
      message: 'Student soft-deleted successfully.',
      student_id: studentId,
      record_version: newVersion,
      already_deleted: false
    }
  };
}));

// POST /api/students/:id/claim-code
// Generate a one-time, 15-minute claim code.
router.post(
  '/:id/claim-code',
  requireRole('LECTURER'),
  async (req, res) => {
    const studentId = req.params.id;
    let connection;
    let transactionOpen = false;

    try {
      connection = await pool.getConnection();
      await connection.beginTransaction();
      transactionOpen = true;

      const [students] = await connection.execute(
        `SELECT student_id
         FROM students
         WHERE student_id = ?
           AND is_deleted = FALSE
         FOR UPDATE`,
        [studentId]
      );

      if (!students.length) {
        await connection.rollback();
        transactionOpen = false;

        return res.status(404).json({
          error: 'ACTIVE_STUDENT_NOT_FOUND'
        });
      }

      const [existing] = await connection.execute(
        `SELECT claim_id, claimed_at
         FROM student_claim_codes
         WHERE student_id = ?
         FOR UPDATE`,
        [studentId]
      );

      if (
        existing.length > 0 &&
        existing[0].claimed_at !== null
      ) {
        await connection.rollback();
        transactionOpen = false;

        return res.status(409).json({
          error: 'STUDENT_ALREADY_CLAIMED'
        });
      }

      const claimCode = crypto
        .randomBytes(8)
        .toString('hex')
        .toUpperCase();

      const claimCodeHash = await bcrypt.hash(claimCode, 12);

      if (existing.length > 0) {
        await connection.execute(
          `UPDATE student_claim_codes
           SET claim_code_hash = ?,
               expires_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 15 MINUTE)
           WHERE student_id = ?
             AND claimed_at IS NULL`,
          [claimCodeHash, studentId]
        );
      } else {
        await connection.execute(
          `INSERT INTO student_claim_codes
           (student_id, claim_code_hash, expires_at)
           VALUES (?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 15 MINUTE))`,
          [studentId, claimCodeHash]
        );
      }

      await connection.commit();
      transactionOpen = false;

      return res.status(201).json({
        message: 'Claim code generated. Share it securely with the student.',
        student_id: studentId,
        claim_code: claimCode,
        expires_in_minutes: 15
      });
    } catch (error) {
      if (connection && transactionOpen) {
        try {
          await connection.rollback();
        } catch (rollbackError) {
          console.error(
            'Claim-code rollback error:',
            rollbackError.message
          );
        }
      }

      console.error('Claim-code generation failed:', error.message);
      return res.status(500).json({
        error: 'INTERNAL_SERVER_ERROR'
      });
    } finally {
      if (connection) connection.release();
    }
  }
);

module.exports = router;