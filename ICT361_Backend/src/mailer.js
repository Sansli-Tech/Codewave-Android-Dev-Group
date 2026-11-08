// Sends e-mail through Brevo's HTTPS API (https://api.brevo.com/v3/smtp/email).
//
// Why an HTTPS API and not SMTP: Render's free web services block outgoing SMTP ports
// (25, 465, 587), so Gmail/SMTP would work on a laptop but fail when hosted. HTTPS (port 443)
// works everywhere, and Node's built-in fetch needs no extra npm package.
//
// .env:
//   BREVO_API_KEY=...            from Brevo -> SMTP & API -> API keys (keep secret)
//   MAIL_FROM=you@example.com    a sender address verified in Brevo -> Senders
//   MAIL_FROM_NAME=MUStudentSync (optional)
//   MAIL_DEV_LOG=true            LOCAL TESTING ONLY: print e-mails to the server console instead
//                                of sending them. Never set this on a hosted server.

function mode() {
  if (process.env.BREVO_API_KEY && process.env.MAIL_FROM) return 'brevo';
  if (process.env.MAIL_DEV_LOG === 'true') return 'console';
  return null;
}

function isConfigured() {
  return mode() !== null;
}

async function sendMail({ to, subject, text, html }) {
  const m = mode();
  if (m === 'console') {
    console.log(`\n[MAIL_DEV_LOG] To: ${to}\nSubject: ${subject}\n\n${text}\n`);
    return;
  }
  if (m !== 'brevo') throw new Error('E-mail is not configured (BREVO_API_KEY and MAIL_FROM).');

  const res = await fetch('https://api.brevo.com/v3/smtp/email', {
    method: 'POST',
    headers: {
      'api-key': process.env.BREVO_API_KEY,
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    body: JSON.stringify({
      sender: { email: process.env.MAIL_FROM, name: process.env.MAIL_FROM_NAME || 'MUStudentSync' },
      to: [{ email: to }],
      subject,
      textContent: text,
      htmlContent: html || undefined
    }),
    signal: AbortSignal.timeout(15000)
  });
  if (!res.ok) {
    // Brevo explains the problem in the body (e.g. unverified sender); never log the API key.
    throw new Error(`Brevo returned ${res.status}: ${(await res.text()).slice(0, 300)}`);
  }
}

module.exports = { sendMail, isConfigured };
