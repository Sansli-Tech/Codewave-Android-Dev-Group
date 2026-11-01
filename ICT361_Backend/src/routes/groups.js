
const express = require('express');
const pool = require('../config/database');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { mutation } = require('../idempotency');

const router = express.Router();

router.use(authenticateToken);

// GET /api/groups — list groups and active student counts.
// Students may read this too: it holds only group codes and numbers, no names,
// so a student can see whether a group has room before requesting a place.
router.get('/', async (req, res) => {
  try {
    const [groups] = await pool.execute(`
      SELECT
        g.group_id,
        g.group_code,
        g.capacity,
        COUNT(s.student_id) AS active_students,
        g.capacity - COUNT(s.student_id) AS available_places
      FROM lab_groups g
      LEFT JOIN students s
        ON s.group_id = g.group_id
       AND s.is_deleted = FALSE
      GROUP BY g.group_id, g.group_code, g.capacity
      ORDER BY g.group_code
    `);

    res.json({ groups });
  } catch (error) {
    console.error('Group listing failed:', error.message);
    res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// POST /api/groups/:groupCode/assign
// Assign or transfer an active student, enforcing capacity transactionally.
// Locking the group row first makes two simultaneous requests for the last
// place run one after the other: exactly one gets it, the other GROUP_FULL
// (Challenge 1). A failure leaves the student's previous group untouched.
router.post('/:groupCode/assign', requireRole('LECTURER'), mutation('Group assignment', async (req, connection) => {
  const { groupCode } = req.params;
  const { student_id: studentId } = req.body || {};

  if (typeof studentId !== 'string' || !/^[0-9a-f-]{36}$/i.test(studentId)) {
    return {
      status: 400,
      body: { error: 'INVALID_STUDENT_ID', message: 'Provide a valid student_id.' }
    };
  }

  // Lock the group row so concurrent assignments to this group serialise.
  const [groups] = await connection.execute(
    `SELECT group_id, group_code, capacity
     FROM lab_groups WHERE group_code = ? FOR UPDATE`,
    [groupCode]
  );
  if (groups.length === 0) {
    return { status: 404, body: { error: 'GROUP_NOT_FOUND' } };
  }
  const group = groups[0];

  const [students] = await connection.execute(
    `SELECT student_id, group_id
     FROM students WHERE student_id = ? AND is_deleted = FALSE FOR UPDATE`,
    [studentId]
  );
  if (students.length === 0) {
    return { status: 404, body: { error: 'ACTIVE_STUDENT_NOT_FOUND' } };
  }

  const [countRows] = await connection.execute(
    // Locking read: sees the latest committed members, never an older snapshot.
    'SELECT COUNT(*) AS total FROM students WHERE group_id = ? AND is_deleted = FALSE LOCK IN SHARE MODE',
    [group.group_id]
  );
  const currentCount = Number(countRows[0].total);

  if (students[0].group_id === group.group_id) {
    return {
      status: 200,
      body: {
        message: 'Student is already assigned to this group.',
        group_code: group.group_code,
        active_students: currentCount,
        capacity: group.capacity
      }
    };
  }

  if (currentCount >= group.capacity) {
    return {
      status: 409,
      body: {
        error: 'GROUP_FULL',
        message: 'This lab group has reached its capacity.',
        group_code: group.group_code,
        capacity: group.capacity
      }
    };
  }

  await connection.execute(
    'UPDATE students SET group_id = ?, record_version = record_version + 1 WHERE student_id = ?',
    [group.group_id, studentId]
  );
  const [versionRows] = await connection.execute(
    'SELECT record_version FROM students WHERE student_id = ?',
    [studentId]
  );
  await connection.execute(
    `INSERT INTO sync_changes (student_id, record_version, change_type)
     VALUES (?, ?, 'UPSERT')`,
    [studentId, versionRows[0].record_version]
  );

  return {
    status: 200,
    body: {
      message: 'Student assigned successfully.',
      student_id: studentId,
      group_code: group.group_code,
      active_students: currentCount + 1,
      capacity: group.capacity,
      record_version: versionRows[0].record_version
    }
  };
}));

module.exports = router;
