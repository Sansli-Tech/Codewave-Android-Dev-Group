// Runs a .sql file against the database in .env (handy when the mysql command is not on PATH).
//   node scripts/run_sql.js db/migrations/002_correction_requests.sql
require('dotenv').config();
const fs = require('fs');
const mysql = require('mysql2/promise');
const dbOptions = require('../src/config/dbOptions');

(async () => {
  const file = process.argv[2];
  if (!file) throw new Error('Usage: node scripts/run_sql.js <file.sql>');
  const conn = await mysql.createConnection(dbOptions({ multipleStatements: true }));
  await conn.query(fs.readFileSync(file, 'utf8'));
  await conn.end();
  console.log(`Ran ${file}`);
})().catch((e) => { console.error(e.message); process.exitCode = 1; });
