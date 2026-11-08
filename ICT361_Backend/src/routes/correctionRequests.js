const express = require('express');
const pool = require('../config/database');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { mutation } = require('../idempotency');
const inbox = require('../notifications');

// Lecturer side of student-number corrections. Only a lecturer can change a number,
// and only by approving a request, so a student can never take over someone else's identity.
const router = express.Router();
router.use(inbox.schemaReady, authenticateToken, requireRole('LECTURER'));

// GET /api/correction-requests - pending requests, oldest first.
router.get('/', async (req, res) => {
  try {
    const [rows] = await pool.execute(
      `SELECT r.request_id, r.student_id, s.student_name,
              s.student_number AS current_number, r.requested_number,
              r.status, r.created_at
       FROM change_requests r
       JOIN students s ON s.student_id = r.student_id
       WHERE r.status = 'PENDING' AND s.is_deleted = FALSE
       ORDER BY r.created_at`
    );
    return res.json({ data: rows });
  } catch (error) {
    console.error('Correction request list failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// POST /api/correction-requests/:id/resolve - Body: { decision: 'APPROVE' | 'REJECT', note? }
// If the new number is already taken, nothing changes and the request stays PENDING.
router.post('/:id/resolve', mutation('Correction resolve', async (req, connection) => {
  const { decision } = req.body || {};
  const note = typeof (req.body || {}).note === 'string' ? req.body.note.trim().slice(0, 255) : null;
  if (decision !== 'APPROVE' && decision !== 'REJECT') {
    return { status: 400, body: { error: 'VALIDATION_ERROR', message: 'decision must be APPROVE or REJECT.' } };
  }

  const [requests] = await connection.execute(
    'SELECT request_id, student_id, requested_number, status FROM change_requests WHERE request_id = ? FOR UPDATE',
    [req.params.id]
  );
  if (!requests.length) return { status: 404, body: { error: 'REQUEST_NOT_FOUND' } };
  const request = requests[0];
  if (request.status !== 'PENDING') {
    return { status: 409, body: { error: 'ALREADY_RESOLVED', status: request.status } };
  }

  let recordVersion = null;
  if (decision === 'APPROVE') {
    const [students] = await connection.execute(
      'SELECT record_version, is_deleted FROM students WHERE student_id = ? FOR UPDATE',
      [request.student_id]
    );
    if (!students.length || students[0].is_deleted) {
      return { status: 410, body: { error: 'STUDENT_DELETED' } };
    }
    recordVersion = Number(students[0].record_version) + 1;
    try {
      await connection.execute(
        'UPDATE students SET student_number = ?, record_version = ? WHERE student_id = ?',
        [request.requested_number, recordVersion, request.student_id]
      );
    } catch (error) {
      if (error.code === 'ER_DUP_ENTRY') {
        // Includes numbers reserved by soft-deleted students.
        return {
          status: 409,
          body: { error: 'NUMBER_TAKEN', message: 'Another student record already uses that number.' }
        };
      }
      throw error;
    }
    await connection.execute(
      `INSERT INTO sync_changes (student_id, record_version, change_type) VALUES (?, ?, 'UPSERT')`,
      [request.student_id, recordVersion]
    );
  }

  const status = decision === 'APPROVE' ? 'APPROVED' : 'REJECTED';
  await connection.execute(
    `UPDATE change_requests
     SET status = ?, note = ?, resolved_at = CURRENT_TIMESTAMP, resolved_by = ?
     WHERE request_id = ?`,
    [status, note, req.user.accountId, request.request_id]
  );
  await inbox.notify(connection, [await inbox.studentAccount(connection, request.student_id)], {
    kind: decision === 'APPROVE' ? 'CORRECTION_APPROVED' : 'CORRECTION_REJECTED',
    title: decision === 'APPROVE' ? 'Student number corrected' : 'Number correction not approved',
    body: decision === 'APPROVE'
      ? `Your student number is now ${request.requested_number}.${note ? ' Your lecturer said: ' + note : ''}`
      : (note ? `Your lecturer said: ${note}` : 'Your student number was not changed.'),
    refId: request.request_id
  });
  return {
    status: 200,
    body: { request_id: request.request_id, status, student_id: request.student_id, record_version: recordVersion }
  };
}));

module.exports = router;
