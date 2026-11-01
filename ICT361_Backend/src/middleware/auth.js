
const jwt = require('jsonwebtoken');
const pool = require('../config/database');

async function authenticateToken(req, res, next) {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith('Bearer ')
    ? authHeader.slice(7)
    : null;

  if (!token) {
    return res.status(401).json({
      error: 'AUTHENTICATION_REQUIRED'
    });
  }

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET, {
      issuer: 'ict361-api'
    });

    const [rows] = await pool.execute(
      `SELECT account_id, role, student_id, is_active
       FROM accounts
       WHERE account_id = ?
       LIMIT 1`,
      [decoded.accountId]
    );

    if (!rows.length || !rows[0].is_active) {
      return res.status(401).json({
        error: 'ACCOUNT_INACTIVE'
      });
    }

    const account = rows[0];

    // Reject tokens whose role or student ownership no longer matches.
    if (
      account.role !== decoded.role ||
      (account.student_id || null) !== (decoded.studentId || null)
    ) {
      return res.status(401).json({
        error: 'SESSION_INVALID'
      });
    }

    req.user = {
      accountId: account.account_id,
      role: account.role,
      studentId: account.student_id
    };

    next();
  } catch (error) {
    if (error.name === 'JsonWebTokenError' ||
        error.name === 'TokenExpiredError' ||
        error.name === 'NotBeforeError') {
      return res.status(401).json({
        error: 'INVALID_OR_EXPIRED_TOKEN'
      });
    }

    console.error('Authentication error:', error.message);

    return res.status(500).json({
      error: 'INTERNAL_SERVER_ERROR'
    });
  }
}

function requireRole(...allowedRoles) {
  return (req, res, next) => {
    if (!req.user || !allowedRoles.includes(req.user.role)) {
      return res.status(403).json({
        error: 'FORBIDDEN',
        message: 'You do not have permission to perform this action.'
      });
    }

    next();
  };
}

module.exports = { authenticateToken, requireRole };
