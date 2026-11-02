// Challenge 1 - "Two phones request the last place". Run against your own running server.
//
//   node scripts/challenge1_last_place.js            (group G01, 20 runs)
//   node scripts/challenge1_last_place.js G03 10     (another group / number of runs)
//
// What it does:
//   1. Tops the group up with FICTITIOUS students (numbers starting 97) until exactly one place is left.
//   2. Each run: two fresh unassigned test students ask for that last place AT THE SAME TIME,
//      from two separate connections. Odd runs both use POST /api/groups/:code/assign; even runs race
//      the assign route against PUT /api/students/:id (the editor's transfer path).
//   3. Checks exactly one got the place and the other got GROUP_FULL, then reads MySQL to confirm the
//      group never went over capacity. The winner is moved back out so the next run starts the same.
//   4. Soft-deletes every test student it created.
//
// Uses SEED_LECTURER_USERNAME / SEED_LECTURER_PASSWORD from .env (or the terminal) to sign in.
// It never moves or deletes students it did not create. If the group already has no free place,
// it stops and tells you instead.
require('dotenv').config();
const crypto = require('crypto');
const pool = require('../src/config/database');

const API = process.env.API_URL || `http://localhost:${process.env.PORT || 5000}`;
const GROUP = (process.argv[2] || 'G01').toUpperCase();
const RUNS = Number(process.argv[3] || 20);
const created = [];   // { id, version } of every test student, for clean-up

async function call(method, route, token, body) {
  const res = await fetch(API + route, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': crypto.randomUUID()
    },
    body: body && JSON.stringify(body)
  });
  return { status: res.status, json: await res.json().catch(() => null) };
}

async function signIn() {
  const res = await fetch(API + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: process.env.SEED_LECTURER_USERNAME,
      password: process.env.SEED_LECTURER_PASSWORD
    })
  });
  if (res.status !== 200) throw new Error(`Lecturer sign-in failed (${res.status}).`);
  return (await res.json()).token;
}

function testNumber() {
  return '97' + String(crypto.randomInt(0, 1e7)).padStart(7, '0');
}

// Names may only contain letters, so run numbers become letters: 1 -> A, 2 -> B, 27 -> AA.
function letters(n) {
  let out = '';
  for (let x = n; x > 0; x = Math.floor((x - 1) / 26)) out = String.fromCharCode(65 + ((x - 1) % 26)) + out;
  return out;
}

async function newStudent(token, label) {
  const body = {
    student_name: `Race Test ${label}`,
    student_number: testNumber(),
    programme_code: 'CS',
    group_code: null
  };
  const r = await call('POST', '/api/students', token, body);
  if (r.status !== 201) throw new Error(`Could not create test student: ${r.status} ${JSON.stringify(r.json)}`);
  const s = { id: r.json.student_id, version: 1, body };
  created.push(s);
  return s;
}

async function occupancy() {
  const [[row]] = await pool.query(
    `SELECT g.capacity, COUNT(s.student_id) AS active
     FROM lab_groups g
     LEFT JOIN students s ON s.group_id = g.group_id AND s.is_deleted = FALSE
     WHERE g.group_code = ? GROUP BY g.group_id, g.capacity`, [GROUP]);
  if (!row) throw new Error(`Group ${GROUP} does not exist.`);
  return { capacity: Number(row.capacity), active: Number(row.active) };
}

async function currentVersion(id) {
  const [[row]] = await pool.query('SELECT record_version FROM students WHERE student_id = ?', [id]);
  return Number(row.record_version);
}

// Ask for the place through either route.
function request(token, student, viaPut) {
  if (!viaPut) return call('POST', `/api/groups/${GROUP}/assign`, token, { student_id: student.id });
  return call('PUT', `/api/students/${student.id}`, token,
    { ...student.body, group_code: GROUP, expected_version: student.version });
}

async function main() {
  const token = await signIn();
  let { capacity, active } = await occupancy();
  console.log(`${GROUP}: capacity ${capacity}, ${active} active students before the test.`);

  if (active >= capacity) {
    throw new Error(`${GROUP} is already full. Pick a group with at least one free place.`);
  }
  // Fill up to capacity - 1 with test students, so exactly one place is left.
  let filled = 0;
  while (active < capacity - 1) {
    const s = await newStudent(token, `Filler ${letters(++filled)}`);
    const r = await call('POST', `/api/groups/${GROUP}/assign`, token, { student_id: s.id });
    if (r.status !== 200) throw new Error(`Could not fill ${GROUP}: ${r.status} ${JSON.stringify(r.json)}`);
    s.version = await currentVersion(s.id);
    active++;
  }
  console.log(`Filled with ${filled} fictitious test students: ${capacity - 1}/${capacity}, one place left.\n`);

  let passed = 0;
  for (let run = 1; run <= RUNS; run++) {
    const a = await newStudent(token, `Alpha ${letters(run)}`);
    const b = await newStudent(token, `Bravo ${letters(run)}`);
    const mixed = run % 2 === 0;

    // Both requests leave at the same moment.
    const [ra, rb] = await Promise.all([request(token, a, false), request(token, b, mixed)]);

    const ok = [ra, rb].filter((r) => r.status === 200);
    const full = [ra, rb].filter((r) => r.status === 409 && r.json && r.json.error === 'GROUP_FULL');
    const after = await occupancy();
    const good = ok.length === 1 && full.length === 1 && after.active === capacity;
    if (good) passed++;

    console.log(
      `Run ${String(run).padStart(2)} (${mixed ? 'assign vs PUT   ' : 'assign vs assign'}): ` +
      `A=${ra.status}${ra.json && ra.json.error ? ' ' + ra.json.error : ''}  ` +
      `B=${rb.status}${rb.json && rb.json.error ? ' ' + rb.json.error : ''}  ` +
      `MySQL ${after.active}/${after.capacity}  ${good ? 'PASS' : 'FAIL'}`);

    // Move the winner back out so the next run starts with one place left again.
    const winner = ra.status === 200 ? a : b;
    winner.version = await currentVersion(winner.id);
    const out = await call('PUT', `/api/students/${winner.id}`, token,
      { ...winner.body, group_code: null, expected_version: winner.version });
    if (out.status !== 200) throw new Error(`Could not free the place: ${out.status} ${JSON.stringify(out.json)}`);
  }

  console.log(`\n${passed}/${RUNS} runs passed: exactly one request took the last place, ` +
    `the other got GROUP_FULL, and ${GROUP} never went over ${capacity}.`);
  if (passed !== RUNS) process.exitCode = 1;
}

async function cleanUp() {
  if (!created.length) return;
  const token = await signIn().catch(() => null);
  if (!token) return;
  for (const s of created) {
    const version = await currentVersion(s.id).catch(() => null);
    if (version) await call('DELETE', `/api/students/${s.id}`, token, { expected_version: version });
  }
  console.log(`Clean-up: soft-deleted ${created.length} test students (their numbers stay reserved).`);
}

main()
  .catch((e) => { console.error(e.message); process.exitCode = 1; })
  .finally(async () => { await cleanUp().catch((e) => console.error('Clean-up:', e.message)); await pool.end(); });
