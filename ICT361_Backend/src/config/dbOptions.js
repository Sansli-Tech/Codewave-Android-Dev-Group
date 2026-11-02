// MySQL connection settings from .env, shared by the server and the scripts.
//
// Local MySQL: no SSL settings needed.
// Cloud MySQL (e.g. Aiven): set DB_SSL=true and give the provider's CA certificate, either
//   DB_CA_FILE=./ca.pem             (a file next to package.json; never commit it), or
//   DB_CA_CERT=<the PEM text>       (on a host such as Render, where there is no file).
// The certificate lets Node check it is really talking to your database before sending the password.
require('dotenv').config();
const fs = require('fs');
const path = require('path');

function sslOptions() {
  if (process.env.DB_SSL !== 'true') return undefined;
  let ca;
  if (process.env.DB_CA_CERT) {
    ca = process.env.DB_CA_CERT.replace(/\\n/g, '\n');   // pasted with \n instead of line breaks
  } else if (process.env.DB_CA_FILE) {
    ca = fs.readFileSync(path.resolve(__dirname, '..', '..', process.env.DB_CA_FILE), 'utf8');
  }
  return ca ? { ca, rejectUnauthorized: true } : { rejectUnauthorized: true };
}

module.exports = function dbOptions(extra = {}) {
  return {
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER,
    password: process.env.DB_PASSWORD,
    database: process.env.DB_NAME,
    ssl: sslOptions(),
    ...extra
  };
};
