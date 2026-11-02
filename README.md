# MUStudentSync — ICT361 Android Group Lab

An Android app (Java/XML, MVVM, Room, Retrofit, WorkManager) and a Node/Express/MySQL
backend (`ICT361_Backend/`) for registering students into lab groups G01–G14.
The app keeps working offline: every change is saved on the phone first and sent later
with an operation ID, so a retry never creates a duplicate.

| Folder | What it is |
|---|---|
| `app/` | Android app (`com.example.mustudentsync`) |
| `ICT361_Backend/` | REST API (Express 4, mysql2, JWT, helmet) |
| `ICT361_Backend/db/` | `schema.sql` (structure only, no student data) and `migrations/` |
| `ICT361_Backend/scripts/` | Challenge and test scripts (fictitious data only) |
| `docs/` | ER diagram, API contract, architecture, test evidence, logs |

All data used in testing is **fictitious**. Never commit `.env`, real student data or
passwords, and never paste them into AI tools.

---

## 1. Backend setup

Requirements: Node 18+ (the scripts use the built-in `fetch`), MySQL 8.

```powershell
cd ICT361_Backend
npm install
copy .env.example .env        # then fill in your own values
```

Create the database and tables:

```sql
CREATE DATABASE ict361_registration;
USE ict361_registration;
SOURCE db/schema.sql;
```

Insert the programmes and groups (see the comment lines at the bottom of `schema.sql`),
then create the lecturer account and start the server:

```powershell
npm run seed:lecturer
npm start                     # http://localhost:5000/api/health
```

An existing database from before the correction-request feature only needs migration 002:

```powershell
npm run db:migrate:002
```

### `.env` values (see `.env.example`)

| Key | Meaning |
|---|---|
| `PORT` | API port (5000) |
| `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME` | MySQL connection |
| `JWT_SECRET` | Long random string used to sign tokens (2-hour expiry) |
| `SEED_LECTURER_USERNAME`, `SEED_LECTURER_PASSWORD` | Lecturer created by `seed:lecturer`, also used by the test scripts |
| `LECTURER_REGISTRATION_CODE` | Faculty code needed to register another lecturer; leave empty to disable |
| `TRUST_PROXY` | `true` when running behind a hosting proxy that terminates HTTPS |
| `REQUIRE_HTTPS` | `true` to refuse plain HTTP with `426 HTTPS_REQUIRED` (the health check stays open) |

### HTTPS

On a laptop the API runs on plain HTTP and the phone reaches it through USB (`adb reverse`),
so the traffic never leaves the cable. When the API is hosted, put it behind a host that
provides HTTPS and set `TRUST_PROXY=true` and `REQUIRE_HTTPS=true`. Then change the app's
base URL in `network/RetrofitClient.java` to the `https://` address.

## 2. App setup

1. Open the repo root in Android Studio and let Gradle sync.
2. Connect the phone by USB with USB debugging on.
3. Forward the phone's port 5000 to the laptop:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:5000 tcp:5000
   ```

4. Choose the **app** run configuration and press Run.

Sign in as the lecturer from `.env`. To sign in as a student, a lecturer generates a claim code
for that student and the student claims the account in the app. For a ready-made
fictitious test student, run `npm run test:make-student`.

## 3. Running the tests

Run every backend script from `ICT361_Backend/` while `npm start` is running in another terminal.

| Command | What it proves |
|---|---|
| `npm run test:challenge2` | Lost response: the same operation ID replayed (also after a server restart) creates one student; a reused ID with a different body gets `422 OPERATION_ID_REUSED` |
| `npm run test:challenge1 -- G08` | Two requests race for the last place 20 times; exactly one wins every time and MySQL never goes over capacity. Pick a group with a free place. |
| `npm run test:make-student` | Creates and claims a fictitious student and prints the `$env:` lines for the roles test |
| `npm run test:roles` | 17 checks: what a student can and cannot reach, self-service, and the correction flow |
| `npm run db:check-capacity` | Reports groups whose active count is above 15. `-- --apply` sets capacity to 15 only where that is safe. |

App tests (Android Studio):

- `app/src/test/.../StudentRepositoryTest.java`: right-click, then **Run** (JVM unit test, no phone needed).
- `app/src/androidTest/.../MigrationTest.java`: right-click, then **Run** with the phone connected. It builds a version 1 database and opens it with `MIGRATION_1_2`.

Results so far are in [`docs/TEST_EVIDENCE.md`](docs/TEST_EVIDENCE.md).

## 4. Documents

- [ER diagram](docs/ER_DIAGRAM.md)
- [API contract](docs/API_CONTRACT.md)
- [Architecture and sync design](docs/ARCHITECTURE.md)
- [Test evidence](docs/TEST_EVIDENCE.md)
- [Evidence checklist (still to collect)](docs/EVIDENCE_CHECKLIST.md)
- [Contribution log](CONTRIBUTION_LOG.md)
- [AI and source log](docs/AI_SOURCE_LOG.md)
- [Individual reflections](docs/REFLECTIONS.md)

## 5. Git workflow for this lab

Commit locally first. Push to GitHub only after the lecturer has reviewed the history in Git GUI.

```powershell
git status                    # .env and node_modules must NOT be listed
git add -A
git commit -m "Short description of the change"
```
