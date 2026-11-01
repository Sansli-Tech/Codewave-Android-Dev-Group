
const express = require('express');
const pool = require('../config/database');
const { authenticateToken } = require('../middleware/auth');

const router = express.Router();

router.use(authenticateToken);

// GET /api/sync/changes?since=0&limit=100
router.get('/changes', async (req, res) => {
  try {
    const since = req.query.since === undefined
      ? 0
      : Number(req.query.since);

    const requestedLimit = req.query.limit === undefined
      ? 100
      : Number(req.query.limit);

    if (
      !Number.isSafeInteger(since) ||
      since < 0 ||
      !Number.isSafeInteger(requestedLimit) ||
      requestedLimit < 1
    ) {
      return res.status(400).json({
        error: 'INVALID_SYNC_CURSOR',
        message: 'since must be a non-negative integer and limit must be positive.'
      });
    }

    const limit = Math.min(requestedLimit, 200);
    const role = req.user.role;

    if (role === 'STUDENT' && !req.user.studentId) {
      return res.status(403).json({
        error: 'STUDENT_ID_MISSING',
        message: 'This student account is not linked to a student record.'
      });
    }

    if (role !== 'STUDENT' && role !== 'LECTURER') {
      return res.status(403).json({
        error: 'FORBIDDEN',
        message: 'Your account cannot synchronize student records.'
      });
    }

    // Read the global change stream so every client can advance its cursor.
    // Student-specific filtering happens before any event is returned.
    const [rows] = await pool.execute(
      `SELECT
         sc.change_id,
         sc.student_id,
         sc.record_version,
         sc.change_type,
         sc.created_at
       FROM sync_changes sc
       WHERE sc.change_id > ?
       ORDER BY sc.change_id ASC
       LIMIT ?`,
      [since, limit + 1]
    );

    const hasMore = rows.length > limit;
    const page = rows.slice(0, limit);

    // Advance through every scanned global event, including events hidden
    // from students. Hidden event details are never returned to students.
    const nextSince = page.length > 0
      ? page[page.length - 1].change_id
      : since;

    const visibleRows = role === 'STUDENT'
      ? page.filter(row => row.student_id === req.user.studentId)
      : page;

    // Fetch current active records for visible UPSERT events only.
    const studentIds = [
      ...new Set(
        visibleRows
          .filter(row => row.change_type === 'UPSERT')
          .map(row => row.student_id)
      )
    ];

    let studentsById = new Map();

   if (studentIds.length > 0) {
  const placeholders = studentIds.map(() => '?').join(', ');

  const [students] = await pool.execute(
    `SELECT s.student_id, s.student_number, s.student_name,
            p.programme_code, g.group_code,
            s.record_version, s.updated_at
     FROM students s
     JOIN programmes p ON p.programme_id = s.programme_id
     LEFT JOIN lab_groups g ON g.group_id = s.group_id
     WHERE s.is_deleted = 0 AND s.student_id IN (${placeholders})`,
    studentIds
  );

  studentsById = new Map(
    students.map(student => [student.student_id, student])
  );
}

    const changes = [];

    for (const row of visibleRows) {
      if (row.change_type === 'DELETE') {
        changes.push({
          changeId: row.change_id,
          studentId: row.student_id,
          recordVersion: row.record_version,
          changeType: 'DELETE',
          createdAt: row.created_at
        });

        continue;
      }

      const student = studentsById.get(row.student_id);

      // Do not resurrect a record that has since been deleted.
      if (!student) {
        continue;
      }

      changes.push({
        changeId: row.change_id,
        studentId: row.student_id,
        recordVersion: row.record_version,
        changeType: 'UPSERT',
        createdAt: row.created_at,
        student: {
          studentId: student.student_id,
          studentNumber: student.student_number,
          studentName: student.student_name,
          programmeCode: student.programme_code,
          groupCode: student.group_code,
          recordVersion: student.record_version,
          updatedAt: student.updated_at
        }
      });
    }

    return res.json({
      success: true,
      changes,
      sync: {
        since,
        nextSince,
        limit,
        hasMore
      }
    });
  } catch (error) {
    console.error('Synchronization failed:', error.message);

    return res.status(500).json({
      error: 'SYNC_FAILED',
      message: 'Unable to retrieve synchronization changes.'
    });
  }
});

module.exports = router;
