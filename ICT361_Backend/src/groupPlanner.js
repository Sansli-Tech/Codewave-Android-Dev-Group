// Group creation, auto-assignment and Excel export (lecturer request, Nov 2026).
//
// Rules:
//   - A new group gets the next free code (G15, G16, ...) and capacity 15.
//   - Auto-assign places ONLY students who have no group. Nobody already placed is moved.
//   - Groups are filled in code order, each up to 15 (groups that allow 16 keep their
//     16th place for manual assignment). New groups are created only when all are full.
//   - Students are taken in name order (then number), so the result is predictable.
const { toXlsx } = require('./xlsx');

const AUTO_FILL_LIMIT = 15;
const NEW_GROUP_CAPACITY = 15;

function codeNumber(code) {
  const m = /^G(\d+)$/.exec(code || '');
  return m ? Number(m[1]) : 0;
}

function formatCode(n) {
  return 'G' + String(n).padStart(2, '0');
}

/** The next free group code after every existing one, e.g. [G01..G14] -> G15. */
function nextCode(existingCodes) {
  let max = 0;
  for (const c of existingCodes) max = Math.max(max, codeNumber(c));
  return formatCode(max + 1);
}

/**
 * Pure planning step (no database), so it can be previewed and tested.
 *   groups:     [{ group_code, capacity, active }]   existing groups, any order
 *   unassigned: [{ student_id }]                     already in the order to place them
 * Returns { assignments: [{ student_id, group_code }], placements: [...], newGroups: [code] }.
 */
function plan(groups, unassigned) {
  const ordered = [...groups].sort((a, b) => codeNumber(a.group_code) - codeNumber(b.group_code));
  const placements = new Map();
  for (const g of ordered) {
    placements.set(g.group_code, {
      group_code: g.group_code, new_group: false,
      capacity: Number(g.capacity), before: Number(g.active), added: 0
    });
  }
  const codes = ordered.map((g) => g.group_code);
  const newGroups = [];
  const assignments = [];

  let i = 0;
  for (const g of ordered) {
    const p = placements.get(g.group_code);
    const room = Math.min(p.capacity, AUTO_FILL_LIMIT) - p.before;
    for (let k = 0; k < room && i < unassigned.length; k++, i++) {
      assignments.push({ student_id: unassigned[i].student_id, group_code: g.group_code });
      p.added++;
    }
  }
  while (i < unassigned.length) {
    const code = nextCode(codes);
    codes.push(code);
    newGroups.push(code);
    const p = { group_code: code, new_group: true, capacity: NEW_GROUP_CAPACITY, before: 0, added: 0 };
    placements.set(code, p);
    for (let k = 0; k < NEW_GROUP_CAPACITY && i < unassigned.length; k++, i++) {
      assignments.push({ student_id: unassigned[i].student_id, group_code: code });
      p.added++;
    }
  }

  const changed = [...placements.values()]
    .filter((p) => p.added > 0)
    .map((p) => ({ ...p, after: p.before + p.added }));
  return { assignments, placements: changed, newGroups };
}

/** Same shape as GET /api/groups, so the app can refresh its group list from any reply. */
async function listGroups(conn) {
  const [groups] = await conn.query(`
    SELECT g.group_id, g.group_code, g.capacity,
           COUNT(s.student_id) AS active_students,
           g.capacity - COUNT(s.student_id) AS available_places
    FROM lab_groups g
    LEFT JOIN students s ON s.group_id = g.group_id AND s.is_deleted = FALSE
    GROUP BY g.group_id, g.group_code, g.capacity
    ORDER BY g.group_code`);
  return groups;
}

/**
 * Reads the current state. With lock=true (inside the auto-assign transaction) the group rows
 * are locked FIRST, in code order, then the students: the same order every other route uses
 * (group, then student), so this cannot deadlock with a single assignment running at the same time.
 */
async function readState(conn, lock) {
  const [groups] = await conn.query(
    `SELECT group_id, group_code, capacity FROM lab_groups ORDER BY group_code${lock ? ' FOR UPDATE' : ''}`);
  const [counts] = await conn.query(
    `SELECT group_id, COUNT(*) AS n FROM students
     WHERE is_deleted = FALSE AND group_id IS NOT NULL GROUP BY group_id${lock ? ' LOCK IN SHARE MODE' : ''}`);
  const [unassigned] = await conn.query(
    `SELECT student_id FROM students
     WHERE is_deleted = FALSE AND group_id IS NULL
     ORDER BY student_name, student_number${lock ? ' FOR UPDATE' : ''}`);
  const byId = new Map(counts.map((c) => [c.group_id, Number(c.n)]));
  return {
    groups: groups.map((g) => ({ ...g, active: byId.get(g.group_id) || 0 })),
    unassigned
  };
}

/** Creates the next group. Must run inside a transaction. */
async function createNextGroup(conn) {
  // Locks every group row (and the gaps), so two lecturers creating at once get G15 and G16.
  const [rows] = await conn.query('SELECT group_code FROM lab_groups FOR UPDATE');
  const code = nextCode(rows.map((r) => r.group_code));
  await conn.query('INSERT INTO lab_groups (group_code, capacity) VALUES (?, ?)', [code, NEW_GROUP_CAPACITY]);
  return { group_code: code, capacity: NEW_GROUP_CAPACITY };
}

/** Places every unassigned student. Must run inside a transaction. */
async function autoAssign(conn) {
  const state = await readState(conn, true);
  const result = plan(state.groups, state.unassigned);

  const idByCode = new Map(state.groups.map((g) => [g.group_code, g.group_id]));
  for (const code of result.newGroups) {
    const [ins] = await conn.query('INSERT INTO lab_groups (group_code, capacity) VALUES (?, ?)',
      [code, NEW_GROUP_CAPACITY]);
    idByCode.set(code, ins.insertId);
  }

  const byGroup = new Map();
  for (const a of result.assignments) {
    if (!byGroup.has(a.group_code)) byGroup.set(a.group_code, []);
    byGroup.get(a.group_code).push(a.student_id);
  }
  for (const [code, ids] of byGroup) {
    await conn.query(
      `UPDATE students SET group_id = ?, record_version = record_version + 1
       WHERE student_id IN (?) AND group_id IS NULL AND is_deleted = FALSE`,
      [idByCode.get(code), ids]);
  }
  if (result.assignments.length) {
    const ids = result.assignments.map((a) => a.student_id);
    const [versions] = await conn.query(
      'SELECT student_id, record_version FROM students WHERE student_id IN (?)', [ids]);
    await conn.query('INSERT INTO sync_changes (student_id, record_version, change_type) VALUES ?',
      [versions.map((v) => [v.student_id, v.record_version, 'UPSERT'])]);
  }
  return { assigned: result.assignments.length, placements: result.placements, new_groups: result.newGroups };
}

/** A real .xlsx (Buffer): a Summary sheet, one sheet per group, and an Unassigned sheet if needed. */
async function buildWorkbook(conn) {
  const groups = await listGroups(conn);
  const [students] = await conn.query(`
    SELECT s.student_number, s.student_name, p.programme_name, g.group_code
    FROM students s
    JOIN programmes p ON p.programme_id = s.programme_id
    LEFT JOIN lab_groups g ON g.group_id = s.group_id
    WHERE s.is_deleted = FALSE
    ORDER BY g.group_code, s.student_name, s.student_number`);

  const unassigned = students.filter((s) => !s.group_code);
  const summaryRows = [['Group', 'Capacity', 'Students', 'Places left']];
  let total = 0;
  for (const g of groups) {
    const n = Number(g.active_students);
    total += n;
    summaryRows.push([g.group_code, Number(g.capacity), n, Number(g.capacity) - n]);
  }
  summaryRows.push([]);
  summaryRows.push(['Unassigned', null, unassigned.length]);
  summaryRows.push(['Total', null, total + unassigned.length]);
  const totalRow = summaryRows.length - 1;
  summaryRows.push([]);
  summaryRows.push([`Exported ${new Date().toISOString().slice(0, 16).replace('T', ' ')} UTC`]);

  const sheets = [{ name: 'Summary', widths: [14, 10, 10, 12], rows: summaryRows, boldRows: [totalRow] }];
  const groupSheet = (name, list) => ({
    name,
    widths: [6, 16, 32, 40],
    rows: [['No.', 'Student number', 'Name', 'Programme'],
      ...list.map((s, i) => [i + 1, String(s.student_number), s.student_name, s.programme_name])]
  });
  for (const g of groups) sheets.push(groupSheet(g.group_code, students.filter((s) => s.group_code === g.group_code)));
  if (unassigned.length) sheets.push(groupSheet('Unassigned', unassigned));
  return toXlsx(sheets, { title: 'MUStudentSync lab groups' });
}

module.exports = {
  AUTO_FILL_LIMIT, NEW_GROUP_CAPACITY,
  nextCode, plan, listGroups, readState, createNextGroup, autoAssign, buildWorkbook
};
