
require('dotenv').config();

const bcrypt = require('bcryptjs');
const { randomUUID } = require('crypto');
const pool = require('./config/database');

async function seedLecturer() {
  const username = process.env.SEED_LECTURER_USERNAME;
  const password = process.env.SEED_LECTURER_PASSWORD;

  if (!username || !password || password.length < 12) {
    throw new Error(
      'Set a lecturer username and a password of at least 12 characters.'
    );
  }

  const [existing] = await pool.execute(
    'SELECT account_id FROM accounts WHERE username = ?',
    [username]
  );

  if (existing.length) {
    console.log('Lecturer account already exists; no changes made.');
    return;
  }

  const passwordHash = await bcrypt.hash(password, 12);

  await pool.execute(
    `INSERT INTO accounts
      (account_id, username, password_hash, role, student_id)
     VALUES (?, ?, ?, 'LECTURER', NULL)`,
    [randomUUID(), username, passwordHash]
  );

  console.log('Lecturer account created successfully.');
}

seedLecturer()
  .catch(error => {
    console.error('Lecturer setup failed:', error.message);
    process.exitCode = 1;
  })
  .finally(async () => {
    await pool.end();
  });
