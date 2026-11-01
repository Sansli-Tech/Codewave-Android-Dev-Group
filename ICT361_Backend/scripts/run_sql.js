// Runs a .sql file against the database in .env (handy when the mysql command is not on PATH).
//   node scripts/run_sql.js db/migrations/002_correction_requests.sql
require('dotenv').config();
const fs = require('fs');
const mysql = require('mysql2/promise');

(async () => {
  const file = process.argv[2];
  if (!file) throw new Error('Usage: node scripts/run_sql.js <file.sql>');
  const conn = await mysql.createConnection({
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME,
    multipleStatements: true
  });
  await conn.query(fs.readFileSync(file, 'utf8'));
  await conn.end();
  console.log(`Ran ${file}`);
})().catch((e) => { console.error(e.message); process.exitCode = 1; });
