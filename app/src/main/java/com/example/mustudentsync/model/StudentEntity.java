package com.example.mustudentsync.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Durable local record of a student, mirrored from / to the server.
 * studentId is an immutable internal identifier — separate from studentNumber,
 * which a lecturer is allowed to correct without breaking ownership/history.
 */
@Entity(tableName = "students")
public class StudentEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "student_id")
    public String studentId; // immutable, generated once (e.g. UUID), never reused

    @ColumnInfo(name = "student_number")
    public String studentNumber; // 9-digit string, unique, editable only by lecturer

    @ColumnInfo(name = "full_name")
    public String fullName;

    @ColumnInfo(name = "program")
    public String program; // full program name from the grouped dropdown

    @ColumnInfo(name = "lab_group")
    public String labGroup; // G01–G04 or "Unassigned"

    @ColumnInfo(name = "is_active")
    public boolean isActive = true; // false once soft-deleted

    @ColumnInfo(name = "record_version")
    public int recordVersion = 1; // last version CONFIRMED by the server; 0 = created offline, never confirmed

    // ---- Activity E: offline changes (added in Room schema version 2) ----
    // The name/number/program/group fields above always hold the CONFIRMED values.
    // A change made offline is kept separately in pendingJson (and in pending_operations)
    // until the server accepts it, so confirmed data and proposals are never mixed.

    /** SAVED_LOCALLY, PENDING, SYNCING, SYNCED or ACTION_REQUIRED. See SyncState. */
    @NonNull
    @ColumnInfo(name = "sync_state")
    public String syncState = SyncState.SYNCED;

    /** Proposed values not yet accepted by the server (JSON), or null. */
    @ColumnInfo(name = "pending_json")
    public String pendingJson;

    /** Why the last change needs attention (conflict, group full, deleted...), or null. */
    @ColumnInfo(name = "sync_message")
    public String syncMessage;

    public StudentEntity(@NonNull String studentId, String studentNumber, String fullName,
                         String program, String labGroup) {
        this.studentId = studentId;
        this.studentNumber = studentNumber;
        this.fullName = fullName;
        this.program = program;
        this.labGroup = labGroup;
    }
}