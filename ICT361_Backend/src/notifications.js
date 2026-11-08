// Notifications, group requests, announcements and phone (push) registrations.
//
// Tables are created once when the server starts (DDL must not run inside a transaction:
// MySQL would commit the transaction early). Also in db/migrations/005_notifications.sql.
//
// Privacy rule: the push sent to a phone never contains names, numbers or message text. It only
// says how many notifications are unread ("Sign in to read it" on a signed-out phone). The real
// content is read inside the app, after sign-in, from GET /api/notifications.
const pool = require('./config/database');
const push = require('./push');

let ready;
function ensureSchema() {
  if (!ready) {
    ready = (async () => {
      await pool.query(`
        CREATE TABLE IF NOT EXISTS group_requests (
          request_id  CHAR(36)    NOT NULL,
          student_id  CHAR(36)    NOT NULL,
          group_code  VARCHAR(10) NOT NULL,
          status      ENUM('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
          note        VARCHAR(255) DEFAULT NULL,
          created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
          resolved_at TIMESTAMP   NULL DEFAULT NULL,
          resolved_by CHAR(36)    DEFAULT NULL,
          PRIMARY KEY (request_id),
          KEY idx_group_requests_student (student_id, status),
          KEY idx_group_requests_status (status, created_at),
          CONSTRAINT fk_group_request_student FOREIGN KEY (student_id) REFERENCES students (student_id)
        )`);
      await pool.query(`
        CREATE TABLE IF NOT EXISTS notifications (
          notification_id BIGINT       NOT NULL AUTO_INCREMENT,
          account_id      CHAR(36)     NOT NULL,
          kind            VARCHAR(30)  NOT NULL,
          title           VARCHAR(120) NOT NULL,
          body            VARCHAR(1000) NOT NULL,
          ref_id          CHAR(36)     DEFAULT NULL,
          created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
          read_at         TIMESTAMP    NULL DEFAULT NULL,
          PRIMARY KEY (notification_id),
          KEY idx_notifications_account (account_id, read_at, created_at),
          CONSTRAINT fk_notification_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
        )`);
      await pool.query(`
        CREATE TABLE IF NOT EXISTS announcements (
          announcement_id CHAR(36)     NOT NULL,
          author_id       CHAR(36)     NOT NULL,
          audience        VARCHAR(10)  NOT NULL,
          title           VARCHAR(120) NOT NULL,
          body            VARCHAR(1000) NOT NULL,
          recipients      INT          NOT NULL DEFAULT 0,
          created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (announcement_id)
        )`);
      await pool.query(`
        CREATE TABLE IF NOT EXISTS device_tokens (
          token       VARCHAR(255) NOT NULL,
          account_id  CHAR(36)     NOT NULL,
          signed_in   TINYINT(1)   NOT NULL DEFAULT 1,
          updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (token),
          KEY idx_device_account (account_id)
        )`);
    })().catch((e) => { ready = null; throw e; });
  }
  return ready;
}

/** Express middleware: make sure the tables exist before a route opens a transaction. */
function schemaReady(req, res, next) {
  ensureSchema().then(() => next(), (e) => {
    console.error('Notification tables could not be created:', e.message);
    res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  });
}

async function lecturerAccounts(db) {
  const [rows] = await db.query("SELECT account_id FROM accounts WHERE role = 'LECTURER' AND is_active = 1");
  return rows.map((r) => r.account_id);
}

async function studentAccount(db, studentId) {
  const [rows] = await db.query(
    "SELECT account_id FROM accounts WHERE student_id = ? AND role = 'STUDENT' AND is_active = 1", [studentId]);
  return rows.length ? rows[0].account_id : null;
}

/** Student accounts for an announcement: 'ALL' or a group code such as 'G08'. */
async function audienceAccounts(db, audience) {
  const [rows] = audience === 'ALL'
    ? await db.query(
      `SELECT a.account_id FROM accounts a JOIN students s ON s.student_id = a.student_id
       WHERE a.role = 'STUDENT' AND a.is_active = 1 AND s.is_deleted = FALSE`)
    : await db.query(
      `SELECT a.account_id FROM accounts a
       JOIN students s ON s.student_id = a.student_id
       JOIN lab_groups g ON g.group_id = s.group_id
       WHERE a.role = 'STUDENT' AND a.is_active = 1 AND s.is_deleted = FALSE AND g.group_code = ?`, [audience]);
  return rows.map((r) => r.account_id);
}

/**
 * Stores one notification per recipient (inside the caller's transaction, so a rolled-back
 * change leaves no notification) and schedules the push for after the commit.
 */
async function notify(db, accountIds, { kind, title, body, refId = null }) {
  const ids = [...new Set(accountIds.filter(Boolean))];
  if (!ids.length) return 0;
  for (let i = 0; i < ids.length; i += 500) {
    const batch = ids.slice(i, i + 500).map((id) => [id, kind, title.slice(0, 120), body.slice(0, 1000), refId]);
    await db.query('INSERT INTO notifications (account_id, kind, title, body, ref_id) VALUES ?', [batch]);
  }
  // The transaction commits right after the route returns; push a moment later so the
  // phone that wakes up and asks for its notifications finds them.
  setTimeout(() => push.sendToAccounts(ids).catch((e) => console.error('Push failed:', e.message)), 1500);
  return ids.length;
}

module.exports = { ensureSchema, schemaReady, lecturerAccounts, studentAccount, audienceAccounts, notify };
