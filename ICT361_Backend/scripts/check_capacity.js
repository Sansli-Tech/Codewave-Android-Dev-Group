// Lists every lab group with its active students and capacity, and flags any group that breaks
// the brief's rule of at most 15 active students.
//
//   node scripts/check_capacity.js           report only, changes nothing
//   node scripts/check_capacity.js --apply   sets capacity to 15 for groups that have 15 or fewer
//                                            active students. Groups that already hold 16 are NOT
//                                            changed: a lecturer must first move or unassign a student.
require('dotenv').config();
const pool = require('../src/config/database');

(async () => {
  const apply = process.argv.includes('--apply');
  const [rows] = await pool.query(
    `SELECT g.group_id, g.group_code, g.capacity, COUNT(s.student_id) AS active
     FROM lab_groups g
     LEFT JOIN students s ON s.group_id = g.group_id AND s.is_deleted = FALSE
     GROUP BY g.group_id, g.group_code, g.capacity ORDER BY g.group_code`);

  const needsMove = [];
  for (const g of rows) {
    const active = Number(g.active);
    const capacity = Number(g.capacity);
    let note = '';
    if (capacity > 15 && active <= 15) {
      note = apply ? '-> capacity set to 15' : 'capacity above 15 (fixable with --apply)';
      if (apply) await pool.query('UPDATE lab_groups SET capacity = 15 WHERE group_id = ?', [g.group_id]);
    } else if (active > 15) {
      note = `${active - 15} student(s) must be moved or unassigned by a lecturer first`;
      needsMove.push(g.group_code);
    }
    console.log(`${g.group_code}  ${String(active).padStart(2)}/${capacity}  ${note}`);
  }
  console.log(needsMove.length
    ? `\nOver the 15-student limit: ${needsMove.join(', ')}. Nothing was changed for these groups.`
    : '\nEvery group is within the 15-student limit.');
  await pool.end();
})().catch((e) => { console.error(e.message); process.exitCode = 1; });
