
const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../config/database');
const {
  authenticateToken
} = require('../middleware/auth');

const router = express.Router();

router.post('/login', async (req, res) => {
  const { username, password } = req.body;

  if (
    typeof username !== 'string' ||
    typeof password !== 'string' ||
    !username.trim() ||
    !password
  ) {
    return res.status(400).json({
      error: 'VALIDATION_ERROR',
      message: 'Username and password are required.'
    });
  }

  try {
    const [rows] = await pool.execute(
      `SELECT account_id, username, password_hash, role,
              student_id, is_active
       FROM accounts
       WHERE username = ?
       LIMIT 1`,
      [username.trim()]
    );

    if (
      rows.length === 0 ||
      !rows[0].is_active ||
      !(await bcrypt.compare(password, rows[0].password_hash))
    ) {
      return res.status(401).json({
        error: 'INVALID_CREDENTIALS',
        message: 'Username or password is incorrect.'
      });
    }

    const account = rows[0];

    const token = jwt.sign(
      {
        accountId: account.account_id,
        role: account.role,
        studentId: account.student_id
      },
      process.env.JWT_SECRET,
      { expiresIn: '2h', issuer: 'ict361-api' }
    );

    res.json({
      message: 'Login successful.',
      token,
      expiresIn: 7200,
      user: {
        accountId: account.account_id,
        username: account.username,
        role: account.role,
        studentId: account.student_id
      }
    });
  } catch (error) {
    console.error('Login error:', error.message);

    res.status(500).json({
      error: 'INTERNAL_SERVER_ERROR',
      message: 'Unable to sign in at this time.'
    });
  }
});

router.get('/me', authenticateToken, async (req, res) => {
  try {
    const [rows] = await pool.execute(
      `SELECT account_id, username, role, student_id, is_active
       FROM accounts
       WHERE account_id = ?`,
      [req.user.accountId]
    );

    if (!rows.length || !rows[0].is_active) {
      return res.status(401).json({
        error: 'ACCOUNT_INACTIVE',
        message: 'This account is unavailable.'
      });
    }

    res.json({ user: rows[0] });
  } catch (error) {
    console.error('Account lookup error:', error.message);

    res.status(500).json({
      error: 'INTERNAL_SERVER_ERROR',
      message: 'Unable to retrieve account details.'
    });
  }
});


router.post('/claim', async (req, res) => {
  const {
    student_number,
    claim_code,
    username,
    password
  } = req.body;

  if (
    typeof student_number !== 'string' ||
    !/^\d{9}$/.test(student_number) ||
    typeof claim_code !== 'string' ||
    !/^[a-fA-F0-9]{16}$/.test(claim_code) ||
    typeof username !== 'string' ||
    !/^[a-zA-Z0-9_.-]{3,100}$/.test(username) ||
    typeof password !== 'string' ||
    password.length < 12 ||
    password.length > 128
  ) {
    return res.status(400).json({
      error: 'VALIDATION_ERROR',
      message: 'Check the student number, claim code, username and password requirements.'
    });
  }

  let connection;

  try {
    connection = await pool.getConnection();
    await connection.beginTransaction();

    const [students] = await connection.execute(
      `SELECT student_id, student_number
       FROM students
       WHERE student_number = ?
         AND is_deleted = 0
       FOR UPDATE`,
      [student_number]
    );

    if (!students.length) {
      await connection.rollback();
      return res.status(404).json({
        error: 'STUDENT_NOT_FOUND',
        message: 'No active student matches that student number.'
      });
    }

    const student = students[0];

const [claims] = await connection.execute(
  `SELECT claim_id, claim_code_hash, expires_at, claimed_at
   FROM student_claim_codes
   WHERE student_id = ?
   LIMIT 1
   FOR UPDATE`,
  [student.student_id]
);

    if (!claims.length || claims[0].claimed_at !== null) {
      await connection.rollback();
      return res.status(400).json({
        error: 'CLAIM_UNAVAILABLE',
        message: 'No unused claim code is available. Ask the lecturer to generate a new one.'
      });
    }

    const claim = claims[0];

    if (
      !claim.expires_at ||
      new Date(claim.expires_at).getTime() <= Date.now()
    ) {
      await connection.rollback();
      return res.status(400).json({
        error: 'CLAIM_EXPIRED',
        message: 'The claim code has expired. Ask the lecturer to generate a new one.'
      });
    }

  const codeMatches = await bcrypt.compare(
  claim_code,
  claim.claim_code_hash
);

    if (!codeMatches) {
      await connection.rollback();
      return res.status(400).json({
        error: 'INVALID_CLAIM_CODE',
        message: 'The claim code is incorrect.'
      });
    }

    const [existing] = await connection.execute(
      `SELECT account_id
       FROM accounts
       WHERE student_id = ? OR username = ?
       LIMIT 1`,
      [student.student_id, username.trim()]
    );

    if (existing.length) {
      await connection.rollback();
      return res.status(409).json({
        error: 'ACCOUNT_EXISTS',
        message: 'An account already exists for this student or username.'
      });
    }

    const accountId = require('crypto').randomUUID();
    const passwordHash = await bcrypt.hash(password, 12);

    await connection.execute(
      `INSERT INTO accounts
       (account_id, username, password_hash, role, student_id, is_active)
       VALUES (?, ?, ?, 'STUDENT', ?, 1)`,
      [
        accountId,
        username.trim(),
        passwordHash,
        student.student_id
      ]
    );

    await connection.execute(
      `UPDATE student_claim_codes
       SET claimed_at = CURRENT_TIMESTAMP
       WHERE claim_id = ?`,
      [claim.claim_id]
    );

    await connection.commit();

    return res.status(201).json({
      message: 'Student account created successfully.',
      user: {
        accountId,
        username: username.trim(),
        role: 'STUDENT',
        studentId: student.student_id
      }
    });
  } catch (error) {
    if (connection) {
      await connection.rollback().catch(() => {});
    }

    console.error('Student claim error:', error.message);

    if (error.code === 'ER_DUP_ENTRY') {
      return res.status(409).json({
        error: 'ACCOUNT_EXISTS',
        message: 'That username or student account already exists.'
      });
    }

    return res.status(500).json({
      error: 'INTERNAL_SERVER_ERROR',
      message: 'Unable to claim the student account at this time.'
    });
  } finally {
    if (connection) connection.release();
  }
});

// ---------------------------------------------------------------------------
// POST /api/auth/register-lecturer
// Creates a LECTURER account, but only with the faculty authorization code that
// the administrator sets in .env as LECTURER_REGISTRATION_CODE. Without that
// variable, lecturer registration is switched off (503).
// Body: { full_name, staff_id, email, school, department, password, faculty_token }
// The staff email becomes the username for signing in.
// ---------------------------------------------------------------------------

// Simple brute-force guard for the code: at most 10 attempts per IP per 15 minutes.
const lecturerAttempts = new Map();
function tooManyLecturerAttempts(ip) {
  const now = Date.now();
  const windowMs = 15 * 60 * 1000;
  const recent = (lecturerAttempts.get(ip) || []).filter(t => now - t < windowMs);
  recent.push(now);
  lecturerAttempts.set(ip, recent);
  return recent.length > 10;
}

function codesMatch(given, expected) {
  // Compare hashes so the comparison takes the same time whatever the input.
  const crypto = require('crypto');
  const a = crypto.createHash('sha256').update(String(given)).digest();
  const b = crypto.createHash('sha256').update(String(expected)).digest();
  return crypto.timingSafeEqual(a, b);
}

router.post('/register-lecturer', async (req, res) => {
  const expectedCode = process.env.LECTURER_REGISTRATION_CODE;
  if (!expectedCode) {
    return res.status(503).json({
      error: 'LECTURER_REGISTRATION_DISABLED',
      message: 'Lecturer registration is not enabled on this server.'
    });
  }

  if (tooManyLecturerAttempts(req.ip)) {
    return res.status(429).json({
      error: 'TOO_MANY_ATTEMPTS',
      message: 'Too many attempts. Try again in 15 minutes.'
    });
  }

  const {
    full_name,
    staff_id,
    email,
    school,
    department,
    password,
    faculty_token
  } = req.body;

  const text = (v, min, max) =>
    typeof v === 'string' && v.trim().length >= min && v.trim().length <= max;

  if (
    !text(full_name, 2, 100) ||
    !text(staff_id, 1, 30) ||
    typeof email !== 'string' ||
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) ||
    email.trim().length > 100 ||
    !text(school, 2, 150) ||
    !text(department, 2, 150) ||
    typeof password !== 'string' ||
    password.length < 12 ||
    password.length > 128 ||
    typeof faculty_token !== 'string'
  ) {
    return res.status(400).json({
      error: 'VALIDATION_ERROR',
      message: 'Check your details. The password must be at least 12 characters.'
    });
  }

  if (!codesMatch(faculty_token.trim(), expectedCode)) {
    return res.status(403).json({
      error: 'INVALID_FACULTY_TOKEN',
      message: 'The faculty authorization code is incorrect.'
    });
  }

  const username = email.trim().toLowerCase();
  let connection;

  try {
    connection = await pool.getConnection();
    await connection.beginTransaction();

    const [existing] = await connection.execute(
      'SELECT account_id FROM accounts WHERE username = ? LIMIT 1',
      [username]
    );
    if (existing.length) {
      await connection.rollback();
      return res.status(409).json({
        error: 'ACCOUNT_EXISTS',
        message: 'An account with this email already exists.'
      });
    }

    const accountId = require('crypto').randomUUID();
    const passwordHash = await bcrypt.hash(password, 12);

    await connection.execute(
      `INSERT INTO accounts
       (account_id, username, password_hash, role, student_id, is_active)
       VALUES (?, ?, ?, 'LECTURER', NULL, 1)`,
      [accountId, username, passwordHash]
    );

    await connection.execute(
      `INSERT INTO lecturer_profiles
       (account_id, full_name, staff_id, school, department)
       VALUES (?, ?, ?, ?, ?)`,
      [accountId, full_name.trim(), staff_id.trim(), school.trim(), department.trim()]
    );

    await connection.commit();

    return res.status(201).json({
      message: 'Lecturer account created. Sign in with your staff email.',
      user: { accountId, username, role: 'LECTURER' }
    });
  } catch (error) {
    if (connection) {
      await connection.rollback().catch(() => {});
    }

    if (error.code === 'ER_DUP_ENTRY') {
      return res.status(409).json({
        error: 'ACCOUNT_EXISTS',
        message: 'An account with this email or staff ID already exists.'
      });
    }

    console.error('Lecturer registration error:', error.message);
    return res.status(500).json({
      error: 'INTERNAL_SERVER_ERROR',
      message: 'Unable to register the lecturer at this time.'
    });
  } finally {
    if (connection) connection.release();
  }
});

module.exports = router;
