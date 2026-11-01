// Creates a FICTITIOUS test student with a claimed account, for the roles test.
//   node scripts/make_test_student.js
// Needs the server running and SEED_LECTURER_USERNAME / SEED_LECTURER_PASSWORD (from .env or the terminal).
require('dotenv').config();
const crypto = require('crypto');
const API = process.env.API_URL || `http://localhost:${process.env.PORT || 5000}`;

async function call(method, route, token, body) {
  const headers = { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(API + route, { method, headers, body: body && JSON.stringify(body) });
  return { status: res.status, json: await res.json().catch(() => null) };
}

(async () => {
  const login = await call('POST', '/api/auth/login', null, {
    username: process.env.SEED_LECTURER_USERNAME, password: process.env.SEED_LECTURER_PASSWORD });
  if (login.status !== 200) throw new Error(`Lecturer sign-in failed (${login.status})`);
  const L = login.json.token;

  const number = '95' + String(crypto.randomInt(0, 1e7)).padStart(7, '0');
  const created = await call('POST', '/api/students', L, {
    student_name: 'Roles Test Student', student_number: number, programme_code: 'CS', group_code: null });
  if (created.status !== 201) throw new Error(`Create failed: ${created.status} ${JSON.stringify(created.json)}`);

  const code = await call('POST', `/api/students/${created.json.student_id}/claim-code`, L);
  if (code.status !== 201) throw new Error(`Claim code failed: ${code.status} ${JSON.stringify(code.json)}`);

  const password = 'Test-' + crypto.randomBytes(8).toString('hex');   // 21 characters
  const claim = await call('POST', '/api/auth/claim', null, {
    student_number: number, claim_code: code.json.claim_code, username: number, password });
  if (claim.status !== 201) throw new Error(`Claim failed: ${claim.status} ${JSON.stringify(claim.json)}`);

  console.log('Test student ready. Paste these two lines, then run: npm run test:roles');
  console.log(`$env:STUDENT_USERNAME="${number}"`);
  console.log(`$env:STUDENT_PASSWORD="${password}"`);
})().catch((e) => { console.error(e.message); process.exitCode = 1; });