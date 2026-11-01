package com.example.mustudentsync.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.mustudentsync.data.local.AppDatabase;
import com.example.mustudentsync.data.local.PendingOperationDao;
import com.example.mustudentsync.data.local.StudentDao;
import com.example.mustudentsync.model.PendingOperationEntity;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.model.SyncState;
import com.example.mustudentsync.sync.SyncScheduler;
import com.example.mustudentsync.util.ProgrammeCatalog;
import com.example.mustudentsync.util.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity E: every lecturer create / edit / delete is saved on the phone FIRST.
 *
 * One Room transaction writes both the proposed values (on the student row, kept apart
 * from the confirmed values) and a pending operation with a fresh operation ID. Then
 * WorkManager is asked to sync. Nothing is lost if the app is closed, the phone is
 * offline, or the reply from the server never arrives.
 */
public class OutboxRepository {

    public interface Callback {
        void onDone(boolean ok, String message);
    }

    public static final String UNASSIGNED = "Unassigned";

    private final Context appContext;
    private final AppDatabase db;
    private final StudentDao students;
    private final PendingOperationDao ops;
    private final SessionManager session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public OutboxRepository(Context context) {
        appContext = context.getApplicationContext();
        db = AppDatabase.getInstance(appContext);
        students = db.studentDao();
        ops = db.pendingOperationDao();
        session = new SessionManager(appContext);
    }

    /** Lecturer adds a student. The student exists on this phone at once (version 0 = not yet confirmed). */
    public void enqueueCreate(String name, String number, String programName, String groupName, Callback cb) {
        run(cb, account -> {
            StudentEntity same = students.findByStudentNumber(number);
            if (same != null && same.isActive) {
                return "That student number is already on this phone.";
            }
            String id = UUID.randomUUID().toString();
            JsonObject payload = payload(name, number, programName, groupName);

            db.runInTransaction(() -> {
                StudentEntity s = new StudentEntity(id, number, name, programName, groupName);
                s.recordVersion = 0;
                s.syncState = SyncState.SAVED_LOCALLY;
                s.pendingJson = payload.toString();
                students.insert(s);
                ops.insert(newOp(account, id, PendingOperationEntity.TYPE_CREATE, payload, 0));
            });
            return null;
        });
    }

    /** Lecturer edits a student. The confirmed values stay as they are until the server accepts. */
    public void enqueueUpdate(String studentId, String name, String number, String programName,
                              String groupName, Callback cb) {
        run(cb, account -> {
            StudentEntity s = students.findByIdNow(studentId);
            if (s == null) return "This student is no longer on this phone. Sync and try again.";
            JsonObject payload = payload(name, number, programName, groupName);

            db.runInTransaction(() -> {
                discardActionRequired(account, studentId);   // re-applying after a conflict / full group
                List<PendingOperationEntity> open = ops.openFor(account, studentId);

                // Created offline and not sent yet: fold the edit into the queued CREATE.
                PendingOperationEntity unsentCreate = unsentCreate(open);
                if (unsentCreate != null && open.size() == 1) {
                    unsentCreate.payloadJson = payload.toString();
                    ops.update(unsentCreate);
                    s.fullName = name;
                    s.studentNumber = number;
                    s.program = programName;
                    s.labGroup = groupName;
                } else {
                    int waiting = ops.countToSendFor(account, studentId);
                    // Chained after another queued change: its version is filled in when sent.
                    int base = waiting > 0 || s.recordVersion == 0 ? 0 : s.recordVersion;
                    ops.insert(newOp(account, studentId, PendingOperationEntity.TYPE_UPDATE, payload, base));
                }
                s.pendingJson = payload.toString();
                s.syncState = SyncState.SAVED_LOCALLY;
                s.syncMessage = null;
                students.update(s);
            });
            return null;
        });
    }

    /** Lecturer deletes a student. Shown as "pending delete" until the server confirms. */
    public void enqueueDelete(String studentId, Callback cb) {
        run(cb, account -> {
            StudentEntity s = students.findByIdNow(studentId);
            if (s == null) return null;   // already gone

            db.runInTransaction(() -> {
                discardActionRequired(account, studentId);
                List<PendingOperationEntity> open = ops.openFor(account, studentId);

                // Created AND deleted offline, never sent: drop both, nothing goes to the server.
                if (s.recordVersion == 0 && neverSent(open)) {
                    for (PendingOperationEntity op : open) {
                        op.status = PendingOperationEntity.STATUS_DISCARDED;
                        op.lastError = "Combined: created and deleted before syncing";
                        ops.update(op);
                    }
                    students.deleteById(studentId);
                    return;
                }
                int waiting = ops.countToSendFor(account, studentId);
                int base = waiting > 0 || s.recordVersion == 0 ? 0 : s.recordVersion;
                ops.insert(newOp(account, studentId, PendingOperationEntity.TYPE_DELETE, null, base));

                JsonObject marker = new JsonObject();
                marker.addProperty("delete", true);
                s.pendingJson = marker.toString();
                s.syncState = SyncState.SAVED_LOCALLY;
                s.syncMessage = null;
                students.update(s);
            });
            return null;
        });
    }

    /**
     * "Discard my change": drops this student's unsent or refused changes and goes back to
     * the confirmed server values. A student created offline is removed from the phone.
     */
    public void discardChanges(String studentId, Callback cb) {
        run(cb, account -> {
            StudentEntity s = students.findByIdNow(studentId);
            List<PendingOperationEntity> open = ops.openFor(account, studentId);
            for (PendingOperationEntity op : open) {
                if (PendingOperationEntity.STATUS_SYNCING.equals(op.status)) {
                    return "This change is being sent right now. Try again in a moment.";
                }
            }
            db.runInTransaction(() -> {
                for (PendingOperationEntity op : open) {
                    op.status = PendingOperationEntity.STATUS_DISCARDED;
                    ops.update(op);
                }
                if (s == null) return;
                if (s.recordVersion == 0) {
                    students.deleteById(studentId);
                } else {
                    s.pendingJson = null;
                    s.syncMessage = null;
                    s.syncState = SyncState.SYNCED;
                    students.update(s);
                }
            });
            return null;
        });
    }

    // ------------------------------------------------------------ student self-service

    /** Student edits their own name and/or programme. Pending until the server confirms. */
    public void enqueueOwnProfile(String studentId, String name, String programName, Callback cb) {
        JsonObject payload = new JsonObject();
        if (name != null) payload.addProperty("student_name", name);
        if (programName != null) payload.addProperty("programme_code", ProgrammeCatalog.codeFor(programName));
        enqueueOwn(studentId, PendingOperationEntity.TYPE_OWN_PROFILE, payload, true, cb);
    }

    /**
     * Student asks for a place in a group. Saving it locally does NOT reserve a place:
     * it stays Pending, and only the server decides (GROUP_FULL keeps the old group).
     */
    public void enqueueGroupRequest(String studentId, String groupCode, Callback cb) {
        JsonObject payload = new JsonObject();
        payload.addProperty("group_code", groupCode);
        enqueueOwn(studentId, PendingOperationEntity.TYPE_GROUP_REQUEST, payload, true, cb);
    }

    /** Student asks the lecturer to correct their student number. Only a lecturer can approve it. */
    public void enqueueCorrectionRequest(String studentId, String requestedNumber, Callback cb) {
        JsonObject payload = new JsonObject();
        payload.addProperty("requested_student_number", requestedNumber);
        enqueueOwn(studentId, PendingOperationEntity.TYPE_CORRECTION_REQUEST, payload, false, cb);
    }

    private void enqueueOwn(String studentId, String type, JsonObject payload, boolean versioned, Callback cb) {
        run(cb, account -> {
            StudentEntity s = students.findByIdNow(studentId);
            if (s == null) return "Your profile isn't on this phone yet. Sync and try again.";
            db.runInTransaction(() -> {
                discardActionRequired(account, studentId);
                int waiting = ops.countToSendFor(account, studentId);
                int base = !versioned || waiting > 0 ? 0 : s.recordVersion;
                ops.insert(newOp(account, studentId, type, payload, base));

                // Proposals from several requests are merged, so the profile can show all of them.
                JsonObject proposed = new JsonObject();
                if (s.pendingJson != null) {
                    try {
                        proposed = JsonParser.parseString(s.pendingJson).getAsJsonObject();
                    } catch (Exception ignored) {
                        proposed = new JsonObject();
                    }
                }
                for (String key : payload.keySet()) proposed.add(key, payload.get(key));
                s.pendingJson = proposed.toString();
                s.syncState = SyncState.SAVED_LOCALLY;
                s.syncMessage = null;
                students.update(s);
            });
            return null;
        });
    }

    // ---------------------------------------------------------------------------------

    private interface Work {
        /** Returns an error message, or null on success. Runs off the main thread. */
        String apply(String accountKey);
    }

    private void run(Callback cb, Work work) {
        executor.execute(() -> {
            String account = session.getAccountKey();
            String error;
            if (account == null) {
                error = "Session expired. Sign in again.";
            } else {
                try {
                    error = work.apply(account);
                } catch (Exception e) {
                    error = "Couldn't save on this phone.";
                }
            }
            if (error == null) SyncScheduler.requestSync(appContext);
            final String message = error == null
                    ? "Saved on this phone. It will sync when you're online."
                    : error;
            final boolean ok = error == null;
            main.post(() -> { if (cb != null) cb.onDone(ok, message); });
        });
    }

    private void discardActionRequired(String account, String studentId) {
        for (PendingOperationEntity op : ops.openFor(account, studentId)) {
            if (PendingOperationEntity.STATUS_ACTION_REQUIRED.equals(op.status)) {
                op.status = PendingOperationEntity.STATUS_DISCARDED;
                ops.update(op);
            }
        }
    }

    private static PendingOperationEntity unsentCreate(List<PendingOperationEntity> open) {
        for (PendingOperationEntity op : open) {
            if (PendingOperationEntity.TYPE_CREATE.equals(op.type)
                    && PendingOperationEntity.STATUS_PENDING.equals(op.status)
                    && op.attempts == 0) {
                return op;
            }
        }
        return null;
    }

    /** True if none of these operations was ever handed to the network. */
    private static boolean neverSent(List<PendingOperationEntity> open) {
        for (PendingOperationEntity op : open) {
            if (op.attempts > 0 || !PendingOperationEntity.STATUS_PENDING.equals(op.status)) return false;
        }
        return true;
    }

    private static PendingOperationEntity newOp(String account, String studentId, String type,
                                                JsonObject payload, int baseVersion) {
        PendingOperationEntity op = new PendingOperationEntity();
        op.opId = UUID.randomUUID().toString();
        op.accountId = account;
        op.studentId = studentId;
        op.type = type;
        op.payloadJson = payload != null ? payload.toString() : null;
        op.baseVersion = baseVersion;
        op.status = PendingOperationEntity.STATUS_PENDING;
        op.createdAt = System.currentTimeMillis();
        return op;
    }

    /** Request body fields, using the server's programme CODE and null for Unassigned. */
    static JsonObject payload(String name, String number, String programName, String groupName) {
        JsonObject o = new JsonObject();
        o.addProperty("student_name", name);
        o.addProperty("student_number", number);
        o.addProperty("programme_code", ProgrammeCatalog.codeFor(programName));
        if (groupName == null || UNASSIGNED.equals(groupName)) {
            o.add("group_code", com.google.gson.JsonNull.INSTANCE);
        } else {
            o.addProperty("group_code", groupName);
        }
        return o;
    }
}
