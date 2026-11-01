
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
      !Number.isInteger(requestedLimit) ||
      requestedLimit < 1
    ) {
      return res.status(400).json({
        error: 'INVALID_SYNC_CURSOR',
        message: 'since must be a non-negative integer and limit must be positive.'
      });
    }

    const limit = Math.min(requestedLimit, 200);
    const role = req.user.role;

    // Students may only synchronize their own student record.
    // Lecturers may synchronize all student records.
    let sql = `
      SELECT
        sc.change_id,
        sc.student_id,
        sc.record_version,
        sc.change_type,
        sc.created_at,
        s.student_number,
        s.student_name,
        s.programme_id,
        s.group_id,
        s.updated_at
      FROM sync_changes sc
      LEFT JOIN students s
        ON s.student_id = sc.student_id
        AND s.record_version = sc.record_version
        AND s.is_deleted = 0
      WHERE sc.change_id > ?
    `;

    const params = [since];

    if (role === 'STUDENT') {
      const studentId = req.user.student_id || req.user.studentId;

      if (!studentId) {
        return res.status(403).json({
          error: 'STUDENT_ID_MISSING',
          message: 'The authenticated student account is not linked to a student record.'
        });
      }

      sql += ' AND sc.student_id = ?';
      params.push(studentId);
    } else if (role !== 'LECTURER') {
      return res.status(403).json({
        error: 'FORBIDDEN',
        message: 'Your account cannot synchronize student records.'
      });
    }

    sql += ' ORDER BY sc.change_id ASC LIMIT ?';
    params.push(limit);

    const [rows] = await pool.execute(sql, params);

    const changes = [];

    for (const row of rows) {
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

      // Ignore outdated UPSERT events. A newer version or deletion
      // may already have superseded this event.
      if (!row.student_number) {
        continue;
      }

      changes.push({
        changeId: row.change_id,
        studentId: row.student_id,
        recordVersion: row.record_version,
        changeType: 'UPSERT',
        createdAt: row.created_at,
        student: {
          studentId: row.student_id,
          studentNumber: row.student_number,
          studentName: row.student_name,
          programmeId: row.programme_id,
          groupId: row.group_id,
          recordVersion: row.record_version,
          updatedAt: row.updated_at
        }
      });
    }

    const lastFetchedId = rows.length
      ? rows[rows.length - 1].change_id
      : since;

    res.json({
      success: true,
      changes,
      sync: {
        since,
        nextSince: lastFetchedId,
        limit,
        hasMore: rows.length === limit
      }
    });
  } catch (error) {
    console.error('Synchronization failed:', error.message);

    res.status(500).json({
      error: 'SYNC_FAILED',
      message: 'Unable to retrieve synchronization changes.'
    });
  }
});

module.exports = router;
