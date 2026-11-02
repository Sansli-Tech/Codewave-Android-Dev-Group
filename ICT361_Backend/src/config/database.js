const mysql = require('mysql2/promise');
const dbOptions = require('./dbOptions');

const pool = mysql.createPool(dbOptions({
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
}));

module.exports = pool;
