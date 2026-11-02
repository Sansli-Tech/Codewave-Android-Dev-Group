# Evidence Checklist (hands-on work only the group can do)

Tick each item, save the screenshot or clip in `docs/evidence/`, and fill in the matching row in
[TEST_EVIDENCE.md](TEST_EVIDENCE.md). Use the fictitious test student only.

Before you start:

- [ ] Backend running (`npm start`) and `http://localhost:5000/api/health` shows `OK`
- [ ] Phone connected, `adb reverse tcp:5000 tcp:5000` done, app installed from the **app** run configuration
- [ ] Turn on **Settings → Developer options → Show taps** for the video

## A. Rotation (test 1)

1. Sign in as the lecturer and open the roster.
2. Type `Race` in search, choose a programme filter, scroll down until more students load.
3. Rotate the phone to landscape, then back.
4. Open a student in the editor, change the name but do not save, then rotate.

- [ ] Search text, filter and list are unchanged after rotating
- [ ] The editor still shows the unsaved text
- [ ] Screenshot before and after: `evidence/rotation_roster.png`, `evidence/rotation_editor.png`

## B. Process restart with unsynced work (test 2)

1. Turn on airplane mode.
2. Edit a test student's name and press Save. The row shows **Saved locally**.
3. Kill the app: swipe it away from recent apps, or in a terminal:
   `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am kill com.example.mustudentsync`
4. Reopen the app. The row still shows **Saved locally**, and the Sync screen shows 1 waiting.
5. Turn airplane mode off and wait, or open the Sync screen. The row becomes **Synced**.
6. Check MySQL: `SELECT student_name, record_version FROM students WHERE student_number='<number>';`

- [ ] Screenshots of steps 2, 4 and 5, plus the MySQL result

## C. Offline create, edit and delete (test 3)

Airplane mode on: add one student, edit another, delete a third (all fictitious). Then turn the network back on.

- [ ] All three show Synced; MySQL has the new one once, the edit, and `is_deleted = 1` for the third
- [ ] `SELECT COUNT(*) FROM operation_receipts WHERE created_at > NOW() - INTERVAL 10 MINUTE;` shows 3

## D. Version conflict in the app (test 6)

1. Phone in airplane mode: edit test student X's name, Save.
2. On the laptop, change X through Postman (`PUT /api/students/:id` with the current `expected_version`), or with a second phone.
3. Turn the phone's network on.

- [ ] Row shows **Action required** with the message that someone else changed it
- [ ] MySQL still has the laptop's change
- [ ] The row's ⋮ menu → **Discard my change** returns to the server values (screenshot)

## E. Fake repository unit test

1. In Android Studio open `app/src/test/java/com/example/mustudentsync/StudentRepositoryTest.java`.
2. Right-click the class, then **Run 'StudentRepositoryTest'**. No phone is needed.

- [ ] Screenshot of the green result: `evidence/unit_tests.png`

## F. Accessibility

1. **Settings → Display → Font size** at the largest, plus **Display size** at the largest.
2. Walk through sign-in, roster, editor, student profile and groups.
3. Turn on **TalkBack** and swipe through the roster and the editor.

- [ ] No clipped text (screenshots of each screen at the largest size)
- [ ] TalkBack reads every button and field with a label; note any it misses and fix `contentDescription` / `hint`
- [ ] The sync status is read as words (Saved locally, Pending, Action required), not only shown as colour

## G. Release APK

1. Android Studio: **Build → Generate Signed App Bundle / APK → APK**.
2. Create a new keystore. Keep it **outside** the repo and never commit it or its password.
3. Build **release**. The file is in `app/release/app-release.apk`.
4. Install it on the phone: `adb install -r app/release/app-release.apk` and sign in once.

- [ ] APK installs and runs
- [ ] Rename to `MUStudentSync-<group>.apk` for submission (not committed to Git)

## H. Demo video (5–8 minutes)

Suggested script (roughly one minute each):

1. **Intro (0:30)**: group, members, what the app does. Architecture slide from `ARCHITECTURE.md`.
2. **Sign in and roles (1:00)**: lecturer roster with paging and search; student sign-in shows only their own profile. Show `npm run test:roles` output (17 passed).
3. **Offline first (1:30)**: airplane mode, create and edit students, labels show Saved locally; kill and reopen the app; network on, labels turn Synced; show the rows in MySQL.
4. **Challenge 2 (1:00)**: run `test:challenge2` part 1, restart the server, part 2. Point at "count 1" and 422.
5. **Challenge 1 (1:00)**: run `test:challenge1 -- G08` and show 20/20, and explain the group lock in one sentence.
6. **Conflict (1:00)**: Section D above on screen.
7. **Student self-service (0:45)**: edit name, group request (full group refused, old group kept), correction request approved by the lecturer.
8. **Close (0:30)**: tests table, what each member did.

- [ ] Video recorded, 5–8 minutes, no real student data or passwords on screen
- [ ] `.env` is never shown (close it in VS Code before recording)

## I. Before submitting

- [ ] Rotate `JWT_SECRET`, the MySQL password and the lecturer password (they were shared in an AI chat); update your own `.env` only
- [ ] `git status` shows no `.env`, `node_modules`, keystore or APK
- [ ] Each member has filled their row in `CONTRIBUTION_LOG.md` and their section in `docs/REFLECTIONS.md`
- [ ] `docs/AI_SOURCE_LOG.md` is complete
- [ ] Commit locally; push to GitHub **only after** the lecturer has reviewed in Git GUI
- [ ] Submission zip: repo without `node_modules`, `.gradle`, `build/`, `.env`; plus APK and video link
