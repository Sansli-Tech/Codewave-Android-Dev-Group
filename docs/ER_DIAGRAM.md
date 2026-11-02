# ER Diagram

## Server (MySQL `ict361_registration`)

Built from `ICT361_Backend/db/schema.sql`, which was exported from the running database with
`SHOW CREATE TABLE`, plus `change_requests` from migration 002.

```mermaid
erDiagram
    programmes ||--o{ students : "studies"
    lab_groups |o--o{ students : "placed in (NULL = Unassigned)"
    students ||--o| accounts : "claimed as (STUDENT role)"
    students ||--o| student_claim_codes : "has one-time code"
    students ||--o{ sync_changes : "change feed"
    students ||--o{ change_requests : "number correction"
    accounts ||--o| lecturer_profiles : "LECTURER role"
    accounts ||--o{ operation_receipts : "sent operations"

    programmes {
        int programme_id PK
        varchar programme_code UK "CS, IT, DS ..."
        varchar programme_name
    }
    lab_groups {
        int group_id PK
        varchar group_code UK "G01..G14"
        int capacity "CHECK 1..16, brief max 15"
    }
    students {
        char36 student_id PK "UUID, can be made by the app"
        char9 student_number UK "9 digits"
        varchar student_name "2..100 chars"
        int programme_id FK
        int group_id FK "nullable"
        int record_version "optimistic version"
        bool is_deleted "soft delete"
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }
    accounts {
        char36 account_id PK
        varchar username UK
        varchar password_hash "bcrypt"
        enum role "STUDENT | LECTURER"
        char36 student_id FK,UK "NULL for lecturers"
        bool is_active "0 after soft delete"
        timestamp created_at
    }
    lecturer_profiles {
        char36 account_id PK,FK
        varchar full_name
        varchar staff_id UK
        varchar school
        varchar department
        timestamp created_at
    }
    student_claim_codes {
        int claim_id PK
        char36 student_id FK,UK
        varchar claim_code_hash "hashed, never stored plain"
        timestamp expires_at "15 minutes"
        timestamp claimed_at
    }
    operation_receipts {
        char36 operation_id PK "Idempotency-Key"
        char36 account_id
        char64 request_hash "SHA-256 of method, route and body"
        json response_json "status + body replayed on retry"
        timestamp created_at
    }
    sync_changes {
        bigint change_id PK "pull cursor"
        char36 student_id
        int record_version
        enum change_type "UPSERT | DELETE"
        timestamp created_at
    }
    change_requests {
        char36 request_id PK
        char36 student_id FK
        char9 requested_number
        enum status "PENDING | APPROVED | REJECTED"
        varchar note
        timestamp created_at
        timestamp resolved_at
        char36 resolved_by "lecturer account"
    }
```

### Rules the database enforces

- `students.student_number` is unique and must be exactly 9 digits, so two students can never share a number. Soft-deleted numbers stay reserved.
- `accounts.student_id` is unique, so each student record can be claimed by one account at most.
- `operation_receipts.operation_id` is the primary key. The receipt is written in the same transaction as the change, so a replayed operation finds it and returns the stored reply.
- Group capacity is checked in the transaction that changes the group. The `lab_groups` row is locked `FOR UPDATE` first, then the active students are counted with `LOCK IN SHARE MODE`. See [ARCHITECTURE.md](ARCHITECTURE.md#group-capacity-challenge-1).
- The `lab_groups` CHECK allows up to 16 because four groups (G01, G04, G09, G10) were already at 16 when we exported. The brief's maximum is 15; see the open item in [TEST_EVIDENCE.md](TEST_EVIDENCE.md).

## Phone (Room `AppDatabase`, version 2)

```mermaid
erDiagram
    students ||--o{ pending_operations : "queued changes"
    students {
        String studentId PK
        String studentNumber
        String fullName
        String program
        String labGroup "Unassigned or G01..G14"
        int recordVersion "0 = not yet confirmed"
        boolean isActive
        String sync_state "SAVED_LOCALLY | PENDING | SYNCING | SYNCED | ACTION_REQUIRED"
        String pending_json "proposed values, kept apart from confirmed ones"
        String sync_message
    }
    pending_operations {
        String op_id PK "UUID = Idempotency-Key"
        String account_id "operations belong to one account"
        String student_id
        String type "CREATE | UPDATE | DELETE | OWN_PROFILE | GROUP_REQUEST | CORRECTION_REQUEST"
        String payload_json
        int base_version "0 = chained, filled in when sent"
        String status "PENDING | SYNCING | DONE | ACTION_REQUIRED | DISCARDED"
        int attempts
        String last_error
        String server_result "HTTP status + body, kept as evidence"
        long created_at
    }
    registration_draft {
        int id PK "single row"
        String fullName
        String studentNumber
        String program
        String claimCode
        long savedAtMillis
    }
```

`MIGRATION_1_2` adds the three sync columns to `students` (existing rows become `SYNCED`) and
creates `pending_operations` with the index `(account_id, status, created_at)`. `MigrationTest`
checks it on a real phone.
