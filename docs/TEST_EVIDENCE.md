# Test Evidence

Follows the brief's **Minimum demonstration checklist** (page 5). For each test: starting data,
steps, expected and actual result, and evidence. All test records are **fictitious** (student
numbers starting 9x). Real class data stays on the local server and is never shown in evidence.

Status: ✅ passed with evidence · 🟡 partly shown, the rest still to record · ⏳ not yet run.
Evidence files go in `docs/evidence/`. Fill in the **Commit** column from `git log --oneline`.

## 1. Minimum demonstration checklist

### 1.1 Valid registration and lecturer CRUD — ✅

| | |
|---|---|
| Starting data | Lecturer signed in; fictitious students `951112099` (claimed account), `961234501`, `961234502` |
| Steps | (a) `npm run test:make-student`: lecturer creates a student, generates a claim code, the student claims it. (b) Phone in airplane mode: add `Offline Create Test` (961234501), edit `951112099`'s programme, delete `Delete Me Test` (961234502). Network back on. (c) MySQL: `SELECT student_number, is_deleted, record_version FROM students WHERE student_number IN (...)`. |
| Expected | Claimed account linked to the existing profile (no second profile). The three offline changes reach MySQL once each, in order; the deleted one is soft-deleted (`is_deleted = 1`). |
| Actual | Claim worked and the test student signed in. Sync tab showed 3 waiting, then 0; MySQL had the new student once (version 1), the edit (version +1) and `is_deleted = 1` for the deleted one; 3 new operation receipts. Extra case: a student created and deleted offline before syncing was never sent (no row in MySQL). |
| Evidence | `offline_1_three_changes.png`, `offline_2_synced.png`, `offline_3_mysql.png` |

### 1.2 Duplicate number and invalid fields — 🟡

| | |
|---|---|
| Starting data | Existing student `961000001` |
| Steps | (a) Add a second student with number `961000001`. (b) Add a student named `Test 3` (contains a digit). (c) Enter an 8-digit number. Check that the typed values stay in the form. |
| Expected | Duplicate refused (on the phone and by MySQL's unique key, `409 DUPLICATE_STUDENT_NUMBER`); invalid fields refused with a clear message; typed input kept. |
| Actual | **Defect D3 found:** a name with a digit (`Cloud Test 2`) was accepted on the phone and only refused by the server after syncing ("Action required"). Fixed: the phone now checks the same name rule as the server before saving. The refused change was corrected in the editor and re-sent successfully. Re-test of (a)–(c) after the fix: ⏳ |
| Evidence | screenshot of the Action required card (taken); re-test screenshots ⏳ |

### 1.3 Full group and simultaneous requests — ✅

| | |
|---|---|
| Starting data | G08 at 12/15; the script tops it up to 14/15 with fictitious students |
| Steps | `npm run test:challenge1 -- G08`: 20 runs, each sending two requests for the last place at the same moment (assign vs assign, and assign vs PUT) |
| Expected | Each run: exactly one `200`, one `409 GROUP_FULL`; MySQL never above 15 |
| Actual | **20/20 runs passed**; MySQL showed 15/15 after every run; test students soft-deleted afterwards |
| Note | The brief says to start **G01** with 14. G01 holds 16 (see open item O1), so G08 was used. Run again on G01 once O1 is settled. |
| Evidence | terminal output |

### 1.4 Failed transfer and repeated deletion — ⏳

| | |
|---|---|
| Starting data | A full group (e.g. G02 15/15); a fictitious student in G08 |
| Steps | (a) Editor: move the student to G02 and save. (b) Delete a fictitious student, then send the same delete again (second phone, or PowerShell with a **new** Idempotency-Key). (c) Check the group count in MySQL. |
| Expected | (a) `409 GROUP_FULL`: the student stays in G08 ("Action required: group is full"). (b) Second delete returns `already_deleted: true`; the group count drops by exactly one. |
| Actual | Server rules are in place (group locked first; repeat delete returns `already_deleted`), and the Challenge 1 losers kept their old state every time. On-phone demonstration ⏳ |
| Evidence | ⏳ |

### 1.5 Offline save and interrupted sync — ✅

| | |
|---|---|
| Starting data | Test student `951112099` synced |
| Steps | (a) Airplane mode, edit the name, Save; kill the app (`adb shell am force-stop`); reopen offline; then go online. (b) `npm run test:challenge2`: save, lose the reply, retry, restart the server, retry again, then reuse the ID with a different body. |
| Expected | (a) Change survives the restart as **Saved locally**, then syncs once. (b) One record only; replays return the stored reply; reuse with other content refused. |
| Actual | (a) After reopening, still "Saved locally" and "1 waiting"; after going online it synced; MySQL showed the new name, version +1 and one new receipt. (b) Operation `88e8dce9-…`, student `978082030`: 201, replay 201, replay after restart 201, then `422 OPERATION_ID_REUSED`; MySQL count 1. |
| Evidence | `restart_1_saved_locally.png` … `restart_4_mysql.png`; Challenge 2 terminal output |

### 1.6 Conflicting edit and remote deletion — 🟡 (Challenge 3)

| | |
|---|---|
| Starting data | One fictitious student cached on two devices |
| Steps | Device A offline edits the student; device B edits it online; A reconnects. Repeat with a group that fills up while A is offline, and with the lecturer deleting the student remotely. |
| Expected | A shows **Action required** with the server's current record; B's change is kept; **Discard** or re-apply works; a stale edit never recreates a deleted student. |
| Actual | Server side proved: roles test "Stale edit is refused, not overwritten" (`409 VERSION_CONFLICT`); delete returns `410 STUDENT_DELETED` to later edits. Sync harness checks for conflict, discard and no resurrection pass. Two-device demonstration ⏳ (Challenge 3). |
| Evidence | ⏳ |

### 1.7 Roles and account switching — 🟡

| | |
|---|---|
| Starting data | Lecturer account; claimed fictitious student `951112099` |
| Steps | (a) `npm run test:roles`. (b) On the phone: lecturer saves a change offline, signs out, the student signs in; check the student sees no roster and the lecturer's queued change is not sent with the student's token. |
| Expected | Students get 403 on lecturer routes and see only their own record; queues are scoped per account; sign-out clears cached data. |
| Actual | (a) **17 passed, 0 failed.** (b) Queue scoping is checked by the sync harness (account-scoped queue, no replay under another user); on-phone demonstration ⏳ |
| Evidence | roles terminal output |

### 1.8 Search, filters and accessibility — 🟡

| | |
|---|---|
| Starting data | Full local roster (211 students) |
| Steps | Roster: scroll (paging), search, programme filter, group filter, Unassigned; then stop the server. Then: largest font and display size, and TalkBack. |
| Expected | Combined filters work; online results paged 20 at a time; offline the screen says the list is this phone's copy and may be incomplete; usable at large text and with TalkBack. |
| Actual | Paging (20 → 40 …), search, combined filters and the offline label all worked: "Online · 20 of 211 students on the server", then "Offline · showing 211 students saved on this phone…". Rotation kept search and filters. Large text and TalkBack ⏳ |
| Evidence | `roster_online.png`, `roster_offline.png` (retake showing only test students; the first ones showed real names), `rotation_roster_*.png` |

## 2. Defects found and corrected

The brief asks for a defect and its correction at each checkpoint.

| # | Found by | Defect | Correction | Re-test | Commit |
|---|---|---|---|---|---|
| D1 | Server review | Group count read an old snapshot (REPEATABLE READ), so two requests could both see 14/15 | Count with `LOCK IN SHARE MODE` after locking the group row | Challenge 1: 20/20 | |
| D2 | Server review | One route locked the student before the group (deadlock risk) | Same lock order everywhere: group, then student | Challenge 1: 20/20 | |
| D3 | Hosted test | Name with a digit accepted on the phone, refused by the server after sync | `NameRule`: the phone checks the server's rule before saving; the refusal message now gives the server's reason | ⏳ | |
| D4 | Rotation test | Editor lost unsaved text on rotation (form refilled from Room) | Fill the form only once (`formFilled` flag in the ViewModel and saved state) | ✅ passed | |
| D5 | Unit test | Fake repository used capacity 16 for every group; `groupWith15ActiveStudents_isFull` failed (5/6) | Per-group capacity, default 15, same as the real repository; new 16-place test | ✅ 7/7 | |
| D6 | Hosted test | Roster said "2 of 1 students on the server" (counted an unsynced student) | Server rows and unsynced new students counted separately | ⏳ | |
| D7 | Hosted test | Lecturer sign-in failure said "Incorrect student number or password" | "Incorrect username, student number or password" | ⏳ | |

## 3. Other tests

| Test | Result |
|---|---|
| `StudentRepositoryTest` (fake repository, JVM) | ✅ 7/7 after D5 |
| `MigrationTest` (Room 1 → 2, existing and pending data kept) | ✅ passed on itel A676L, Android 15 |
| Sync engine harness: order, chaining, combine create+delete, 401 pause, retry with the same ID, refusal blocks later ops, discard, account scoping, group request waits for the lecturer (approve when full refused, one request at a time) | ✅ 30/30 |
| Notifications backend (mocked DB): request creates no move and notifies lecturers, approve/reject/full/deleted/already-resolved, announcements to all or one group, unread counts and mark-read, device register/sign-out, push text generic (no names), dead tokens removed | ✅ 26/26 |
| Roster harness: debounce, stale reply ignored, offline fallback, pending overlay, paging | ✅ 5/5 |
| Idempotency (operation receipts) with a mocked database | ✅ 10/10 |
| Group planner: auto-assign up to 15, new groups only when all are full, locks, Excel workbook | ✅ 20/20 |
| Delete group: count check (`GROUP_CHANGED`), students become Unassigned, soft-deleted rows detached | ✅ 9/9 |
| Forgot password: e-mailed code (Brevo simulated), same reply for unknown accounts, one e-mail per minute, code once only, 5-wrong-tries lockout, lecturer code backup, faculty-code route, attempt limit, change password while signed in (wrong current, same password, lockout) | ✅ 28/28 |

The harness tests were run by the AI assistant while building the code (see [AI_SOURCE_LOG.md](AI_SOURCE_LOG.md)).
They are not part of the Gradle build.

## 4. Hosted backend (HTTPS)

Render (web service) + Aiven (MySQL with an encrypted connection). The hosted database holds **fictitious data only**.

| Check | Result |
|---|---|
| `https://<service>.onrender.com/api/health` | `{"status":"OK","database":"connected"}` |
| Plain `http://` request | Moved to HTTPS by the host; the API refuses non-HTTPS (`426`) and data routes need a token (`401 AUTHENTICATION_REQUIRED` seen) |
| Phone on mobile data, no cable | Cloud lecturer signed in; `Cloud Test One` created and synced (G08 1/15) |
| Refused change (D3) | Shown as Action required; corrected in the editor and synced |

## 5. Lecturer-requested features (Nov 2026) — ⏳ on the phone

| Feature | Check on the phone |
|---|---|
| New group | Creates the next code (G15) with 15 places |
| Auto-assign | Preview shows the plan; only unassigned students placed; groups filled up to 15 |
| Delete group | Count shown; students become Unassigned; changed count refused |
| Export to Excel | `.xlsx` with Summary and one sheet per group opens in Excel/Sheets |
| Recovery e-mail | Student saves an e-mail on the profile; it is shown again after reopening |
| Forgot password | "E-mail me a code" → code arrives → new password works; lecturer code and faculty code backups work |
| Share code | Claim/reset code dialog: Share opens WhatsApp/SMS with the ready message |
| Student tabs | Home, Groups, Notifications and Profile; Back returns to Home |
| Group request approval | Student asks for G05 → stays in old group, "waiting for your lecturer". Lecturer's 🔔 shows 1; Approve → student moves (after sync) and gets a notification; Reject with a note → student sees the note. Approve when the group is full → refused, request stays waiting. Second request while one waits → refused (REQUEST_PENDING). Withdraw works. |
| Announcements | Lecturer sends to All, then to one group: only that group's students receive it; badge on the Notifications tab; opening the tab clears the badge |
| Push (phone) | App closed: alert arrives with only "You have N unread notifications". After sign-out: "Sign in to read it". Lock screen shows only "MUStudentSync". No names or text in any alert. |
| Settings | Opens from ⚙ (student profile) or Settings (lecturer profile); change password, recovery e-mail, theme, sync status, help, about, sign out |

## 6. Open items

| # | Item | Decision needed from |
|---|---|---|
| O1 | G01, G04, G09, G10 and G11 have capacity 16 (G01, G04, G09, G10 hold 16 students). The brief's maximum is 15, and failed capacity checks must be corrected. `npm run db:check-capacity` lists them. | Lecturer |
| O2 | The brief lists programmes CS, IT, DS and groups G01–G04; the app has 23 programmes and 14+ groups **on the lecturer's instruction**. | Note in README |
| O3 | Secrets were pasted into an AI chat during development: change `JWT_SECRET`, the MySQL and Aiven passwords, the lecturer password, the faculty code and the ngrok token. | Group |
| O4 | Real class data is kept on the local server only; the hosted database has fictitious data (group decision). | Recorded |
