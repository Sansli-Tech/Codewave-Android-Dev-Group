// "Roles and account switching" + student self-service checks. Run against your running server.
//
//   $env:STUDENT_USERNAME="<a claimed test student's username>"
//   $env:STUDENT_PASSWORD="<their password>"
//   node scripts/roles_test.js
//
// Lecturer login comes from SEED_LECTURER_USERNAME / SEED_LECTURER_PASSWORD.
// Use a FICTITIOUS test student. The script changes that student's name and back, asks for a
// number correction and has the lecturer REJECT it, so nothing about the student really changes.
require('dotenv').config();
const crypto = require('crypto');

const API = process.env.API_URL || `http://localhost:${process.env.PORT || 5000}`;
let passed = 0;
let failed = 0;

async function call(method, route, token, body, key) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (method !== 'GET') headers['Idempotency-Key'] = key || crypto.randomUUID();
  const res = await fetch(API + route, { method, headers, body: body && JSON.stringify(body) });
  return { status: res.status, json: await res.json().catch(() => null) };
}

async function login(username, password) {
  const r = await call('POST', '/api/auth/login', null, { username, password });
  if (r.status !== 200) throw new Error(`Sign-in failed for ${username} (${r.status})`);
  return r.json;
}

function check(label, ok, detail) {
  if (ok) passed++; else failed++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  -> ' + detail : ''}`);
}

(async () => {
  if (!process.env.STUDENT_USERNAME) throw new Error('Set STUDENT_USERNAME and STUDENT_PASSWORD first.');
  const lecturer = await login(process.env.SEED_LECTURER_USERNAME, process.env.SEED_LECTURER_PASSWORD);
  const student = await login(process.env.STUDENT_USERNAME, process.env.STUDENT_PASSWORD);
  const L = lecturer.token;
  const T = student.token;

  // --- What a student must NOT reach (enforced on the server, not by hiding buttons) ---
  let r = await call('GET', '/api/students', T);
  check('Student cannot list the roster', r.status === 403, `${r.status} ${r.json && r.json.error}`);
  r = await call('GET', `/api/students/${student.user.studentId}`, T);
  check('Student cannot use lecturer record route', r.status === 403, `${r.status}`);
  r = await call('POST', '/api/students', T, { student_name: 'X Y', student_number: '999999999', programme_code: 'CS' });
  check('Student cannot create students', r.status === 403, `${r.status}`);
  r = await call('GET', '/api/correction-requests', T);
  check('Student cannot see other students\' requests', r.status === 403, `${r.status}`);
  r = await call('GET', '/api/me/student', L);
  check('Lecturer token is refused on student-only routes', r.status === 403, `${r.status}`);
  r = await call('GET', '/api/me/student', 'not-a-real-token');
  check('Invalid token is refused', r.status === 401, `${r.status} ${r.json && r.json.error}`);

  // --- Student self-service ---
  r = await call('GET', '/api/me/student', T);
  check('Student sees own profile + group occupancy', r.status === 200 && r.json.student
    && r.json.student.student_id === student.user.studentId,
    r.json && r.json.student ? `${r.json.student.student_name}, group ${r.json.student.group_code || 'Unassigned'}` +
      (r.json.group ? ` (${r.json.group.active_students}/${r.json.group.capacity})` : '') : `${r.status}`);
  const me = r.json.student;

  r = await call('PUT', '/api/me/student', T, { student_number: '123456789', expected_version: me.record_version });
  check('Student cannot change own student number directly', r.status === 403, `${r.status} ${r.json && r.json.error}`);
  r = await call('PUT', '/api/me/student', T, { group_code: 'G01', expected_version: me.record_version });
  check('Student cannot set own group directly', r.status === 403, `${r.status} ${r.json && r.json.error}`);

  r = await call('PUT', '/api/me/student', T, { student_name: me.student_name + ' Test', expected_version: me.record_version });
  check('Student edits own name', r.status === 200, `${r.status}`);
  const v2 = r.json && r.json.student ? r.json.student.record_version : me.record_version + 1;
  r = await call('PUT', '/api/me/student', T, { student_name: 'Stale Edit', expected_version: me.record_version });
  check('Stale edit is refused, not overwritten', r.status === 409 && r.json.error === 'VERSION_CONFLICT', `${r.status} ${r.json && r.json.error}`);
  r = await call('PUT', '/api/me/student', T, { student_name: me.student_name, expected_version: v2 });
  check('Name changed back', r.status === 200, `${r.status}`);

  // --- Number correction: student asks, only the lecturer can decide ---
  const wanted = '96' + String(crypto.randomInt(0, 1e7)).padStart(7, '0');
  r = await call('POST', '/api/me/correction-requests', T, { requested_student_number: wanted });
  const requestId = r.json && r.json.request_id;
  check('Student requests a number correction', r.status === 201 && r.json.status === 'PENDING', `${r.status}`);
  r = await call('POST', '/api/me/correction-requests', T, { requested_student_number: wanted });
  check('Only one pending request at a time', r.status === 409, `${r.status} ${r.json && r.json.error}`);
  r = await call('GET', '/api/correction-requests', L);
  check('Lecturer sees the pending request', r.status === 200 && r.json.data.some((x) => x.request_id === requestId), `${r.status}`);
  r = await call('POST', `/api/correction-requests/${requestId}/resolve`, L, { decision: 'REJECT', note: 'Roles test' });
  check('Lecturer rejects it (number unchanged)', r.status === 200 && r.json.status === 'REJECTED', `${r.status}`);
  r = await call('GET', '/api/me/student', T);
  check('Student number is unchanged', r.status === 200 && r.json.student.student_number === me.student_number, r.json && r.json.student && r.json.student.student_number);

  console.log(`\n${passed} passed, ${failed} failed`);
  if (failed) process.exitCode = 1;
})().catch((e) => { console.error(e.message); process.exitCode = 1; });
