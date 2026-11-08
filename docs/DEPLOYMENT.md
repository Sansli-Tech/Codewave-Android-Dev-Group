# Hosting the backend (HTTPS, no USB cable)

The API runs on **Render** (free web service, HTTPS included) and the database on **Aiven for MySQL**
(free plan, encrypted connections). The phone then talks to `https://<name>.onrender.com/api/` over
Wi-Fi or mobile data, with no laptop needed.

**Only fictitious data goes into the cloud database.** The real class list stays in your local MySQL.

## Limits of the free plans

| | Limit | What it means for us |
|---|---|---|
| Render free web service | Sleeps after 15 minutes with no requests; waking takes about one minute. 750 hours per month. | The first request after a break is slow. The app now waits up to 60 seconds, and changes saved on the phone are kept and retried anyway. Open `/api/health` in a browser a minute before a demo. |
| Aiven free MySQL | 1 GB storage, 1 GB RAM, 76 connections. May be powered off when not used; it can be turned back on. | Plenty for the lab. If the API reports the database is unreachable, check the service is running in the Aiven console. |

## Part 1: Aiven MySQL (can be done now)

1. Sign up at aiven.io, create a project, then **Create service → MySQL → Free plan**. Pick the region closest to Zambia that's offered.
2. Wait until the service shows **Running**.
3. On the service **Overview** page, note: **Host, Port, User** (`avnadmin`), **Password**, **Database name** (`defaultdb`).
4. Download the **CA certificate** and save it as `ICT361_Backend/ca.pem`. `*.pem` is gitignored, so it won't be committed.

### Load the tables into Aiven from your PC

In a PowerShell terminal **in `ICT361_Backend`**. These settings last only for this terminal and
take priority over `.env`, so your local `.env` is untouched. Type the values; don't paste them into chats.

```powershell
$env:DB_HOST = Read-Host "Aiven host"
$env:DB_PORT = Read-Host "Aiven port"
$env:DB_USER = "avnadmin"
$env:DB_PASSWORD = Read-Host "Aiven password"
$env:DB_NAME = "defaultdb"
$env:DB_SSL = "true"
$env:DB_CA_FILE = "./ca.pem"
```

```powershell
npm run db:schema
npm run db:migrate:002
npm run db:seed-reference
```

Each prints `Ran db/...`. Then create the lecturer **in the cloud database**, with a new password of at least 12 characters:

```powershell
$env:SEED_LECTURER_USERNAME = Read-Host "Cloud lecturer username"
$env:SEED_LECTURER_PASSWORD = Read-Host "Cloud lecturer password (12+ characters)"
npm run seed:lecturer
```

Close this terminal when done, so later commands use your local `.env` again.

## Part 2: Render (needs the code on GitHub)

Render deploys from a Git repository. The lecturer asked us to push only **after** reviewing the
commits in Git GUI, so do this part after that review, or after asking the lecturer.

1. Sign up at render.com with GitHub and allow access to the repository.
2. **New → Web Service** → choose the repository.
3. Settings:

   | Setting | Value |
   |---|---|
   | Root Directory | `ICT361_Backend` |
   | Runtime | Node |
   | Build Command | `npm ci` |
   | Start Command | `npm start` |
   | Instance type | Free |
   | Health Check Path | `/api/health` |

4. **Environment variables.** Render sets `PORT` itself; don't add it.

   | Key | Value |
   |---|---|
   | `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` | from Aiven |
   | `DB_SSL` | `true` |
   | `DB_CA_CERT` | the whole contents of `ca.pem`, from `-----BEGIN CERTIFICATE-----` to `-----END CERTIFICATE-----` |
   | `JWT_SECRET` | a **new** long random string: `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"` |
   | `TRUST_PROXY` | `true` |
   | `REQUIRE_HTTPS` | `true` |
   | `LECTURER_REGISTRATION_CODE` | a new code, or empty to switch lecturer registration off |

5. **Create Web Service**. When the log says `ICT361 API running on port ...`, open
   `https://<name>.onrender.com/api/health` and check it shows `{"status":"OK","database":"connected"}`.
6. Check HTTPS is enforced: `http://<name>.onrender.com/api/students` should redirect or be refused, never served.

## Part 3: Point the app at the hosted API

1. Open `local.properties` in the project root (not committed to Git) and add:

   ```
   api.base.url=https://<name>.onrender.com/api/
   ```

   Keep the trailing slash.
2. **File → Sync Project with Gradle Files**, then run the app. The USB cable and `adb reverse` aren't needed.
3. To go back to the laptop backend, delete that line (the default is `http://localhost:5000/api/`) and sync again.

The release APK uses the same setting, so set `api.base.url` to the hosted address before building
the APK you submit.

## Part 4: Test students and scripts against the hosted API

The scripts read `API_URL`. In a terminal with the Aiven settings from Part 1 (the scripts also read the database):

```powershell
$env:API_URL = "https://<name>.onrender.com"
npm run test:make-student
npm run test:challenge2
npm run test:challenge1 -- G08
```

## Keeping it awake for a demo (optional)

A free monitor such as cron-job.org can request `https://<name>.onrender.com/api/health` every 10
minutes. That keeps the service from sleeping, and the 750 free hours cover one service for a
whole month.

## E-mail for "Forgot password?" (Brevo)

Render's free plan blocks the normal mail ports (SMTP), so codes are sent through **Brevo's HTTPS
API**, which works both on your laptop and on Render. The free plan allows 300 e-mails a day.

1. Sign up at brevo.com (free).
2. **Senders, domains & dedicated IPs → Senders → Add a sender**: use an address you can open,
   for example a Gmail made for the app, and click the link Brevo e-mails to it.
3. **SMTP & API → API keys → Generate a new API key**. Copy it once; don't send it to anyone.
4. Local `.env` and Render **Environment**:

   | Key | Value |
   |---|---|
   | `BREVO_API_KEY` | the key |
   | `MAIL_FROM` | the verified sender address |
   | `MAIL_FROM_NAME` | `MUStudentSync` |

5. Restart the server (Render restarts by itself after you save).

To test on your laptop **without** sending real e-mail, set `MAIL_DEV_LOG=true` (and leave
`BREVO_API_KEY` empty): the code is printed in the `npm start` terminal. Never set it on Render.

If e-mails don't arrive: check the spam folder, and look at the server log for
`Reset e-mail failed: Brevo returned …`, which says what Brevo didn't accept (often an unverified sender).

## Push notifications (Firebase Cloud Messaging)

Without this, everything still works inside the app (bell count, Notifications tab); only the
alert on a closed phone is missing. Firebase's messaging is free.

**Privacy.** The alert says only "MUStudentSync — You have N unread notifications", or
"You have a new notification. Sign in to read it." on a phone that signed out. Names, numbers,
groups and announcement text never leave the server in a push; they are read in the app after
sign-in. The alert is *private*, so the lock screen shows only "MUStudentSync".

1. Go to console.firebase.google.com → **Add project** (e.g. `MUStudentSync`); Google Analytics
   is not needed.
2. **Add app → Android**. Package name: `com.example.mustudentsync`. Download
   `google-services.json` and put it in the **`app/`** folder (next to `app/build.gradle`).
   It is in `.gitignore`: don't commit it. Sync Gradle, rebuild and reinstall the app.
3. **Project settings → Service accounts → Generate new private key**. This downloads a JSON
   file that lets the server send pushes. Treat it like a password: never commit it, never paste
   it into a chat.
4. In `ICT361_Backend`:
   ```powershell
   npm install firebase-admin
   ```
   Commit `package.json` and `package-lock.json` (Render installs from them).
5. Tell the server where the key is:
   - **Laptop:** save the file as `ICT361_Backend/firebase-service-account.json` (ignored by git)
     and add `FIREBASE_SERVICE_ACCOUNT_FILE=./firebase-service-account.json` to `.env`.
   - **Render:** Environment → add `FIREBASE_SERVICE_ACCOUNT` and paste the **whole JSON** as the
     value (it may be on one line).
6. Restart. The server log says `Push notifications are on.` (or why they are off).
7. On the phone, sign in and allow notifications when asked (Android 13+). Then from another
   phone or account make a group request: the lecturer's phone shows the alert even when the app
   is closed.

If no alert arrives: check that the phone allows notifications for the app (Settings → Apps →
MUStudentSync → Notifications), that the app was rebuilt **after** adding `google-services.json`,
and the server log for `Push failed: …`.
