
const express = require('express');
const pool = require('../config/database');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { mutation } = require('../idempotency');
const planner = require('../groupPlanner');

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

// POST /api/groups
// Lecturer: create the next group (G15, G16, ...) with capacity 15.
// Send Idempotency-Key so a retry after a lost reply does not create a second group.
router.post('/', requireRole('LECTURER'), mutation('Group creation', async (req, connection) => {
  const created = await planner.createNextGroup(connection);
  return {
    status: 201,
    body: { message: `Group ${created.group_code} created.`, ...created, groups: await planner.listGroups(connection) }
  };
}));

// GET /api/groups/auto-assign/preview
// Lecturer: what auto-assign WOULD do right now. Changes nothing.
router.get('/auto-assign/preview', requireRole('LECTURER'), async (req, res) => {
  try {
    const state = await planner.readState(pool, false);
    const result = planner.plan(state.groups, state.unassigned);
    res.json({
      unassigned: state.unassigned.length,
      placements: result.placements,
      new_groups: result.newGroups,
      fill_limit: planner.AUTO_FILL_LIMIT
    });
  } catch (error) {
    console.error('Auto-assign preview failed:', error.message);
    res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// POST /api/groups/auto-assign
// Lecturer: place every student with no group into groups of up to 15, creating groups as needed.
// One transaction: group rows are locked first, so it cannot overfill a group even if
// someone assigns a student at the same moment. Send Idempotency-Key: a retry replays the result.
router.post('/auto-assign', requireRole('LECTURER'), mutation('Auto-assign', async (req, connection) => {
  const result = await planner.autoAssign(connection);
  return {
    status: 200,
    body: {
      message: result.assigned ? `${result.assigned} students placed.` : 'There are no unassigned students.',
      ...result,
      groups: await planner.listGroups(connection)
    }
  };
}));

// GET /api/groups/export.xlsx
// Lecturer: Excel workbook with a Summary sheet and one sheet per group (number, name, programme).
router.get('/export.xlsx', requireRole('LECTURER'), async (req, res) => {
  try {
    const file = await planner.buildWorkbook(pool);
    const day = new Date().toISOString().slice(0, 10);
    res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    res.setHeader('Content-Disposition', `attachment; filename="MUStudentSync-groups-${day}.xlsx"`);
    res.setHeader('Cache-Control', 'no-store');
    res.send(file);
  } catch (error) {
    console.error('Group export failed:', error.message);
    res.status(500).json({ error: 'EXPORT_FAILED' });
  }
});

module.exports = router;
