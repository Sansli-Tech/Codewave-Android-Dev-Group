# AI and Source Log

The brief requires that AI help is declared, that no real student data or credentials are given
to AI tools, and that fictitious data is used. This log records what was used, for what, and how
the group checked it. Each member adds their own entries.

## AI tools

| Date(s) | Member | Tool | What we asked for | What we used | How we checked it |
|---|---|---|---|---|---|
| Oct–Nov 2026 | | Claude (Anthropic) | Explanations of the brief, MVVM, Part C and the API design | Notes for group discussion | Discussed in the group against the brief |
| Oct–Nov 2026 | | Claude | Review of our app and backend zips against the brief | List of gaps (operation IDs, capacity locking, soft-delete account) | Each gap checked in our own code |
| Oct–Nov 2026 | | Claude | Backend: `idempotency.js` (operation receipts), capacity locking with group lock first and `LOCK IN SHARE MODE`, soft delete disabling the account, `/api/me` routes, correction requests, HTTPS middleware | Code merged into `ICT361_Backend` | `test:challenge2` passed, `test:challenge1` 20/20, `test:roles` 17/17 on our machine |
| Oct–Nov 2026 | | Claude | App: Room migration 1→2, `OutboxRepository`, `SyncManager`, `SyncWorker`/`SyncScheduler`, status labels, discard, student profile screen, online paged roster, Sharesheet summary | Code merged into `app/` | Built in Android Studio; `MigrationTest` passed on our phone; manual checks in `EVIDENCE_CHECKLIST.md` |
| Oct–Nov 2026 | | Claude | Test scripts: Challenge 1, Challenge 2, roles test, make test student, check capacity | `ICT361_Backend/scripts/` | Run against our own server with fictitious students |
| Nov 2026 | | Claude | Draft README, ER diagram, API contract, architecture, test evidence, checklists | `README.md`, `docs/` | Checked against our code and test output; results we have not run are marked ⏳ |

### Problems the AI caused or found, and what we did

| Problem | What happened | Fix |
|---|---|---|
| Missing `db/schema.sql` | A file-copy command suggested by the AI excluded `schema.sql` by mistake | The file was restored and re-checked |
| Capacity race | A plain `SELECT COUNT` under REPEATABLE READ could let a 16th student into a 15-place group | Changed to a locking read (`LOCK IN SHARE MODE`), proved with Challenge 1 |
| Deadlock risk | One route locked the student before the group | Lock order made the same everywhere (group first) |
| Test names rejected | Challenge 1 test names contained digits, which our name rule rejects | Script changed to letters only |

### Data and credentials shared with the AI

Be honest here; the marker will check it against the brief.

- Only fictitious test students (numbers starting 9x, created by our scripts) were used in tests.
- [ ] **Shared by mistake:** the lecturer account password and the contents of `.env` (JWT secret, DB password) were pasted into the chat during troubleshooting.
  **Action:** rotated `JWT_SECRET`, the MySQL password and the lecturer password on ____ (date). Initials: ____
- [ ] A group list export (`.xlsx`) was shared to show the added groups. Note here whether it contained any real names or student numbers: ____

## Other sources

| Source | Used for |
|---|---|
| Android Developers: Room, migrations and `MigrationTestHelper` | Migration 1→2 and its test |
| Android Developers: WorkManager (unique work, constraints, backoff) | `SyncScheduler` |
| Android Developers: Sharesheet (`Intent.createChooser`) | Share group summary |
| MySQL 8 Reference: InnoDB locking reads (`FOR UPDATE`, `LOCK IN SHARE MODE`) | Capacity check |
| IETF draft "The Idempotency-Key HTTP Header Field" | Operation ID header |
| Express, helmet, mysql2, jsonwebtoken, bcryptjs documentation | Backend |
| ICT361 lecture notes and lab brief | Requirements |
