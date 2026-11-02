# Test Evidence

All tests use **fictitious** students only (numbers starting 9x, created by the scripts).
Status key: ✅ passed (with evidence), ⏳ not yet run (steps in [EVIDENCE_CHECKLIST.md](EVIDENCE_CHECKLIST.md)).

Put screenshots and terminal output in `docs/evidence/` and link them in the last column.

## 1. Main test table

| # | Test | Steps | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|---|
| 1 | Rotation keeps state | Roster: type a search, scroll to page 2, rotate. Editor: type in fields, rotate. | Search text, list and typed fields stay; no second network call starts from the rotation alone | | ⏳ | |
| 2 | Process restart keeps unsynced work | Airplane mode, edit a student, Save. Swipe the app away (or `adb shell am kill`). Reopen. | Change still shows **Saved locally**; after airplane mode is off it becomes **Synced** | | ⏳ | |
| 3 | Offline create, edit, delete | Airplane mode: add a student, edit another, delete a third. Turn network on. | All three reach MySQL once each, in order; labels go to Synced | | ⏳ | |
| 4 | Lost response, same operation ID (Challenge 2) | `npm run test:challenge2` (part 1, restart server, part 2) | One student in MySQL; replay returns the stored 201; reused ID with another body returns 422 | Op `88e8dce9-fd54-4df3-8909-90a1d41200be`, student `978082030`: 201, replay 201, replay after restart 201, then 422 OPERATION_ID_REUSED. MySQL count = 1, receipt stored. | ✅ | terminal output |
| 5 | Last place race (Challenge 1) | `npm run test:challenge1 -- G08` | Every run: one 200, one 409 GROUP_FULL; MySQL never over capacity | 20/20 runs passed on G08 (assign vs assign and assign vs PUT). Test students soft-deleted afterwards. | ✅ | terminal output |
| 6 | Version conflict is not overwritten | Phone A offline edits student X. Phone B (or Postman) edits X online. Phone A goes online. | Phone A shows **Action required: changed by someone else**; MySQL keeps B's change; Discard or re-apply works | Also proved on the server by roles test check "Stale edit is refused" (409 VERSION_CONFLICT) | ⏳ (app) / ✅ (server) | |
| 7 | Roles enforced on the server | `npm run test:roles` with a claimed test student | Student gets 403 on lecturer routes; lecturer gets 403 on `/me`; bad token 401; self-service works; correction needs a lecturer | 17 passed, 0 failed | ✅ | terminal output |
| 8 | Room migration 1 → 2 | Run `MigrationTest` on a phone | Version 1 data survives; new columns and `pending_operations` exist | Passed on itel A676L (Android 15) | ✅ | Android Studio run window |

## 2. Unit tests

| Test | Where | Status |
|---|---|---|
| `StudentRepositoryTest` (fake repository, no network) | `app/src/test/...` | ⏳ run in Android Studio and screenshot |
| `MigrationTest` | `app/src/androidTest/...` | ✅ 1 passed on device |
| Sync engine harness: 28 checks (outbox order, chaining, combine create+delete, 401 pause, 5xx retry keeps the same op ID, refusal blocks later ops, discard, account scoping) | run outside Android with stubs during development | ✅ 28/28 |
| Roster harness: 5 checks (debounce, stale reply ignored, offline fallback, pending overlay, paging) | same | ✅ 5/5 |
| Idempotency mock: 10 checks (first run, replay, header, 422 on reuse, business error stored, invalid ID) | `node` with a mocked pool | ✅ 10/10 |

The harness tests were run by the AI assistant while building the code (see [AI_SOURCE_LOG.md](AI_SOURCE_LOG.md)).
They are not part of the Gradle build.

## 3. Challenges

| Challenge | Result |
|---|---|
| 1. Two phones, last place | ✅ Script proof: 20/20 on G08. Two-phone video is part of Challenge 3. |
| 2. Lost response | ✅ See row 4 |
| 3. Two-device demonstration | ⏳ Deferred: to be recorded later |

## 4. Accessibility

| Check | Expected | Status |
|---|---|---|
| Font size at the largest setting (Display size and text, about 200%) | No clipped or overlapping text on sign-in, roster, editor, profile, groups | ⏳ |
| TalkBack | Every button and field is read with a meaningful label; status labels are read as text, not only colour | ⏳ |
| Touch targets | At least 48 dp | ⏳ |

## 5. Open items found by testing

| Item | Detail | Decision needed from |
|---|---|---|
| Groups over 15 | On 2026-11-01, G01, G04, G09 and G10 each held 16 active students (capacity 16). The brief's maximum is 15. `npm run db:check-capacity` lists them. The script never moves students; `--apply` only lowers capacity where active ≤ 15. | Lecturer |
| Secrets shared in a chat | The lecturer password and `.env` values were pasted into an AI chat during development. Rotate `JWT_SECRET`, the DB password and the lecturer password before the demo. | Group |

Group occupancy on 2026-11-01:

| Group | Active / capacity | Group | Active / capacity |
|---|---|---|---|
| G01 | 16/16 | G08 | 12/15 |
| G02 | 15/15 | G09 | 16/16 |
| G03 | 14/15 | G10 | 16/16 |
| G04 | 16/16 | G11 | 13/16 |
| G05 | 15/15 | G12 | 14/15 |
| G06 | 15/15 | G13 | 15/15 |
| G07 | 15/15 | G14 | 14/15 |
