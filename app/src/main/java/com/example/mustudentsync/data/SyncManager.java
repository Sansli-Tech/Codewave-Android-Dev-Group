package com.example.mustudentsync.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.mustudentsync.data.local.AppDatabase;
import com.example.mustudentsync.data.local.StudentDao;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.network.dto.StudentRequest;
import com.example.mustudentsync.network.dto.SyncResponse;
import com.example.mustudentsync.util.ProgrammeCatalog;
import com.example.mustudentsync.util.SessionManager;
import com.example.mustudentsync.network.dto.GroupListResponse;
import com.example.mustudentsync.util.GroupCatalog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class SyncManager {

    public interface Callback {
        void onDone(boolean success, String message);
    }

    private final AppDatabase db;
    private final StudentDao dao;
    private final SessionManager session;

    private final Context appContext;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler main =
            new Handler(Looper.getMainLooper());

    // FIXED: Accept Context instead of AdapterView.OnItemSelectedListener.
    public SyncManager(Context context) {
        appContext = context.getApplicationContext();

        db = AppDatabase.getInstance(appContext);
        dao = db.studentDao();
        session = new SessionManager(appContext);
    }

    public void syncNow(Callback cb) {
        executor.execute(() -> {
            try {
                long since = session.getSyncCursor();
                boolean hasMore = true;
                int pages = 0;

                while (hasMore && pages++ < 50) {
                    Response<SyncResponse> res =
                            RetrofitClient.getApiService()
                                    .getChanges(
                                            session.getBearerToken(),
                                            since,
                                            100
                                    )
                                    .execute();

                    if (res.code() == 401) {
                        post(cb, false,
                                "Session expired. Sign in again.");
                        return;
                    }

                    if (!res.isSuccessful() || res.body() == null) {
                        post(cb, false,
                                "Sync failed (" + res.code() + ").");
                        return;
                    }

                    SyncResponse body = res.body();
                    final long next = body.sync.nextSince;

                    // Apply the page and save the cursor after success.
                    db.runInTransaction(() -> {
                        for (SyncResponse.Change c : body.changes) {
                            apply(c);
                        }
                    });

                    session.saveSyncCursor(next);

                    since = next;
                    hasMore = body.sync.hasMore;
                }
                session.saveLastSync(System.currentTimeMillis());
                refreshGroups();
                post(cb, true, "Synced");
                refreshGroups();
                post(cb, true, "Synced");

            } catch (Exception e) {
                post(cb, false, "Can't reach the server.");
            }
        });
    }

    /**
     * Refreshes the cached group list (codes + capacities) from the server.
     * Lecturer only; students get 403, which is fine: they keep the built-in list.
     * A failure here never fails the sync.
     */
    private void refreshGroups() {
        if (!"LECTURER".equalsIgnoreCase(session.getRole())) return;
        try {
            Response<GroupListResponse> res = RetrofitClient.getApiService()
                    .getGroups(session.getBearerToken())
                    .execute();
            if (res.isSuccessful() && res.body() != null) {
                GroupCatalog.saveFromServer(appContext, res.body());
            }
        } catch (Exception ignored) {
            // Keep the cached list.
        }
    }
    private void apply(SyncResponse.Change c) {
        if ("DELETE".equals(c.changeType)) {
            dao.deleteById(c.studentId);
            return;
        }

        if (c.student == null) {
            return;
        }

        StudentEntity local = dao.findByIdNow(c.studentId);

        // Never overwrite newer local data with an older server copy.
        if (local != null
                && local.recordVersion > c.student.recordVersion) {
            return;
        }

        StudentEntity s = new StudentEntity(
                c.student.studentId,
                c.student.studentNumber,
                c.student.studentName,
                ProgrammeCatalog.nameFor(c.student.programmeCode),
                c.student.groupCode != null
                        ? c.student.groupCode
                        : "Unassigned"
        );

        s.recordVersion = c.student.recordVersion;
        s.isActive = true;

        // REPLACE, so applying the same change twice is safe.
        dao.insert(s);
    }

    private void post(Callback cb, boolean ok, String msg) {
        main.post(() -> {
            if (cb != null) {
                cb.onDone(ok, msg);
            }
        });
    }

    public void deleteStudent(StudentEntity student, Callback cb) {
        executor.execute(() -> {
            try {
                Response<okhttp3.ResponseBody> res =
                        RetrofitClient.getApiService()
                                .deleteStudent(
                                        session.getBearerToken(),
                                        student.studentId,
                                        new com.example.mustudentsync.network.dto.DeleteRequest(
                                                student.recordVersion
                                        )
                                )
                                .execute();

                if (res.isSuccessful() || res.code() == 404) {
                    // Deleted on the server, or it never existed there.
                    dao.deleteById(student.studentId);
                    post(cb, true, "Deleted");

                } else if (res.code() == 409) {
                    post(cb, false,
                            "This student was changed elsewhere. "
                                    + "Refresh and try again.");

                } else if (res.code() == 401) {
                    post(cb, false,
                            "Session expired. Sign in again.");

                } else {
                    post(cb, false,
                            "Delete failed (" + res.code() + ").");
                }

            } catch (Exception e) {
                post(cb, false,
                        "Can't reach the server. Nothing was deleted.");
            }
        });
    }

    public void createStudent(
            String name,
            String number,
            String programName,
            String groupName,
            Callback cb
    ) {
        executor.execute(() -> {
            try {
                StudentRequest req = new StudentRequest(
                        name,
                        number,
                        ProgrammeCatalog.codeFor(programName),
                        groupCode(groupName),
                        null
                );

                Response<okhttp3.ResponseBody> res =
                        RetrofitClient.getApiService()
                                .createStudent(
                                        session.getBearerToken(),
                                        req
                                )
                                .execute();

                finishWrite(res, cb);

            } catch (Exception e) {
                post(cb, false,
                        "Can't reach the server. Nothing was saved.");
            }
        });
    }

    public void updateStudent(
            StudentEntity existing,
            String name,
            String number,
            String programName,
            String groupName,
            Callback cb
    ) {
        executor.execute(() -> {
            try {
                StudentRequest req = new StudentRequest(
                        name,
                        number,
                        ProgrammeCatalog.codeFor(programName),
                        groupCode(groupName),
                        existing.recordVersion
                );

                Response<okhttp3.ResponseBody> res =
                        RetrofitClient.getApiService()
                                .updateStudent(
                                        session.getBearerToken(),
                                        existing.studentId,
                                        req
                                )
                                .execute();

                finishWrite(res, cb);

            } catch (Exception e) {
                post(cb, false,
                        "Can't reach the server. Nothing was saved.");
            }
        });
    }

    private void finishWrite(
            Response<okhttp3.ResponseBody> res,
            Callback cb
    ) {
        if (res.isSuccessful()) {
            // Pull the saved record into Room.
            syncNow(cb);
        } else {
            post(cb, false, writeErrorMessage(res));
        }
    }

    private String groupCode(String groupName) {
        return "Unassigned".equals(groupName) ? null : groupName;
    }

    private String writeErrorMessage(Response<?> res) {
        String code = null;

        try {
            if (res.errorBody() != null) {
                com.google.gson.JsonObject o =
                        com.google.gson.JsonParser
                                .parseString(
                                        res.errorBody().string()
                                )
                                .getAsJsonObject();

                if (o.has("error")
                        && o.get("error").isJsonPrimitive()) {
                    code = o.get("error").getAsString();
                }
            }
        } catch (Exception ignored) {
            // The server may return an error body that is not JSON.
        }

        if ("DUPLICATE_STUDENT_NUMBER".equals(code)) {
            return "That student number is already in use.";
        }

        if ("GROUP_FULL".equals(code)) {
            return "That group is already full.";
        }

        if ("VERSION_CONFLICT".equals(code)) {
            return "This student was changed elsewhere. "
                    + "Go back and refresh.";
        }

        if ("INVALID_PROGRAMME".equals(code)) {
            return "The server doesn't recognise that programme.";
        }

        if (res.code() == 401) {
            return "Session expired. Sign in again.";
        }

        return "Save failed (" + res.code() + ").";
    }
}
