// Challenge 2 - "The response disappears". Run against your own running server.
//
//   Part 1:  node scripts/challenge2_lost_response.js
//            -> creates a fictitious student with a fixed operation ID, then sends the
//               exact same request again as if the first response had been lost.
//   Now STOP the server (Ctrl+C) and START it again (npm start).
//   Part 2:  node scripts/challenge2_lost_response.js --after-restart
//            -> sends the same request a third time, reuses the ID with different
//               content, and counts the rows in MySQL.
//
// Uses SEED_LECTURER_USERNAME / SEED_LECTURER_PASSWORD from .env to sign in.
// Set API_URL if your server is not on http://localhost:<PORT>.
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const pool = require('../src/config/database');

const API = process.env.API_URL || `http://localhost:${process.env.PORT || 5000}`;
const STATE = path.join(__dirname, '.challenge2-state.json');

async function call(method, route, { token, key, body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (key) headers['Idempotency-Key'] = key;
  const res = await fetch(API + route, { method, headers, body: body && JSON.stringify(body) });
  const json = await res.json().catch(() => null);
  return { status: res.status, replay: res.headers.get('idempotent-replay') === 'true', json };
}

async function signIn() {
  const r = await call('POST', '/api/auth/login', {
    body: { username: process.env.SEED_LECTURER_USERNAME, password: process.env.SEED_LECTURER_PASSWORD }
  });
  if (r.status !== 200) throw new Error(`Lecturer sign-in failed (${r.status}). Check SEED_LECTURER_* in .env.`);
  return r.json.token;
}

function show(step, r) {
  console.log(`${step}: HTTP ${r.status}${r.replay ? '  [Idempotent-Replay: true]' : ''}  ${JSON.stringify(r.json)}`);
}

async function partOne() {
  const token = await signIn();
  const state = {
    key: crypto.randomUUID(),
    // Fictitious 9-digit number starting with 9 so it never matches a real student.
    body: {
      student_name: 'Test Replay Student',
      student_number: '9' + String(crypto.randomInt(0, 1e8)).padStart(8, '0'),
      programme_code: 'CS',
      group_code: null
    }
  };
  fs.writeFileSync(STATE, JSON.stringify(state, null, 2));
  console.log(`Operation ID: ${state.key}\nStudent number: ${state.body.student_number}\n`);

  show('1. First request          ', await call('POST', '/api/students', { token, key: state.key, body: state.body }));
  show('2. Same request (retry)   ', await call('POST', '/api/students', { token, key: state.key, body: state.body }));
  console.log('\nExpected: step 2 returns the SAME student_id with Idempotent-Replay: true, not 409 DUPLICATE.');
  console.log('Now restart the server (Ctrl+C, npm start) and run: node scripts/challenge2_lost_response.js --after-restart');
}

async function partTwo() {
  if (!fs.existsSync(STATE)) throw new Error('Run part 1 first.');
  const state = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  const token = await signIn();

  show('3. Retry after restart    ', await call('POST', '/api/students', { token, key: state.key, body: state.body }));
  const changed = { ...state.body, student_name: 'Different Content' };
  show('4. Same ID, other content ', await call('POST', '/api/students', { token, key: state.key, body: changed }));

  const [[count]] = await pool.query(
    'SELECT COUNT(*) AS n FROM students WHERE student_number = ?', [state.body.student_number]);
  const [[receipt]] = await pool.query(
    'SELECT operation_id, created_at, response_json FROM operation_receipts WHERE operation_id = ?', [state.key]);
  console.log(`\nMySQL: students with number ${state.body.student_number} = ${count.n}  (expected 1)`);
  console.log('MySQL: stored receipt =', receipt ? JSON.stringify(receipt) : 'MISSING');
  console.log('Expected: step 3 replays the same reply; step 4 is 422 OPERATION_ID_REUSED.');
}

(process.argv.includes('--after-restart') ? partTwo() : partOne())
  .catch((e) => { console.error(e.message); process.exitCode = 1; })
  .finally(() => pool.end());
