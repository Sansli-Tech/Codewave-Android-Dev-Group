package com.example.mustudentsync.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One offline change waiting to be sent to the server (Activity E).
 *
 * It is written in the SAME Room transaction as the proposed values on the student row,
 * so the change and its operation can never get separated by a crash.
 * opId is created once and sent as the Idempotency-Key header on every retry: if a reply is
 * lost, the server replays its stored result instead of applying the change twice.
 * accountId scopes the queue: another user signing in on this phone never replays it.
 */
@Entity(
        tableName = "pending_operations",
        indices = {@Index(value = {"account_id", "status", "created_at"})}
)
public class PendingOperationEntity {

    public static final String TYPE_CREATE = "CREATE";
    public static final String TYPE_UPDATE = "UPDATE";
    public static final String TYPE_DELETE = "DELETE";
    // Student self-service (the student's own record only)
    public static final String TYPE_OWN_PROFILE = "OWN_PROFILE";
    public static final String TYPE_GROUP_REQUEST = "GROUP_REQUEST";
    public static final String TYPE_CORRECTION_REQUEST = "CORRECTION_REQUEST";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SYNCING = "SYNCING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_ACTION_REQUIRED = "ACTION_REQUIRED";
    public static final String STATUS_DISCARDED = "DISCARDED";

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "op_id")
    public String opId = "";

    @NonNull
    @ColumnInfo(name = "account_id")
    public String accountId = "";

    @NonNull
    @ColumnInfo(name = "student_id")
    public String studentId = "";

    /** CREATE, UPDATE, DELETE, OWN_PROFILE, GROUP_REQUEST or CORRECTION_REQUEST. */
    @NonNull
    @ColumnInfo(name = "type")
    public String type = "";

    /** Body fields as JSON (student_name, student_number, programme_code, group_code); null for DELETE. */
    @ColumnInfo(name = "payload_json")
    public String payloadJson;

    /**
     * The server version this change was based on. 0 means "follows an earlier queued
     * change for the same student": the version is filled in when it is sent, from the
     * version the server confirmed for that earlier change.
     */
    @ColumnInfo(name = "base_version")
    public int baseVersion;

    @NonNull
    @ColumnInfo(name = "status")
    public String status = STATUS_PENDING;

    /** System.currentTimeMillis() when queued. Operations are sent in this order. */
    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "attempts")
    public int attempts;

    @ColumnInfo(name = "last_error")
    public String lastError;

    /** The server's reply (HTTP status + body), kept for review and as test evidence. */
    @ColumnInfo(name = "server_result")
    public String serverResult;
}
