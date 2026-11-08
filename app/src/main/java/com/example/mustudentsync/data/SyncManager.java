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
import com.example.mustudentsync.network.ApiService;
import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.network.dto.GroupListResponse;
import com.example.mustudentsync.network.dto.SyncResponse;
import com.example.mustudentsync.util.GroupCatalog;
import com.example.mustudentsync.util.ProgrammeCatalog;
import com.example.mustudentsync.util.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Sync = PUSH the queued offline changes (in the order they were made), then PULL the
 * server's changes and deletion markers. Used by the manual "Sync now" buttons and by
 * the WorkManager SyncWorker; a lock makes sure only one sync runs at a time.
 */
public class SyncManager {

    public interface Callback {
        void onDone(boolean success, String message);
    }

    /** What a sync run ended with. SyncWorker turns this into a WorkManager Result. */
    public enum Outcome { OK, RETRY_LATER, SIGN_IN_REQUIRED }

    public static final class Report {
        public final Outcome outcome;
        public final String message;

        Report(Outcome outcome, String message) {
            this.outcome = outcome;
            this.message = message;
        }
    }

    private static final Object LOCK = new Object();

    private final AppDatabase db;
    private final StudentDao dao;
    private final PendingOperationDao ops;
    private final SessionManager session;
    private final Context appContext;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public SyncManager(Context context) {
        appContext = context.getApplicationContext();
        db = AppDatabase.getInstance(appContext);
        dao = db.studentDao();
        ops = db.pendingOperationDao();
        session = new SessionManager(appContext);
    }

    /** Manual sync from a button: runs in the background, answers on the main thread. */
    public void syncNow(Callback cb) {
        executor.execute(() -> {
            Report r = runBlocking();
            main.post(() -> {
                if (cb != null) cb.onDone(r.outcome == Outcome.OK, r.message);
            });
        });
    }

    /** Push then pull. Blocks: call from a background thread (SyncWorker or the executor). */
    public Report runBlocking() {
        synchronized (LOCK) {
            String bearer = session.getBearerToken();
            String account = session.getAccountKey();
            if (bearer == null || account == null) {
                return new Report(Outcome.SIGN_IN_REQUIRED, "Session expired. Sign in again.");
            }
            int needAttention;
            try {
                needAttention = push(bearer, account);
            } catch (AuthException e) {
                return new Report(Outcome.SIGN_IN_REQUIRED, "Session expired. Sign in again. Your changes are kept.");
            } catch (RetryException e) {
                return new Report(Outcome.RETRY_LATER, "Can't reach the server. Changes are saved and will sync later.");
            }

            Report pulled = pull(bearer);
            if (pulled.outcome != Outcome.OK) return pulled;
            refreshGroups(bearer);

            String message = needAttention == 0
                    ? "Synced"
                    : "Synced. " + needAttention + " change(s) need your attention.";
            return new Report(Outcome.OK, message);
        }
    }

    // ------------------------------------------------------------------------- PUSH

    private static class AuthException extends Exception { }

    private static class RetryException extends Exception { }

    /**
     * Sends this account's queued operations oldest first. Returns how many ended as
     * ACTION_REQUIRED. Stops at the first network failure so the order is preserved;
     * the same operation (same ID) is sent again next time.
     */
    private int push(String bearer, String account) throws AuthException, RetryException {
        ApiService api = RetrofitClient.getApiService();
        Set<String> blocked = new HashSet<>();   // a refused change holds back later changes to that student
        int needAttention = 0;

        for (PendingOperationEntity op : ops.toSend(account)) {
            if (blocked.contains(op.studentId)) continue;

            StudentEntity local = dao.findByIdNow(op.studentId);
            int version = op.baseVersion > 0 ? op.baseVersion : (local != null ? local.recordVersion : 0);
            boolean needsVersion = !PendingOperationEntity.TYPE_CREATE.equals(op.type)
                    && !PendingOperationEntity.TYPE_CORRECTION_REQUEST.equals(op.type);
            if (needsVersion && version < 1) {
                needAttention++;
                blocked.add(op.studentId);
                refuse(op, "This change can't be matched to a saved version. Sync, then edit again.");
                continue;
            }

            // Mark as sending BEFORE the request: from here on it may reach the server,
            // so it is never silently dropped or merged, only retried with the same ID.
            op.status = PendingOperationEntity.STATUS_SYNCING;
            op.attempts++;
            ops.update(op);
            setState(op.studentId, SyncState.SYNCING);

            Response<ResponseBody> res;
            try {
                res = call(api, bearer, op, version).execute();
            } catch (IOException e) {
                backToPending(op, "Network: " + e.getClass().getSimpleName());
                throw new RetryException();
            }

            int code = res.code();
            String body = bodyText(res);
            op.serverResult = code + " " + (body != null ? body : "");

            if (res.isSuccessful()) {
                accepted(op, body);
            } else if (code == 401) {
                backToPending(op, "Session expired");
                throw new AuthException();
            } else if (code >= 500 || code == 408 || code == 429) {
                backToPending(op, "Server " + code);
                throw new RetryException();
            } else if (code == 404 && PendingOperationEntity.TYPE_DELETE.equals(op.type)) {
                accepted(op, body);   // already gone on the server: the delete is done
            } else {
                needAttention++;
                blocked.add(op.studentId);
                refuse(op, messageFor(code, errorCode(body), serverMessage(body), op));
            }
        }
        return needAttention;
    }

    private Call<ResponseBody> call(ApiService api, String bearer, PendingOperationEntity op, int version) {
        JsonObject body = op.payloadJson != null
                ? JsonParser.parseString(op.payloadJson).getAsJsonObject()
                : new JsonObject();
        switch (op.type) {
            case PendingOperationEntity.TYPE_CREATE:
                body.addProperty("student_id", op.studentId);   // the phone's ID becomes the server's ID
                return api.createStudentOp(bearer, op.opId, body);
            case PendingOperationEntity.TYPE_UPDATE:
                body.addProperty("expected_version", version);
                return api.updateStudentOp(bearer, op.opId, op.studentId, body);
            case PendingOperationEntity.TYPE_OWN_PROFILE:
                body.addProperty("expected_version", version);
                return api.updateMyProfileOp(bearer, op.opId, body);
            case PendingOperationEntity.TYPE_GROUP_REQUEST:
                body.addProperty("expected_version", version);
                return api.groupRequestOp(bearer, op.opId, body);
            case PendingOperationEntity.TYPE_CORRECTION_REQUEST:
                return api.correctionRequestOp(bearer, op.opId, body);
            default:
                body.addProperty("expected_version", version);
                return api.deleteStudentOp(bearer, op.opId, op.studentId, body);
        }
    }

    /** The server accepted the change (or replayed an earlier acceptance). */
    private void accepted(PendingOperationEntity op, String body) {
        db.runInTransaction(() -> {
            // Re-read inside the transaction: the lecturer may have queued another
            // change for this student while this one was on its way.
            StudentEntity local = dao.findByIdNow(op.studentId);
            op.status = PendingOperationEntity.STATUS_DONE;
            op.lastError = null;
            ops.update(op);

            if (PendingOperationEntity.TYPE_DELETE.equals(op.type)) {
                dao.deleteById(op.studentId);
                return;
            }
            if (local == null) return;

            if (PendingOperationEntity.TYPE_GROUP_REQUEST.equals(op.type)) {
                // The request reached the server. The group only changes when a lecturer approves.
                String asked = null;
                try {
                    asked = str(JsonParser.parseString(body).getAsJsonObject(), "group_code", null);
                } catch (Exception ignored) {
                    // keep the generic wording
                }
                local.syncMessage = "Request for " + (asked != null ? asked : "the group")
                        + " sent. Waiting for your lecturer.";
                boolean stillQueued = ops.countToSendFor(op.accountId, op.studentId) > 0;
                local.syncState = stillQueued ? SyncState.PENDING : SyncState.SYNCED;
                if (!stillQueued) local.pendingJson = null;
                dao.update(local);
                return;
            }

            boolean correction = PendingOperationEntity.TYPE_CORRECTION_REQUEST.equals(op.type);
            JsonObject server = studentIn(body);
            if (correction) {
                // Nothing changes yet: the number only changes when the lecturer approves.
                local.syncMessage = "Number correction requested. Waiting for your lecturer.";
            } else if (server != null) {
                // Student routes reply with the full confirmed record: take it as it is.
                local.fullName = str(server, "student_name", local.fullName);
                local.studentNumber = str(server, "student_number", local.studentNumber);
                String programme = str(server, "programme_code", null);
                if (programme != null) local.program = ProgrammeCatalog.nameFor(programme);
                String group = str(server, "group_code", null);
                local.labGroup = group != null ? group : OutboxRepository.UNASSIGNED;
                int v = intIn(server, "record_version");
                if (v > 0) local.recordVersion = v;
                local.syncMessage = null;
            } else {
                // Lecturer routes reply with the new version; the values are the ones we sent.
                JsonObject p = op.payloadJson != null
                        ? JsonParser.parseString(op.payloadJson).getAsJsonObject() : new JsonObject();
                local.fullName = str(p, "student_name", local.fullName);
                local.studentNumber = str(p, "student_number", local.studentNumber);
                String programme = str(p, "programme_code", null);
                if (programme != null) local.program = ProgrammeCatalog.nameFor(programme);
                if (p.has("group_code")) {
                    String group = str(p, "group_code", null);
                    local.labGroup = group != null ? group : OutboxRepository.UNASSIGNED;
                }
                int confirmed = intField(body, "record_version");
                local.recordVersion = confirmed > 0 ? confirmed
                        : (PendingOperationEntity.TYPE_CREATE.equals(op.type) ? 1 : local.recordVersion + 1);
                local.syncMessage = null;
            }

            boolean more = ops.countToSendFor(op.accountId, op.studentId) > 0;
            local.syncState = more ? SyncState.PENDING : SyncState.SYNCED;
            if (!more) local.pendingJson = null;
            dao.update(local);
        });
    }

    /** The server refused the change: keep it for review, never overwrite the server's data. */
    private void refuse(PendingOperationEntity op, String message) {
        db.runInTransaction(() -> {
            StudentEntity local = dao.findByIdNow(op.studentId);
            op.status = PendingOperationEntity.STATUS_ACTION_REQUIRED;
            op.lastError = message;
            ops.update(op);
            if (local != null) {
                local.syncState = SyncState.ACTION_REQUIRED;
                local.syncMessage = message;
                dao.update(local);
            }
        });
    }

    private void backToPending(PendingOperationEntity op, String why) {
        db.runInTransaction(() -> {
            op.status = PendingOperationEntity.STATUS_PENDING;
            op.lastError = why;
            ops.update(op);
            setState(op.studentId, SyncState.PENDING);
        });
    }

    /** Changes only the status label of a student row (re-read, so nothing else is overwritten). */
    private void setState(String studentId, String state) {
        db.runInTransaction(() -> {
            StudentEntity local = dao.findByIdNow(studentId);
            if (local == null) return;
            local.syncState = state;
            dao.update(local);
        });
    }

    private static String messageFor(int code, String error, String serverMessage, PendingOperationEntity op) {
        if ("VERSION_CONFLICT".equals(error)) {
            return "Someone else changed this student first. Review the current details, then edit again or discard your change.";
        }
        if ("GROUP_FULL".equals(error)) {
            return "That group filled up before your change was sent. The student keeps their previous group.";
        }
        if ("DUPLICATE_STUDENT_NUMBER".equals(error)) return "That student number is already in use on the server.";
        if ("STUDENT_DELETED".equals(error) || (code == 404 && !PendingOperationEntity.TYPE_CREATE.equals(op.type))) {
            return "This student was deleted on the server. Your change was not applied.";
        }
        if ("INVALID_PROGRAMME".equals(error)) return "The server doesn't recognise that programme.";
        if ("INVALID_GROUP".equals(error)) return "The server doesn't recognise that group.";
        if ("OPERATION_ID_REUSED".equals(error)) return "This change conflicts with an earlier one. Discard it and edit again.";
        if ("REQUEST_PENDING".equals(error)) {
            if (PendingOperationEntity.TYPE_GROUP_REQUEST.equals(op.type)) {
                return serverMessage != null ? serverMessage : "You already have a group request waiting for your lecturer.";
            }
            return "You already have a number correction waiting for your lecturer.";
        }
        if ("ALREADY_IN_GROUP".equals(error)) return serverMessage != null ? serverMessage : "You are already in that group.";
        if ("SAME_NUMBER".equals(error)) return "That is already your student number.";
        if ("FIELD_NOT_PERMITTED".equals(error)) return "Students can't change that field directly.";
        if ("VALIDATION_ERROR".equals(error)) {
            return serverMessage != null
                    ? "The server rejected the details: " + serverMessage + " Edit them, or discard the change."
                    : "The server rejected the details. Check them and try again.";
        }
        if (code == 403) return "Only lecturers can make this change.";
        if (serverMessage != null) return serverMessage;
        return "The server refused this change (" + code + (error != null ? " " + error : "") + ").";
    }

    // ------------------------------------------------------------------------- PULL

    private Report pull(String bearer) {
        try {
            long since = session.getSyncCursor();
            boolean hasMore = true;
            int pages = 0;

            while (hasMore && pages++ < 50) {
                Response<SyncResponse> res = RetrofitClient.getApiService()
                        .getChanges(bearer, since, 100)
                        .execute();

                if (res.code() == 401) {
                    return new Report(Outcome.SIGN_IN_REQUIRED, "Session expired. Sign in again.");
                }
                if (!res.isSuccessful() || res.body() == null || res.body().sync == null) {
                    return new Report(Outcome.RETRY_LATER, "Sync failed (" + res.code() + ").");
                }

                SyncResponse body = res.body();
                db.runInTransaction(() -> {
                    if (body.changes != null) {
                        for (SyncResponse.Change c : body.changes) apply(c);
                    }
                });
                // The cursor only moves after the page is safely in Room.
                session.saveSyncCursor(body.sync.nextSince);
                since = body.sync.nextSince;
                hasMore = body.sync.hasMore;
            }
            session.saveLastSync(System.currentTimeMillis());
            return new Report(Outcome.OK, "Synced");
        } catch (Exception e) {
            return new Report(Outcome.RETRY_LATER, "Can't reach the server.");
        }
    }

    /**
     * Applies one server change. Server data updates the CONFIRMED fields only: any change
     * still waiting on this phone (pendingJson, syncState) is kept, never thrown away.
     * A deletion marker removes the student; a later stale edit can't bring it back
     * because the server answers it with STUDENT_DELETED.
     */
    private void apply(SyncResponse.Change c) {
        if ("DELETE".equals(c.changeType)) {
            dao.deleteById(c.studentId);
            return;
        }
        if (c.student == null) return;

        StudentEntity local = dao.findByIdNow(c.studentId);
        if (local != null && local.recordVersion > c.student.recordVersion) return;   // older copy

        StudentEntity s = new StudentEntity(
                c.student.studentId,
                c.student.studentNumber,
                c.student.studentName,
                ProgrammeCatalog.nameFor(c.student.programmeCode),
                c.student.groupCode != null ? c.student.groupCode : OutboxRepository.UNASSIGNED);
        s.recordVersion = c.student.recordVersion;
        s.isActive = true;
        if (local != null) {
            s.syncState = local.syncState;
            s.pendingJson = local.pendingJson;
            s.syncMessage = local.syncMessage;
        }
        dao.insert(s);   // REPLACE: applying the same change twice is harmless
    }

    /**
     * Refreshes the cached group list (codes + capacities). Lecturer only; students get
     * 403 and keep the built-in list. A failure here never fails the sync.
     */
    /** Both roles: students need new groups (e.g. G15) in their group-request list too. */
    private void refreshGroups(String bearer) {
        try {
            Response<GroupListResponse> res = RetrofitClient.getApiService().getGroups(bearer).execute();
            if (res.isSuccessful() && res.body() != null) {
                GroupCatalog.saveFromServer(appContext, res.body());
            }
        } catch (Exception ignored) {
            // Keep the cached list.
        }
    }

    // ------------------------------------------------------------------------- helpers

    private static String bodyText(Response<ResponseBody> res) {
        try {
            ResponseBody b = res.isSuccessful() ? res.body() : res.errorBody();
            return b != null ? b.string() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads {"error": "CODE"} from a reply body, or null. */
    /** The human "message" in an error reply, or null. */
    private static String serverMessage(String body) {
        try {
            JsonElement e = JsonParser.parseString(body).getAsJsonObject().get("message");
            return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String errorCode(String body) {
        try {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            JsonElement e = o.get("error");
            return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** The "student" object inside a reply body, or null. */
    private static JsonObject studentIn(String body) {
        try {
            JsonElement e = JsonParser.parseString(body).getAsJsonObject().get("student");
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int intIn(JsonObject o, String name) {
        JsonElement e = o.get(name);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
    }

    private static int intField(String body, String name) {
        try {
            JsonElement e = JsonParser.parseString(body).getAsJsonObject().get(name);
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String str(JsonObject o, String name, String fallback) {
        JsonElement e = o.get(name);
        return e != null && !e.isJsonNull() ? e.getAsString() : fallback;
    }
}
