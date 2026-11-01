const crypto = require('crypto');
const pool = require('./config/database');

// Activity E / Challenge 2: "the response disappears".
//
// Every mutation can carry an operation ID (header `Idempotency-Key: <uuid>`
// or body field `operation_id`). The app creates the ID once per change and
// reuses it on every retry.
//
// The receipt row is written in the SAME transaction as the change itself:
//   - change committed  => receipt committed => a retry gets the stored reply
//                          and nothing is applied a second time (even after a
//                          server restart, because the receipt is in MySQL).
//   - change rolled back => receipt rolled back => a retry runs normally.
// Two copies of the same request arriving together: the second INSERT waits
// on the primary-key lock until the first commits, then replays its result.
// Same ID with different content, or from another account => 422.

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

// JSON with sorted keys, so { a, b } and { b, a } hash the same.
function stable(value) {
  if (Array.isArray(value)) return '[' + value.map(stable).join(',') + ']';
  if (value && typeof value === 'object') {
    return '{' + Object.keys(value).sort()
      .map((k) => JSON.stringify(k) + ':' + stable(value[k])).join(',') + '}';
  }
  return JSON.stringify(value === undefined ? null : value);
}

function requestHash(req) {
  const { operation_id: _ignored, ...content } = req.body || {};
  return crypto.createHash('sha256')
    .update(`${req.method} ${req.baseUrl}${req.path} ${stable(content)}`)
    .digest('hex');
}

// Returns the operation ID, null when none was sent, or throws 400.
function getOperationId(req) {
  const raw = (req.body && req.body.operation_id) || req.get('Idempotency-Key');
  if (raw === undefined || raw === null || raw === '') return null;
  if (typeof raw !== 'string' || !UUID_RE.test(raw)) {
    const err = new Error('operation_id must be a UUID.');
    err.status = 400;
    err.body = { error: 'INVALID_OPERATION_ID', message: err.message };
    throw err;
  }
  return raw.toLowerCase();
}

/**
 * Runs handler(connection) inside one transaction and returns { status, body, replay }.
 * The handler must NOT begin, commit or roll back; it returns { status, body }.
 * A reply with status >= 400 (GROUP_FULL, VERSION_CONFLICT, ...) undoes the
 * handler's work but is still stored, so a retry gets the same answer.
 * An unexpected exception undoes everything (including the receipt) and is rethrown.
 */
async function runOperation(req, handler) {
  const opId = getOperationId(req);
  const accountId = req.user.accountId;
  const connection = await pool.getConnection();

  try {
    await connection.beginTransaction();

    if (opId === null) {
      // No ID sent (older app builds): plain transaction, no replay protection.
      const outcome = await handler(connection);
      if (outcome.status >= 400) await connection.rollback();
      else await connection.commit();
      return outcome;
    }

    const hash = requestHash(req);
    try {
      await connection.execute(
        `INSERT INTO operation_receipts
         (operation_id, account_id, request_hash, response_json)
         VALUES (?, ?, ?, ?)`,
        [opId, accountId, hash, '{}']
      );
    } catch (error) {
      if (error.code !== 'ER_DUP_ENTRY') throw error;
      await connection.rollback();

      const [rows] = await pool.execute(
        `SELECT account_id, request_hash, response_json
         FROM operation_receipts WHERE operation_id = ?`,
        [opId]
      );
      const receipt = rows[0];
      if (!receipt || receipt.account_id !== accountId || receipt.request_hash !== hash) {
        return {
          status: 422,
          body: {
            error: 'OPERATION_ID_REUSED',
            message: 'This operation ID was already used for a different request.'
          }
        };
      }
      const stored = typeof receipt.response_json === 'string'
        ? JSON.parse(receipt.response_json)
        : receipt.response_json;
      return { status: stored.status, body: stored.body, replay: true };
    }

    await connection.query('SAVEPOINT op_start');
    const outcome = await handler(connection);
    if (outcome.status >= 400) {
      await connection.query('ROLLBACK TO SAVEPOINT op_start');
    }
    await connection.execute(
      'UPDATE operation_receipts SET response_json = ? WHERE operation_id = ?',
      [JSON.stringify({ status: outcome.status, body: outcome.body }), opId]
    );
    await connection.commit();
    return outcome;
  } catch (error) {
    await connection.rollback().catch(() => {});
    throw error;
  } finally {
    connection.release();
  }
}

// Sends the outcome; replays are marked so they are easy to see in Postman/Logcat.
function send(res, outcome) {
  if (outcome.replay) res.set('Idempotent-Replay', 'true');
  return res.status(outcome.status).json(outcome.body);
}

// Wraps a route: runs the operation, sends it, and turns crashes into a 500.
function mutation(label, handler) {
  return async (req, res) => {
    try {
      return send(res, await runOperation(req, (conn) => handler(req, conn)));
    } catch (error) {
      if (error.status && error.body) return res.status(error.status).json(error.body);
      console.error(`${label} failed:`, error.message);
      return res.status(500).json({ error: 'INTERNAL_SERVER_ERROR' });
    }
  };
}

module.exports = { runOperation, send, mutation, getOperationId, requestHash };
