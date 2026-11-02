// Checks a sign-in against the database this terminal is connected to, and can reset a password.
//
//   node scripts/check_login.js            list the lecturer accounts, then test a username + password
//   node scripts/check_login.js --reset    set a new password for an existing account
//
// Uses DB_* from .env, or from $env:DB_* if set in this terminal (e.g. the Aiven settings).
// Passwords are typed into the terminal, never printed, and only bcrypt hashes are stored.
require('dotenv').config();
const readline = require('readline');
const bcrypt = require('bcryptjs');
const pool = require('../src/config/database');

function ask(question, hidden) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    if (hidden) {
      rl._writeToOutput = (s) => { if (s.startsWith(question)) rl.output.write(s); else rl.output.write('*'); };
    }
    rl.question(question, (answer) => { rl.close(); if (hidden) process.stdout.write('\n'); resolve(answer); });
  });
}

(async () => {
  const reset = process.argv.includes('--reset');
  console.log(`Database: ${process.env.DB_NAME} (${process.env.DB_SSL === 'true' ? 'cloud, SSL' : 'local'})`);

  const [lecturers] = await pool.query(
    "SELECT username, is_active FROM accounts WHERE role = 'LECTURER' ORDER BY created_at");
  if (!lecturers.length) {
    console.log('No lecturer accounts in this database. Run: npm run seed:lecturer');
    return;
  }
  console.log('Lecturer accounts here:');
  for (const l of lecturers) console.log(`  [${l.username}]${l.is_active ? '' : '  (disabled)'}`);

  const username = (await ask('Username to check: ')).trim();
  const [rows] = await pool.query('SELECT account_id, password_hash, is_active FROM accounts WHERE username = ?', [username]);
  if (!rows.length) {
    console.log(`No account called [${username}]. Use one of the names above exactly (the brackets are not part of it).`);
    return;
  }

  if (reset) {
    const p1 = await ask('New password (12+ characters): ', true);
    const p2 = await ask('Type it again: ', true);
    if (p1 !== p2) return console.log('The two passwords are different. Nothing changed.');
    if (p1.length < 12) return console.log('Too short: at least 12 characters. Nothing changed.');
    await pool.query('UPDATE accounts SET password_hash = ?, is_active = 1 WHERE account_id = ?',
      [await bcrypt.hash(p1, 12), rows[0].account_id]);
    console.log('Password changed. Sign in with it now.');
    return;
  }

  const password = await ask('Password to check: ', true);
  if (!rows[0].is_active) console.log('This account is DISABLED, so sign-in is refused.');
  else if (await bcrypt.compare(password, rows[0].password_hash)) console.log('Username and password are CORRECT for this database.');
  else console.log('The password is WRONG for this account. To set a new one: node scripts/check_login.js --reset');
})()
  .catch((e) => { console.error(e.message); process.exitCode = 1; })
  .finally(() => pool.end());
