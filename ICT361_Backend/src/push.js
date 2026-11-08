// Firebase Cloud Messaging (push to phones, even when the app is closed).
//
// Setup (see docs/DEPLOYMENT.md, "Push notifications"):
//   npm install firebase-admin
//   .env / Render:  FIREBASE_SERVICE_ACCOUNT=<the whole service-account JSON on one line>
//               or  FIREBASE_SERVICE_ACCOUNT_FILE=./firebase-service-account.json   (never commit it)
// Without these the server still works: notifications are stored and shown in the app,
// only the phone alert is skipped.
//
// PRIVACY: the alert text is generic. It never contains names, numbers, group codes or the
// announcement itself. Phones that signed out get "Sign in to read it". On Android the alert is
// also marked "private", so the lock screen hides even the generic text when the user chose that.
const fs = require('fs');
const path = require('path');
const pool = require('./config/database');

let messaging;   // undefined = not tried yet, null = not configured
function client() {
  if (messaging !== undefined) return messaging;
  messaging = null;
  try {
    let json = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (!json && process.env.FIREBASE_SERVICE_ACCOUNT_FILE) {
      json = fs.readFileSync(path.resolve(__dirname, '..', process.env.FIREBASE_SERVICE_ACCOUNT_FILE), 'utf8');
    }
    if (!json) {
      console.log('Push notifications are off (no FIREBASE_SERVICE_ACCOUNT).');
      return messaging;
    }
       // Modular API (firebase-admin 10 and newer); installed with: npm install firebase-admin
       const { initializeApp, getApps, cert } = require('firebase-admin/app');
       const { getMessaging } = require('firebase-admin/messaging');
       const app = getApps().length ? getApps()[0] : initializeApp({ credential: cert(JSON.parse(json)) });
       messaging = getMessaging(app);
    console.log('Push notifications are on.');
  } catch (e) {
    console.error('Push notifications are off:', e.message);
  }
  return messaging;
}

const DEAD_TOKEN = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
  'messaging/invalid-argument'
]);

/** One generic alert per registered phone of these accounts. */
async function sendToAccounts(accountIds) {
  const fcm = client();
  if (!fcm || !accountIds.length) return;
  const [devices] = await pool.query(
    `SELECT d.token, d.account_id, d.signed_in,
            (SELECT COUNT(*) FROM notifications n WHERE n.account_id = d.account_id AND n.read_at IS NULL) AS unread
     FROM device_tokens d WHERE d.account_id IN (?)`, [accountIds]);
  if (!devices.length) return;

  const messages = devices.map((d) => {
    const unread = Number(d.unread);
    const body = !d.signed_in
      ? 'You have a new notification. Sign in to read it.'
      : unread === 1 ? 'You have 1 unread notification.' : `You have ${unread} unread notifications.`;
    return {
      token: d.token,
      notification: { title: 'MUStudentSync', body },
      data: { kind: 'refresh', unread: String(unread) },
      android: {
        priority: 'high',
        collapseKey: 'mu_updates',               // one alert that updates, not a pile of them
        notification: {
          channelId: 'mu_updates',
          tag: 'mu_updates',
          visibility: 'private',
          notificationCount: unread
        }
      }
    };
  });

  for (let i = 0; i < messages.length; i += 500) {
    const chunk = messages.slice(i, i + 500);
    const result = await fcm.sendEach(chunk);
    const dead = [];
    result.responses.forEach((r, k) => {
      if (!r.success && r.error && DEAD_TOKEN.has(r.error.code)) dead.push(chunk[k].token);
    });
    if (dead.length) await pool.query('DELETE FROM device_tokens WHERE token IN (?)', [dead]);
  }
}

module.exports = { sendToAccounts };
