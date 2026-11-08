// "Forgot password?" for students and lecturers.
//
//   1. E-mailed code (main way, no lecturer needed):
//        POST /api/auth/forgot-password { login }     login = student number or staff e-mail / username
//      The server e-mails a 6-digit code to the account's recovery e-mail (lecturers: their staff
//      e-mail). The reply is ALWAYS the same, so nobody can find out which accounts exist.
//        POST /api/auth/reset-with-code { login, code, new_password }
//   2. Code from the lecturer (backup for students without an e-mail address):
//        POST /api/students/:id/reset-code (LECTURER) -> 16-character code, used with reset-with-code
//   3. Lecturer without e-mail access: staff e-mail + staff ID + faculty code.
//        POST /api/auth/lecturer-reset-password
//
// Every code: stored only as a bcrypt hash, works once, expires after 15 minutes, and stops
// working after 5 wrong tries. Public routes: 10 requests per IP per 15 minutes, and at most one
// e-mail per account per minute.
const express = require('express');
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const pool = require('../config/database');
const { authenticateToken, requireRole } = require('../middleware/auth');
const mailer = require('../mailer');

const CODE_MINUTES = 15;
const MAX_WRONG = 5;
const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]+\.[^\s@]{2,}$/;

// ------------------------------------------------------------------ schema (created on first use)

let schemaReady;
async function columnExists(table, column) {
  const [rows] = await pool.query(
    `SELECT 1 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?`, [table, column]);
  return rows.length > 0;
}
/** Adds what this feature needs, once per server start; also in db/migrations/003 and 004. */
function ensureSchema() {
  if (!schemaReady) {
    schemaReady = (async () => {
      await pool.query(`
        CREATE TABLE IF NOT EXISTS password_reset_codes (
          account_id  CHAR(36)     NOT NULL,
          code_hash   VARCHAR(255) NOT NULL,
          expires_at  TIMESTAMP    NOT NULL,
          used_at     TIMESTAMP    NULL DEFAULT NULL,
          created_by  CHAR(36)     DEFAULT NULL,
          created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
          wrong_attempts INT       NOT NULL DEFAULT 0,
          PRIMARY KEY (account_id),
          CONSTRAINT fk_reset_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
        )`);
      if (!(await columnExists('password_reset_codes', 'wrong_attempts'))) {
        await pool.query('ALTER TABLE password_reset_codes ADD COLUMN wrong_attempts INT NOT NULL DEFAULT 0');
      }
      if (!(await columnExists('accounts', 'email'))) {
        await pool.query('ALTER TABLE accounts ADD COLUMN email VARCHAR(254) NULL DEFAULT NULL');
      }
    })().catch((e) => { schemaReady = null; throw e; });
  }
  return schemaReady;
}

// ------------------------------------------------------------------ helpers

const attempts = new Map();
function tooManyAttempts(ip) {
  const now = Date.now();
  const recent = (attempts.get(ip) || []).filter((t) => now - t < 15 * 60 * 1000);
  recent.push(now);
  attempts.set(ip, recent);
  return recent.length > 10;
}

const lastEmail = new Map();   // account_id -> time of last e-mailed code

function codesMatch(given, expected) {
  const a = crypto.createHash('sha256').update(String(given)).digest();
  const b = crypto.createHash('sha256').update(String(expected)).digest();
  return crypto.timingSafeEqual(a, b);
}

function validPassword(p) {
  return typeof p === 'string' && p.length >= 12 && p.length <= 128;
}

function validLogin(l) {
  return typeof l === 'string' && l.trim().length >= 3 && l.trim().length <= 254;
}

/** Student number -> that student's account; anything else -> account by username (any case). */
async function findAccount(conn, login, lock) {
  const value = login.trim();
  const forUpdate = lock ? ' FOR UPDATE' : '';
  const [rows] = /^\d{9}$/.test(value)
    ? await conn.query(
      `SELECT a.account_id, a.username, a.role, a.email FROM students s
       JOIN accounts a ON a.student_id = s.student_id AND a.is_active = 1
       WHERE s.student_number = ? AND s.is_deleted = FALSE${forUpdate}`, [value])
    : await conn.query(
      `SELECT account_id, username, role, email FROM accounts
       WHERE LOWER(username) = LOWER(?) AND is_active = 1${forUpdate}`, [value]);
  return rows[0] || null;
}

/** Where the code is sent: the recovery e-mail, or a lecturer's staff e-mail (their username). */
function recoveryAddress(account) {
  if (account.email) return account.email;
  if (account.role === 'LECTURER' && EMAIL_RE.test(account.username)) return account.username;
  return null;
}

async function storeCode(conn, accountId, code, createdBy) {
  await conn.query(
    `INSERT INTO password_reset_codes (account_id, code_hash, expires_at, used_at, created_by, wrong_attempts)
     VALUES (?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ${CODE_MINUTES} MINUTE), NULL, ?, 0)
     ON DUPLICATE KEY UPDATE code_hash = VALUES(code_hash), expires_at = VALUES(expires_at),
       used_at = NULL, created_by = VALUES(created_by), created_at = CURRENT_TIMESTAMP, wrong_attempts = 0`,
    [accountId, await bcrypt.hash(code, 12), createdBy]);
}

const GENERIC_SENT = 'If an account with an e-mail address matches, a code has been sent. '
  + `It expires in ${CODE_MINUTES} minutes. Check your spam folder too.`;
const WRONG_CODE = {
  error: 'INVALID_RESET_CODE',
  message: 'The details or the code are wrong, or the code has expired or was used. Request a new code.'
};

/** Shared by every code route. Returns { status, body }. */
async function resetWithCode(login, code, password) {
  let conn;
  try {
    await ensureSchema();
    conn = await pool.getConnection();
    await conn.beginTransaction();
    const account = await findAccount(conn, login, true);
    if (!account) { await conn.rollback(); return { status: 400, body: WRONG_CODE }; }
    const [rows] = await conn.query(
      `SELECT code_hash, wrong_attempts,
              (expires_at > CURRENT_TIMESTAMP AND used_at IS NULL) AS usable
       FROM password_reset_codes WHERE account_id = ? FOR UPDATE`, [account.account_id]);
    const row = rows[0];
    if (!row || !row.usable || row.wrong_attempts >= MAX_WRONG) {
      await conn.rollback();
      return { status: 400, body: WRONG_CODE };
    }
    if (!(await bcrypt.compare(code.trim().toUpperCase(), row.code_hash))) {
      await conn.query('UPDATE password_reset_codes SET wrong_attempts = wrong_attempts + 1 WHERE account_id = ?',
        [account.account_id]);
      await conn.commit();
      return { status: 400, body: WRONG_CODE };
    }
    await conn.query('UPDATE accounts SET password_hash = ? WHERE account_id = ?',
      [await bcrypt.hash(password, 12), account.account_id]);
    await conn.query('UPDATE password_reset_codes SET used_at = CURRENT_TIMESTAMP WHERE account_id = ?',
      [account.account_id]);
    await conn.commit();
    return {
      status: 200,
      body: { message: 'Password changed. Sign in with your new password.', username: account.username }
    };
  } catch (error) {
    if (conn) await conn.rollback().catch(() => {});
    throw error;
  } finally {
    if (conn) conn.release();
  }
}

// ------------------------------------------------------------------ lecturer issues a code (backup)

const lecturerRouter = express.Router();
lecturerRouter.use(authenticateToken);

// POST /api/students/:id/reset-code   (LECTURER)
lecturerRouter.post('/:id/reset-code', requireRole('LECTURER'), async (req, res) => {
  try {
    await ensureSchema();
    const [rows] = await pool.query(
      `SELECT a.account_id, a.username, a.is_active
       FROM students s JOIN accounts a ON a.student_id = s.student_id
       WHERE s.student_id = ? AND s.is_deleted = FALSE`, [req.params.id]);
    if (!rows.length) {
      return res.status(404).json({
        error: 'NO_ACCOUNT',
        message: 'This student has not created an account yet. Give them a claim code instead.'
      });
    }
    if (!rows[0].is_active) {
      return res.status(409).json({ error: 'ACCOUNT_INACTIVE', message: 'This account is disabled.' });
    }
    const code = crypto.randomBytes(8).toString('hex').toUpperCase();
    await storeCode(pool, rows[0].account_id, code, req.user.accountId);
    return res.status(201).json({
      message: 'Reset code created. Give it to the student in person or privately.',
      reset_code: code,
      username: rows[0].username,
      expires_in_minutes: CODE_MINUTES
    });
  } catch (error) {
    console.error('Reset code failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// ------------------------------------------------------------------ recovery e-mail (signed in)

const accountRouter = express.Router();
accountRouter.use(authenticateToken);

// GET /api/account/email
accountRouter.get('/email', async (req, res) => {
  try {
    await ensureSchema();
    const [rows] = await pool.query('SELECT username, role, email FROM accounts WHERE account_id = ?',
      [req.user.accountId]);
    if (!rows.length) return res.status(404).json({ error: 'ACCOUNT_NOT_FOUND' });
    return res.json({ email: rows[0].email, codes_sent_to: recoveryAddress(rows[0]) });
  } catch (error) {
    console.error('Read recovery e-mail failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// PUT /api/account/email   { email }   (empty or null removes it)
accountRouter.put('/email', async (req, res) => {
  const raw = req.body ? req.body.email : undefined;
  const email = raw == null ? '' : String(raw).trim().toLowerCase();
  if (email && (email.length > 254 || !EMAIL_RE.test(email))) {
    return res.status(400).json({ error: 'VALIDATION_ERROR', message: 'Enter a valid e-mail address.' });
  }
  try {
    await ensureSchema();
    await pool.query('UPDATE accounts SET email = ? WHERE account_id = ?', [email || null, req.user.accountId]);
    return res.json({ message: email ? 'Recovery e-mail saved.' : 'Recovery e-mail removed.', email: email || null });
  } catch (error) {
    console.error('Save recovery e-mail failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// ------------------------------------------------------------------ public reset routes

const publicRouter = express.Router();

// POST /api/auth/forgot-password   { login }
publicRouter.post('/forgot-password', async (req, res) => {
  const login = req.body && req.body.login;
  if (!validLogin(login)) {
    return res.status(400).json({ error: 'VALIDATION_ERROR', message: 'Enter your student number or staff e-mail.' });
  }
  if (!mailer.isConfigured()) {
    return res.status(503).json({
      error: 'EMAIL_NOT_CONFIGURED',
      message: 'This server cannot send e-mail yet. Ask your lecturer for a reset code instead.'
    });
  }
  if (tooManyAttempts(req.ip)) {
    return res.status(429).json({ error: 'TOO_MANY_ATTEMPTS', message: 'Too many attempts. Wait 15 minutes.' });
  }
  try {
    await ensureSchema();
    const account = await findAccount(pool, login, false);
    const to = account && recoveryAddress(account);
    const last = account ? lastEmail.get(account.account_id) || 0 : 0;
    if (to && Date.now() - last > 60 * 1000) {
      lastEmail.set(account.account_id, Date.now());
      const code = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
      await storeCode(pool, account.account_id, code, null);
      try {
        await mailer.sendMail({
          to,
          subject: 'Your MUStudentSync password reset code',
          text: `Your MUStudentSync password reset code is ${code}.\n\n`
            + `In the app, enter it under "Forgot password?" with a new password. It works once and expires in ${CODE_MINUTES} minutes.\n\n`
            + 'If you did not ask for this, ignore this e-mail; your password has not changed.',
          html: `<p>Your MUStudentSync password reset code is</p><p style="font-size:28px;font-weight:bold;letter-spacing:4px">${code}</p>`
            + `<p>In the app, enter it under <b>Forgot password?</b> with a new password. It works once and expires in ${CODE_MINUTES} minutes.</p>`
            + '<p>If you did not ask for this, ignore this e-mail; your password has not changed.</p>'
        });
      } catch (mailError) {
        console.error('Reset e-mail failed:', mailError.message);   // same reply: never reveal accounts
      }
    }
    return res.json({ message: GENERIC_SENT });
  } catch (error) {
    console.error('Forgot password failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// POST /api/auth/reset-with-code   { login, code, new_password }
// code: 6 digits from the e-mail, or the 16-character code from the lecturer.
publicRouter.post('/reset-with-code', async (req, res) => {
  const { login, code, new_password: password } = req.body || {};
  if (!validLogin(login) || typeof code !== 'string' ||
      !/^(\d{6}|[a-fA-F0-9]{16})$/.test(code.trim()) || !validPassword(password)) {
    return res.status(400).json({
      error: 'VALIDATION_ERROR',
      message: 'Enter your student number or staff e-mail, the code (6 digits, or 16 characters from your lecturer) and a password of 12-128 characters.'
    });
  }
  if (tooManyAttempts(req.ip)) {
    return res.status(429).json({ error: 'TOO_MANY_ATTEMPTS', message: 'Too many attempts. Wait 15 minutes.' });
  }
  try {
    const result = await resetWithCode(login, code, password);
    return res.status(result.status).json(result.body);
  } catch (error) {
    console.error('Reset with code failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// POST /api/auth/reset-password   { student_number, reset_code, new_password }   (older app versions)
publicRouter.post('/reset-password', async (req, res) => {
  const { student_number: number, reset_code: code, new_password: password } = req.body || {};
  if (typeof number !== 'string' || !/^\d{9}$/.test(number) || typeof code !== 'string' ||
      !/^(\d{6}|[a-fA-F0-9]{16})$/.test(code.trim()) || !validPassword(password)) {
    return res.status(400).json({
      error: 'VALIDATION_ERROR',
      message: 'Enter your 9-digit student number, the reset code and a password of 12-128 characters.'
    });
  }
  if (tooManyAttempts(req.ip)) {
    return res.status(429).json({ error: 'TOO_MANY_ATTEMPTS', message: 'Too many attempts. Wait 15 minutes.' });
  }
  try {
    const result = await resetWithCode(number, code, password);
    return res.status(result.status).json(result.body);
  } catch (error) {
    console.error('Student password reset failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

// POST /api/auth/lecturer-reset-password   { email, staff_id, faculty_token, new_password }
publicRouter.post('/lecturer-reset-password', async (req, res) => {
  const { email, staff_id: staffId, faculty_token: token, new_password: password } = req.body || {};
  const expected = process.env.LECTURER_REGISTRATION_CODE;
  if (!expected) {
    return res.status(503).json({
      error: 'RESET_DISABLED',
      message: 'Lecturer reset with the faculty code is not enabled on this server. Use "E-mail me a code".'
    });
  }
  if (typeof email !== 'string' || !email.trim() || typeof staffId !== 'string' || !staffId.trim() ||
      typeof token !== 'string' || !validPassword(password)) {
    return res.status(400).json({
      error: 'VALIDATION_ERROR',
      message: 'Enter your staff e-mail, staff ID, the faculty code and a password of 12-128 characters.'
    });
  }
  if (tooManyAttempts(req.ip)) {
    return res.status(429).json({ error: 'TOO_MANY_ATTEMPTS', message: 'Too many attempts. Wait 15 minutes.' });
  }
  const wrong = () => res.status(400).json({
    error: 'INVALID_RESET_DETAILS',
    message: 'Those details do not match a lecturer account, or the faculty code is wrong.'
  });
  try {
    if (!codesMatch(token.trim(), expected)) return wrong();
    const [rows] = await pool.query(
      `SELECT a.account_id FROM accounts a
       JOIN lecturer_profiles p ON p.account_id = a.account_id
       WHERE a.username = ? AND a.role = 'LECTURER' AND a.is_active = 1 AND p.staff_id = ?`,
      [email.trim().toLowerCase(), staffId.trim()]);
    if (!rows.length) return wrong();
    await pool.query('UPDATE accounts SET password_hash = ? WHERE account_id = ?',
      [await bcrypt.hash(password, 12), rows[0].account_id]);
    return res.json({ message: 'Password changed. Sign in with your staff e-mail and new password.' });
  } catch (error) {
    console.error('Lecturer password reset failed:', error.message);
    return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
  }
});

module.exports = { lecturerRouter, accountRouter, publicRouter, ensureSchema };
