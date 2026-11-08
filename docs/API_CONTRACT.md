# API Contract

Base URL: `http://localhost:5000/api` (USB phone uses `adb reverse tcp:5000 tcp:5000`).
When hosted: `https://<host>/api` with `REQUIRE_HTTPS=true`.

All bodies are JSON and limited to 20 KB. Errors look like `{ "error": "CODE", "message": "..." }`.

## Common rules

| Rule | Detail |
|---|---|
| Authentication | `Authorization: Bearer <token>` on every route except `login`, `claim`, `register-lecturer`, `health`. Tokens last 2 hours. |
| Missing or bad token | `401 AUTHENTICATION_REQUIRED` / `401 INVALID_OR_EXPIRED_TOKEN`. A disabled account gets `401 ACCOUNT_INACTIVE`; a token whose role no longer matches the account gets `401 SESSION_INVALID`. |
| Wrong role | `403 FORBIDDEN`. Roles are checked on the server for every route; the app hiding a button is not the protection. |
| Plain HTTP when `REQUIRE_HTTPS=true` | `426 HTTPS_REQUIRED` |
| Optimistic versioning | Every change to an existing student sends `expected_version`. If the record moved on, the reply is `409 VERSION_CONFLICT` with the server's `current` values; nothing is overwritten. |

### Operation IDs (`Idempotency-Key`)

Every route marked **Op** below accepts an operation ID, a UUID made by the phone when the change
is saved locally:

```
Idempotency-Key: 88e8dce9-fd54-4df3-8909-90a1d41200be
```

(`operation_id` in the body works too.) The phone sends the **same** ID on every retry.

| Situation | Server reply |
|---|---|
| First time this ID is seen | Runs the change. In the **same transaction** it stores a receipt: operation ID, account, SHA-256 of method + route + body, and the reply. |
| Same ID, same account, same body | The stored reply is returned unchanged, with header `Idempotent-Replay: true`. Nothing runs twice. This also works after a server restart. |
| Same ID, different body or account | `422 OPERATION_ID_REUSED` |
| ID that is not a UUID | `400 INVALID_OPERATION_ID` |
| No ID sent | Runs normally in a transaction (older clients keep working). |

Business refusals (`409 GROUP_FULL`, `409 VERSION_CONFLICT`, ...) are stored as receipts too, so a
retry gets the same refusal instead of a different result.

---

## Health

| Method | Route | Role | Reply |
|---|---|---|---|
| GET | `/health` | none | `200 { status: "OK", database: "connected" }`, or `503` if MySQL is unreachable |

## Auth — `/api/auth`

| Method | Route | Role | Body | Success | Errors |
|---|---|---|---|---|---|
| POST | `/login` | none | `{ username, password }` | `200 { token, expiresIn: 7200, user: { accountId, username, role, studentId } }` | 400 VALIDATION_ERROR, 401 INVALID_CREDENTIALS |
| GET | `/me` | any | — | `200 { user }` | 401 |
| POST | `/claim` | none | `{ student_number, claim_code, username, password }` | `201 { user }` | 400 VALIDATION_ERROR, 404 STUDENT_NOT_FOUND, 400 CLAIM_UNAVAILABLE, 400 CLAIM_EXPIRED, 400 INVALID_CLAIM_CODE, 409 ACCOUNT_EXISTS |
| POST | `/register-lecturer` | none | `{ full_name, staff_id, email, school, department, password (12+), faculty_token }` | `201` | 400 VALIDATION_ERROR, 403 INVALID_FACULTY_TOKEN, 409 ACCOUNT_EXISTS, 429 TOO_MANY_ATTEMPTS (per IP, 15-minute window), 503 LECTURER_REGISTRATION_DISABLED |
| POST | `/forgot-password` | none | `{ login }` (student number, or staff e-mail / username) | `200 { message }`, the **same reply whether or not the account exists**. E-mails a 6-digit code to the recovery e-mail (lecturers: their staff e-mail). At most one e-mail per account per minute. | 400 VALIDATION_ERROR, 429 TOO_MANY_ATTEMPTS, 503 EMAIL_NOT_CONFIGURED |
| POST | `/reset-with-code` | none | `{ login, code, new_password (12+) }`. Code: 6 digits from the e-mail, or 16 characters from the lecturer. | `200 { message, username }` | 400 VALIDATION_ERROR, 400 INVALID_RESET_CODE (wrong, used, expired, or 5 wrong tries), 429 TOO_MANY_ATTEMPTS |
| POST | `/reset-password` | none | `{ student_number, reset_code, new_password }` (older app versions) | as `reset-with-code` | as `reset-with-code` |
| POST | `/lecturer-reset-password` | none | `{ email, staff_id, faculty_token, new_password (12+) }` | `200 { message }` | 400 VALIDATION_ERROR, 400 INVALID_RESET_DETAILS, 429 TOO_MANY_ATTEMPTS, 503 RESET_DISABLED (no faculty code set) |

**Forgot password.** Codes are e-mailed through Brevo's HTTPS API (Render's free plan blocks SMTP).
A student adds a **recovery e-mail** on their profile (`/api/account/email`); a lecturer's code goes
to their staff e-mail. Backups: a one-time code from the lecturer (students), or staff ID + faculty
code (lecturers). Every code is hashed, works once, expires after 15 minutes and stops after 5 wrong
tries. Public routes allow 10 requests per IP per 15 minutes. The seeded lecturer (no staff profile,
username not an e-mail) resets with `node scripts/check_login.js --reset` on the server.

## Account — `/api/account` (any signed-in role)

| Method | Route | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/email` | — | `200 { email, codes_sent_to }` (own account only) | 401 |
| PUT | `/email` | `{ email }` (empty removes it) | `200 { message, email }` | 400 VALIDATION_ERROR, 401 |

## Students — `/api/students` (LECTURER only)

| Method | Route | Op | Body / query | Success | Errors |
|---|---|---|---|---|---|
| GET | `/` | | `?search=&programme=CS&group=G01\|UNASSIGNED&page=1&limit=20` (limit 1–100) | `200 { data: [student], pagination: { page, limit, total } }` | 400 INVALID_QUERY, 400 INVALID_GROUP |
| GET | `/:id` | | — | `200 { data: student }` | 404 STUDENT_NOT_FOUND |
| POST | `/` | ✔ | `{ student_id? (UUID from the phone), student_name, student_number (9 digits), programme_code, group_code \| null }` | `201 { student_id }` | 400 VALIDATION_ERROR, 400 INVALID_PROGRAMME, 400 INVALID_GROUP, 409 DUPLICATE_STUDENT_NUMBER, 409 DUPLICATE_STUDENT_ID, 409 GROUP_FULL |
| PUT | `/:id` | ✔ | `{ student_name, student_number, programme_code, group_code \| null, expected_version }` | `200 { student_id, record_version }` | 400 EXPECTED_VERSION_REQUIRED, 404 STUDENT_NOT_FOUND, 409 VERSION_CONFLICT (+ `current`), 409 GROUP_FULL, 409 DUPLICATE_STUDENT_NUMBER, 410 STUDENT_DELETED |
| DELETE | `/:id` | ✔ | `{ expected_version }` | `200 { student_id, record_version, already_deleted }` | 404 STUDENT_NOT_FOUND, 409 VERSION_CONFLICT |
| POST | `/:id/claim-code` | | — | `201 { student_id, claim_code, expires_in_minutes: 15 }` (one-time; only the hash is stored) | 404 STUDENT_NOT_FOUND, 409 STUDENT_ALREADY_CLAIMED |
| POST | `/:id/reset-code` | | — | `201 { reset_code, username, expires_in_minutes: 15 }` (one-time; only the hash is stored) | 404 NO_ACCOUNT (not claimed yet: give a claim code instead), 409 ACCOUNT_INACTIVE |

A `student` object: `{ student_id, student_number, student_name, programme_code, programme_name, group_code, record_version }`.

Delete is a **soft delete**: the row stays as a marker for sync, the place in the group is freed
once, the student's account is disabled (`is_active = 0`) and the number stays reserved. A repeated
delete returns `200` with `already_deleted: true`.

## Groups — `/api/groups`

| Method | Route | Role | Op | Body | Success | Errors |
|---|---|---|---|---|---|---|
| GET | `/` | any | | — | `200 { groups: [{ group_code, capacity, active_students, available_places }] }` | |
| POST | `/:groupCode/assign` | LECTURER | ✔ | `{ student_id }` | `200 { student_id, group_code, active_students, capacity, record_version }` | 400, 404 GROUP_NOT_FOUND, 404 ACTIVE_STUDENT_NOT_FOUND, 409 GROUP_FULL |
| POST | `/` | LECTURER | ✔ | `{}` | `201 { group_code, capacity: 15, groups }`. Creates the next code (G15, G16, …). | |
| DELETE | `/:groupCode` | LECTURER | ✔ | `{ confirm_students }` (the count the lecturer was shown) | `200 { group_code, unassigned_students, groups }`. The group's students become **Unassigned**; no student is deleted. | 404 GROUP_NOT_FOUND, 409 GROUP_CHANGED (the count changed since, nothing done), 400 VALIDATION_ERROR |
| GET | `/auto-assign/preview` | LECTURER | | — | `200 { unassigned, placements: [{ group_code, new_group, before, added, after, capacity }], new_groups, fill_limit: 15 }`. Changes nothing. | |
| POST | `/auto-assign` | LECTURER | ✔ | `{}` | `200 { assigned, placements, new_groups, groups }` | |
| GET | `/export.xlsx` | LECTURER | | — | `200` Excel file: a Summary sheet, one sheet per group (No., student number, name, programme) and an Unassigned sheet if needed | 500 EXPORT_FAILED |

**Auto-assign rules.** Only students with **no group** are placed; nobody already in a group is
moved. Groups are filled in code order up to **15** (groups that allow 16 keep their 16th place for
manual assignment). When every group is full, new groups of 15 are created. Students are taken in
name order. Everything happens in **one transaction**: all group rows are locked first (the same
group-then-student order as every other route), so a single assignment at the same moment cannot
push a group over capacity. With an `Idempotency-Key`, a retry replays the result and nobody is placed twice.

The export is generated by the server with a small built-in writer (`src/xlsx.js`), so no extra npm package is needed.

## Student self-service — `/api/me` (STUDENT only)

The student is always taken from the token (`req.user.studentId`), never from the URL or body,
so a student can only ever see or change their own record.

| Method | Route | Op | Body | Success | Errors |
|---|---|---|---|---|---|
| GET | `/student` | | — | `200 { student, group: { group_code, capacity, active_students } \| null, correction_request }` | 404, 410 STUDENT_DELETED |
| PUT | `/student` | ✔ | `{ student_name?, programme_code?, expected_version }` | `200` profile | 403 FIELD_NOT_PERMITTED (number or group), 400 INVALID_PROGRAMME, 409 VERSION_CONFLICT |
| POST | `/group-request` | ✔ | `{ group_code, expected_version }` | `200` profile with the new group | 400 INVALID_GROUP, 409 GROUP_FULL (old group kept), 409 VERSION_CONFLICT |
| POST | `/correction-requests` | ✔ | `{ requested_student_number }` | `201 { request_id, status: "PENDING", requested_number }` | 400 SAME_NUMBER, 409 REQUEST_PENDING |
| GET | `/correction-requests` | | — | `200 { data: [{ request_id, requested_number, status, note, created_at, resolved_at }] }` (own requests only) | |

## Correction requests — `/api/correction-requests` (LECTURER only)

| Method | Route | Op | Body | Success | Errors |
|---|---|---|---|---|---|
| GET | `/` | | — | `200 { data: [pending requests] }` | |
| POST | `/:id/resolve` | ✔ | `{ decision: "APPROVE" \| "REJECT", note? }` | `200 { request_id, status, student_id, record_version }` | 400 VALIDATION_ERROR, 404 REQUEST_NOT_FOUND, 409 ALREADY_RESOLVED, 409 NUMBER_TAKEN (request stays pending), 410 STUDENT_DELETED |

## Sync pull — `/api/sync`

| Method | Route | Role | Query | Success | Errors |
|---|---|---|---|---|---|
| GET | `/changes` | any | `?since=<change_id>&limit=100` (max 200) | `200 { changes: [{ changeId, changeType, student }], sync: { since, nextSince, limit, hasMore } }` | 400 INVALID_SYNC_CURSOR, 403 STUDENT_ID_MISSING |

A lecturer receives every change; a student receives only changes to their own record. The phone
keeps `nextSince` and asks again while `hasMore` is true.

## Status codes used

| Code | Meaning in this API |
|---|---|
| 200 / 201 | Done (or a replay of a done operation) |
| 400 | Bad input: validation, unknown programme or group, bad operation ID |
| 401 | Not signed in, or the token expired: the phone pauses its queue and asks to sign in again |
| 403 | Signed in, but this role may not do this |
| 404 | Record not found |
| 409 | Refused by a rule: VERSION_CONFLICT, GROUP_FULL, duplicate number. The phone marks the change **Action required**. |
| 410 | The student was deleted |
| 422 | Operation ID reused with a different request |
| 426 | HTTPS required |
| 429 | Too many attempts |
| 5xx | Server problem: the phone keeps the change and retries later with the same operation ID |
