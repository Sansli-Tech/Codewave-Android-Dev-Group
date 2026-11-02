package com.example.mustudentsync.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.network.dto.GroupListResponse;
import com.example.mustudentsync.sync.SyncScheduler;
import com.example.mustudentsync.util.GroupCatalog;
import com.example.mustudentsync.util.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lecturer group management: create the next group, auto-assign unassigned students
 * (groups of up to 15) and export all groups to Excel.
 *
 * These are online actions: the server decides, inside one locked transaction, so two
 * lecturers can never overfill a group. Each action keeps its operation ID until the server
 * answers, so tapping "Try again" after a lost reply replays the same operation instead of
 * creating a second group or placing students twice (same rule as Challenge 2).
 * Kept in a ViewModel so a rotation during a request loses nothing.
 */
public class GroupAdminViewModel extends AndroidViewModel {

    /** One-off things for the screen to show or do. */
    public static class Event {
        public enum Kind { MESSAGE, PREVIEW, EXPORTED }
        public final Kind kind;
        public final String text;
        public final File file;
        private boolean handled;

        Event(Kind kind, String text, File file) {
            this.kind = kind;
            this.text = text;
            this.file = file;
        }

        /** Returns true once, so a rotation does not show the same dialog or share twice. */
        public boolean take() {
            if (handled) return false;
            handled = true;
            return true;
        }
    }

    private final SessionManager session;
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event> events = new MutableLiveData<>();

    // Kept until the server answers, then cleared: a retry re-sends the SAME ID.
    private String pendingCreateId;
    private String pendingAssignId;

    public GroupAdminViewModel(@NonNull Application application) {
        super(application);
        session = new SessionManager(application);
    }

    public LiveData<Boolean> isBusy() { return busy; }

    public LiveData<Event> getEvents() { return events; }

    // ------------------------------------------------------------------ create group

    public void createGroup() {
        String bearer = bearerOrComplain();
        if (bearer == null || Boolean.TRUE.equals(busy.getValue())) return;
        if (pendingCreateId == null) pendingCreateId = UUID.randomUUID().toString();
        busy.setValue(true);
        RetrofitClient.getApiService().createGroupOp(bearer, pendingCreateId, new JsonObject())
                .enqueue(new Reply() {
                    @Override
                    void ok(JsonObject body) {
                        pendingCreateId = null;
                        saveGroups(body);
                        message("Group " + str(body, "group_code") + " created (" + str(body, "capacity") + " places).");
                    }

                    @Override
                    void refused(String text) {
                        pendingCreateId = null;
                        message(text);
                    }
                });
    }

    // ------------------------------------------------------------------ auto-assign

    /** Asks the server what auto-assign would do; the screen shows it and asks to confirm. */
    public void previewAutoAssign() {
        String bearer = bearerOrComplain();
        if (bearer == null || Boolean.TRUE.equals(busy.getValue())) return;
        busy.setValue(true);
        RetrofitClient.getApiService().autoAssignPreview(bearer).enqueue(new Reply() {
            @Override
            void ok(JsonObject body) {
                int unassigned = body.has("unassigned") ? body.get("unassigned").getAsInt() : 0;
                if (unassigned == 0) {
                    message("Every student already has a group. Nothing to assign.");
                    return;
                }
                events.setValue(new Event(Event.Kind.PREVIEW, describe(body, unassigned), null));
            }

            @Override
            void refused(String text) { message(text); }
        });
    }

    public void autoAssign() {
        String bearer = bearerOrComplain();
        if (bearer == null || Boolean.TRUE.equals(busy.getValue())) return;
        if (pendingAssignId == null) pendingAssignId = UUID.randomUUID().toString();
        busy.setValue(true);
        RetrofitClient.getApiService().autoAssignOp(bearer, pendingAssignId, new JsonObject())
                .enqueue(new Reply() {
                    @Override
                    void ok(JsonObject body) {
                        pendingAssignId = null;
                        saveGroups(body);
                        int n = body.has("assigned") ? body.get("assigned").getAsInt() : 0;
                        StringBuilder text = new StringBuilder(n == 0
                                ? "There were no unassigned students."
                                : n + " students placed in groups.");
                        if (body.has("new_groups") && body.getAsJsonArray("new_groups").size() > 0) {
                            text.append(" New groups: ").append(join(body.getAsJsonArray("new_groups"))).append('.');
                        }
                        message(text.toString());
                    }

                    @Override
                    void refused(String text) {
                        pendingAssignId = null;
                        message(text);
                    }
                });
    }

    // ------------------------------------------------------------------ export

    public void exportToExcel() {
        String bearer = bearerOrComplain();
        if (bearer == null || Boolean.TRUE.equals(busy.getValue())) return;
        busy.setValue(true);
        RetrofitClient.getApiService().exportGroups(bearer).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    busy.setValue(false);
                    message(res.code() == 401 ? "Session expired. Sign in again."
                            : "The export failed (" + res.code() + "). Try again.");
                    return;
                }
                ResponseBody body = res.body();
                // Writing the file happens off the main thread.
                new Thread(() -> {
                    try {
                        File file = saveToCache(body);
                        busy.postValue(false);
                        events.postValue(new Event(Event.Kind.EXPORTED, null, file));
                    } catch (IOException e) {
                        busy.postValue(false);
                        events.postValue(new Event(Event.Kind.MESSAGE, "Couldn't save the Excel file on this phone.", null));
                    }
                }).start();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                busy.setValue(false);
                message("No connection to the server. The export needs to be online.");
            }
        });
    }

    private File saveToCache(ResponseBody body) throws IOException {
        File dir = new File(getApplication().getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("no export folder");
        // Only the newest export is kept on the phone.
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
            f.delete();
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        File file = new File(dir, "MUStudentSync-groups-" + day + ".xlsx");
        try (InputStream in = body.byteStream(); OutputStream out = new FileOutputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        return file;
    }

    // ------------------------------------------------------------------ helpers

    private String bearerOrComplain() {
        String bearer = session.getBearerToken();
        if (bearer == null) message("Session expired. Sign in again.");
        return bearer;
    }

    private void message(String text) {
        events.setValue(new Event(Event.Kind.MESSAGE, text, null));
    }

    /** Every reply carries the full group list: store it so new groups show up at once. */
    private void saveGroups(JsonObject body) {
        if (body.has("groups")) {
            JsonObject wrapper = new JsonObject();
            wrapper.add("groups", body.get("groups"));
            GroupCatalog.saveFromServer(getApplication(),
                    new Gson().fromJson(wrapper, GroupListResponse.class));
        }
        // Pull the moved students into this phone's copy (counts on the Groups screen).
        SyncScheduler.requestSync(getApplication());
    }

    static String describe(JsonObject body, int unassigned) {
        StringBuilder sb = new StringBuilder();
        sb.append(unassigned).append(unassigned == 1 ? " student has" : " students have")
                .append(" no group. They will be placed like this (groups are filled up to 15):\n\n");
        for (JsonElement e : body.getAsJsonArray("placements")) {
            JsonObject p = e.getAsJsonObject();
            boolean isNew = p.has("new_group") && p.get("new_group").getAsBoolean();
            sb.append(isNew ? "New " : "").append(str(p, "group_code"))
                    .append(":  +").append(str(p, "added"))
                    .append("   (").append(str(p, "before")).append(" → ").append(str(p, "after")).append(")\n");
        }
        sb.append("\nNo student who already has a group will be moved.");
        return sb.toString();
    }

    private static String join(com.google.gson.JsonArray a) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : a) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getAsString());
        }
        return sb.toString();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    /** Common reply handling: JSON parsing, 401, refusals with the server's message, no connection. */
    private abstract class Reply implements Callback<ResponseBody> {
        abstract void ok(JsonObject body);

        abstract void refused(String text);

        @Override
        public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> res) {
            busy.setValue(false);
            try {
                if (res.isSuccessful() && res.body() != null) {
                    ok(JsonParser.parseString(res.body().string()).getAsJsonObject());
                } else if (res.code() == 401) {
                    refused("Session expired. Sign in again.");
                } else if (res.code() >= 500) {
                    // Keep the operation ID: "Try again" replays the same operation.
                    message("The server had a problem (" + res.code() + "). Try again.");
                } else {
                    String text = "The server refused this (" + res.code() + ").";
                    if (res.errorBody() != null) {
                        JsonObject err = JsonParser.parseString(res.errorBody().string()).getAsJsonObject();
                        if (err.has("message")) text = err.get("message").getAsString();
                    }
                    refused(text);
                }
            } catch (Exception e) {
                message("Unexpected reply from the server.");
            }
        }

        @Override
        public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
            busy.setValue(false);
            // The operation ID is kept, so trying again cannot apply it twice.
            message("No connection, or the reply was lost. Check your connection and try again; "
                    + "it is safe to repeat.");
        }
    }
}
