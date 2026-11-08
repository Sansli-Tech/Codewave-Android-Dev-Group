// Notifications feed, phone registration, group-request decisions and announcements.
//
//   /api/notifications            any signed-in user: own feed, unread count, mark as read
//   /api/devices                  register this phone for push; mark it signed out
//   /api/group-requests           LECTURER: pending requests, approve / reject
//   /api/announcements            LECTURER: send to all students or one group; list sent ones
const express = require('express');
const crypto = require('crypto');
const pool = require('../config/database');
const { authenticateToken, requireRole } = require('../middleware/auth');
const { mutation } = require('../idempotency');
const inbox = require('../notifications');

// ------------------------------------------------------------------ notifications (any role)

const notificationsRouter = express.Router();
notificationsRouter.use(inbox.schemaReady, authenticateToken);

async function unreadCount(accountId) {
  const [[row]] = await pool.query(
    'SELECT COUNT(*) AS n FROM notifications WHERE account_id = ? AND read_at IS NULL', [accountId]);
  return Number(row.n);
}

// GET /api/notifications?limit=50   newest first, own notifications only
notificationsRouter.get('/', async (req, res) => {
  const limit = Math.min(Math.max(parseInt(req.query.limit, 10) || 50, 1), 100);
  try {
    const [rows] = await pool.query(
      `SELECT notification_id, kind, title, body, ref_id, created_at, read_at
       FROM notifications WHERE account_id = ? ORDER BY created_at DESC, notification_id DESC LIMIT ?`,
      [req.user.accountId, limit]);
    return res.json({ data: rows, unread: await unreadCount(req.user.accountId) });
  } catch (error) {
    console.error('Notifications failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// GET /api/notifications/unread-count   (for the bell badge)
notificationsRouter.get('/unread-count', async (req, res) => {
  try {
    return res.json({ unread: await unreadCount(req.user.accountId) });
  } catch (error) {
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// POST /api/notifications/read   { ids: [..] }  or  { all: true }
notificationsRouter.post('/read', async (req, res) => {
  const b = req.body || {};
  try {
    if (b.all === true) {
      await pool.query('UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE account_id = ? AND read_at IS NULL',
        [req.user.accountId]);
    } else if (Array.isArray(b.ids) && b.ids.length && b.ids.every(Number.isInteger)) {
      await pool.query(
        'UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE account_id = ? AND notification_id IN (?) AND read_at IS NULL',
        [req.user.accountId, b.ids.slice(0, 200)]);
    } else {
      return res.status(400).json({ error: 'VALIDATION_ERROR', message: 'Send ids: [...] or all: true.' });
    }
    return res.json({ unread: await unreadCount(req.user.accountId) });
  } catch (error) {
    console.error('Mark read failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// ------------------------------------------------------------------ phones (push registration)

const devicesRouter = express.Router();
devicesRouter.use(inbox.schemaReady);
const TOKEN_RE = /^[A-Za-z0-9_:.\-]{20,255}$/;

// POST /api/devices   { token }   signed in: this phone receives alerts for this account
devicesRouter.post('/', authenticateToken, async (req, res) => {
  const token = req.body && req.body.token;
  if (typeof token !== 'string' || !TOKEN_RE.test(token)) {
    return res.status(400).json({ error: 'VALIDATION_ERROR', message: 'Invalid device token.' });
  }
  try {
    // A token belongs to one phone; whoever signed in last on that phone owns it.
    await pool.query(
      `INSERT INTO device_tokens (token, account_id, signed_in) VALUES (?, ?, 1)
       ON DUPLICATE KEY UPDATE account_id = VALUES(account_id), signed_in = 1`,
      [token, req.user.accountId]);
    return res.json({ message: 'This phone will receive notifications.' });
  } catch (error) {
    console.error('Device registration failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// POST /api/devices/signed-out   { token }   no sign-in needed (the session is already gone).
// The phone keeps getting alerts, but only the generic "Sign in to read it" text.
devicesRouter.post('/signed-out', async (req, res) => {
  const token = req.body && req.body.token;
  if (typeof token !== 'string' || !TOKEN_RE.test(token)) return res.status(400).json({ error: 'VALIDATION_ERROR' });
  try {
    await pool.query('UPDATE device_tokens SET signed_in = 0 WHERE token = ?', [token]);
    return res.json({ message: 'OK' });
  } catch (error) {
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// ------------------------------------------------------------------ group requests (lecturer)

const groupRequestsRouter = express.Router();
groupRequestsRouter.use(inbox.schemaReady, authenticateToken, requireRole('LECTURER'));

// GET /api/group-requests?status=PENDING   (PENDING by default)
groupRequestsRouter.get('/', async (req, res) => {
  const status = String(req.query.status || 'PENDING').toUpperCase();
  if (!['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].includes(status)) {
    return res.status(400).json({ error: 'INVALID_QUERY' });
  }
  try {
    const [rows] = await pool.query(
      `SELECT r.request_id, r.group_code AS requested_group, r.status, r.note, r.created_at, r.resolved_at,
              s.student_id, s.student_number, s.student_name, cg.group_code AS current_group,
              rg.capacity,
              (SELECT COUNT(*) FROM students x WHERE x.group_id = rg.group_id AND x.is_deleted = FALSE) AS active_students
       FROM group_requests r
       JOIN students s ON s.student_id = r.student_id
       LEFT JOIN lab_groups cg ON cg.group_id = s.group_id
       LEFT JOIN lab_groups rg ON rg.group_code = r.group_code
       WHERE r.status = ?
       ORDER BY r.created_at ${status === 'PENDING' ? 'ASC' : 'DESC'} LIMIT 200`, [status]);
    return res.json({ data: rows });
  } catch (error) {
    console.error('Group requests failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// POST /api/group-requests/:id/resolve   { decision: APPROVE | REJECT, note? }
// Approve = the same locked capacity check as every other group change (Challenge 1).
// If the group is full the request stays PENDING, so the lecturer can reject it or wait.
groupRequestsRouter.post('/:id/resolve', mutation('Group request decision', async (req, connection) => {
  const b = req.body || {};
  const decision = String(b.decision || '').toUpperCase();
  const note = typeof b.note === 'string' ? b.note.trim().slice(0, 255) : null;
  if (!['APPROVE', 'REJECT'].includes(decision)) {
    return { status: 400, body: { error: 'VALIDATION_ERROR', message: 'decision must be APPROVE or REJECT.' } };
  }
  const [reqs] = await connection.execute(
    'SELECT request_id, student_id, group_code, status FROM group_requests WHERE request_id = ? FOR UPDATE',
    [req.params.id]);
  if (!reqs.length) return { status: 404, body: { error: 'REQUEST_NOT_FOUND' } };
  const request = reqs[0];
  if (request.status !== 'PENDING') {
    return { status: 409, body: { error: 'ALREADY_RESOLVED', status: request.status } };
  }
  const account = await inbox.studentAccount(connection, request.student_id);

  if (decision === 'REJECT') {
    await connection.execute(
      `UPDATE group_requests SET status = 'REJECTED', note = ?, resolved_at = CURRENT_TIMESTAMP, resolved_by = ?
       WHERE request_id = ?`, [note, req.user.accountId, request.request_id]);
    await inbox.notify(connection, [account], {
      kind: 'GROUP_REQUEST_REJECTED',
      title: `Group request for ${request.group_code} not approved`,
      body: note ? `Your lecturer said: ${note}` : 'You keep your current group. You can ask for another group.',
      refId: request.request_id
    });
    return { status: 200, body: { request_id: request.request_id, status: 'REJECTED' } };
  }

  // APPROVE. Lock order: group, then student (same as every other route).
  const [groups] = await connection.execute(
    'SELECT group_id, group_code, capacity FROM lab_groups WHERE group_code = ? FOR UPDATE', [request.group_code]);
  if (!groups.length) {
    return { status: 409, body: { error: 'GROUP_NOT_FOUND', message: `${request.group_code} no longer exists. Reject this request.` } };
  }
  const group = groups[0];
  const [students] = await connection.execute(
    'SELECT student_id, group_id, is_deleted FROM students WHERE student_id = ? FOR UPDATE', [request.student_id]);
  if (!students.length || students[0].is_deleted) {
    await connection.execute(
      `UPDATE group_requests SET status = 'CANCELLED', resolved_at = CURRENT_TIMESTAMP, resolved_by = ?
       WHERE request_id = ?`, [req.user.accountId, request.request_id]);
    return { status: 410, body: { error: 'STUDENT_DELETED', message: 'This student was removed. The request was cancelled.' } };
  }
  if (students[0].group_id !== group.group_id) {
    const [[count]] = await connection.execute(
      'SELECT COUNT(*) AS total FROM students WHERE group_id = ? AND is_deleted = FALSE LOCK IN SHARE MODE',
      [group.group_id]);
    if (Number(count.total) >= Number(group.capacity)) {
      return {
        status: 409,
        body: { error: 'GROUP_FULL', message: `${group.group_code} is full (${count.total}/${group.capacity}). Reject the request or free a place first.` }
      };
    }
    await connection.execute(
      'UPDATE students SET group_id = ?, record_version = record_version + 1 WHERE student_id = ?',
      [group.group_id, request.student_id]);
    await connection.execute(
      `INSERT INTO sync_changes (student_id, record_version, change_type)
       SELECT student_id, record_version, 'UPSERT' FROM students WHERE student_id = ?`, [request.student_id]);
  }
  await connection.execute(
    `UPDATE group_requests SET status = 'APPROVED', note = ?, resolved_at = CURRENT_TIMESTAMP, resolved_by = ?
     WHERE request_id = ?`, [note, req.user.accountId, request.request_id]);
  await inbox.notify(connection, [account], {
    kind: 'GROUP_REQUEST_APPROVED',
    title: `You're now in ${group.group_code}`,
    body: note ? `Your group request was approved. Your lecturer said: ${note}` : 'Your group request was approved.',
    refId: request.request_id
  });
  return { status: 200, body: { request_id: request.request_id, status: 'APPROVED', group_code: group.group_code } };
}));

// ------------------------------------------------------------------ announcements (lecturer)

const announcementsRouter = express.Router();
announcementsRouter.use(inbox.schemaReady, authenticateToken, requireRole('LECTURER'));

// POST /api/announcements   { title, body, audience: "ALL" | "G08" }
announcementsRouter.post('/', mutation('Announcement', async (req, connection) => {
  const b = req.body || {};
  const title = typeof b.title === 'string' ? b.title.trim() : '';
  const body = typeof b.body === 'string' ? b.body.trim() : '';
  const audience = String(b.audience || 'ALL').trim().toUpperCase();
  if (title.length < 3 || title.length > 120 || body.length < 1 || body.length > 1000) {
    return { status: 400, body: { error: 'VALIDATION_ERROR', message: 'Title 3-120 characters, message 1-1000 characters.' } };
  }
  if (audience !== 'ALL') {
    const [g] = await connection.execute('SELECT 1 FROM lab_groups WHERE group_code = ?', [audience]);
    if (!g.length) return { status: 400, body: { error: 'INVALID_GROUP', message: 'Choose All students or an existing group.' } };
  }
  const id = crypto.randomUUID();
  const recipients = await inbox.audienceAccounts(connection, audience);
  await connection.execute(
    'INSERT INTO announcements (announcement_id, author_id, audience, title, body, recipients) VALUES (?, ?, ?, ?, ?, ?)',
    [id, req.user.accountId, audience, title, body, recipients.length]);
  await inbox.notify(connection, recipients, { kind: 'ANNOUNCEMENT', title, body, refId: id });
  return {
    status: 201,
    body: { announcement_id: id, audience, recipients: recipients.length,
      message: `Sent to ${recipients.length} student${recipients.length === 1 ? '' : 's'}.` }
  };
}));

// GET /api/announcements   the 30 most recent, for the lecturer's "Sent" list
announcementsRouter.get('/', async (req, res) => {
  try {
    const [rows] = await pool.query(
      `SELECT announcement_id, audience, title, body, recipients, created_at
       FROM announcements ORDER BY created_at DESC LIMIT 30`);
    return res.json({ data: rows });
  } catch (error) {
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

module.exports = { notificationsRouter, devicesRouter, groupRequestsRouter, announcementsRouter };
