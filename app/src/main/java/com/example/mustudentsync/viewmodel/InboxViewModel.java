package com.example.mustudentsync.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mustudentsync.network.ApiService;
import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.push.NotificationCenter;
import com.example.mustudentsync.util.SessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Notifications (both roles) plus the lecturer's inbox: group requests, number corrections and
 * announcements. These need the server (a lecturer decides on live data), so they are online
 * actions; each one carries an operation ID so a retry after a dropped connection is applied once.
 * Lists live here, so rotating the phone doesn't reload them.
 */
public class InboxViewModel extends AndroidViewModel {

    /** One-off message for a Toast (consumed once). */
    public static final class Event {
        public final boolean ok;
        public final String message;
        private boolean handled;

        Event(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public boolean take() {
            if (handled) return false;
            handled = true;
            return true;
        }
    }

    private final MutableLiveData<List<JsonObject>> notifications = new MutableLiveData<>();
    private final MutableLiveData<List<JsonObject>> groupRequests = new MutableLiveData<>();
    private final MutableLiveData<List<JsonObject>> corrections = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> offline = new MutableLiveData<>(false);
    private final MutableLiveData<Event> events = new MutableLiveData<>();

    private final SessionManager session;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Same action retried after a network failure -> same operation ID (applied once). */
    private final Map<String, String> operationIds = new HashMap<>();

    public InboxViewModel(@NonNull Application application) {
        super(application);
        session = new SessionManager(application);
    }

    public LiveData<List<JsonObject>> getNotifications() { return notifications; }
    public LiveData<List<JsonObject>> getGroupRequests() { return groupRequests; }
    public LiveData<List<JsonObject>> getCorrections() { return corrections; }
    public LiveData<Boolean> isLoading() { return loading; }
    public LiveData<Boolean> isOffline() { return offline; }
    public LiveData<Event> getEvents() { return events; }

    private ApiService api() { return RetrofitClient.getApiService(); }

    /**
     * Loads the latest notifications, then marks them read (the user is looking at them).
     * The list keeps showing which ones were new.
     */
    public void loadNotifications() {
        String bearer = session.getBearerToken();
        if (bearer == null) return;
        loading.setValue(true);
        io.execute(() -> {
            try {
                JsonObject o = json(api().getNotifications(bearer, 50).execute());
                if (o == null) throw new IllegalStateException();
                notifications.postValue(list(o, "data"));
                offline.postValue(false);
                if (o.has("unread") && o.get("unread").getAsInt() > 0) {
                    JsonObject all = new JsonObject();
                    all.addProperty("all", true);
                    api().markNotificationsRead(bearer, all).execute();
                }
                NotificationCenter.setUnread(0);
            } catch (Exception e) {
                offline.postValue(true);
            }
            loading.postValue(false);
        });
    }

    /** Lecturer: pending group requests and number corrections. */
    public void loadRequests() {
        String bearer = session.getBearerToken();
        if (bearer == null) return;
        io.execute(() -> {
            try {
                JsonObject g = json(api().getGroupRequests(bearer, "PENDING").execute());
                JsonObject c = json(api().getCorrectionRequests(bearer).execute());
                if (g == null || c == null) throw new IllegalStateException();
                groupRequests.postValue(list(g, "data"));
                corrections.postValue(list(c, "data"));
                offline.postValue(false);
            } catch (Exception e) {
                offline.postValue(true);
            }
        });
    }

    public void resolveGroupRequest(String requestId, boolean approve, String note) {
        JsonObject body = decision(approve, note);
        perform("group:" + requestId + ":" + approve,
                (bearer, opId) -> api().resolveGroupRequestOp(bearer, opId, requestId, body),
                approve ? "Approved. The student has been moved." : "Rejected. The student has been told.");
    }

    public void resolveCorrection(String requestId, boolean approve, String note) {
        JsonObject body = decision(approve, note);
        perform("correction:" + requestId + ":" + approve,
                (bearer, opId) -> api().resolveCorrectionOp(bearer, opId, requestId, body),
                approve ? "Approved. The number has been changed." : "Rejected. The student has been told.");
    }

    /** audience: "ALL" or a group code such as "G08". */
    public void sendAnnouncement(String title, String message, String audience) {
        JsonObject body = new JsonObject();
        body.addProperty("title", title);
        body.addProperty("body", message);
        body.addProperty("audience", audience);
        perform("announce:" + audience + ":" + title + ":" + message.hashCode(),
                (bearer, opId) -> api().sendAnnouncementOp(bearer, opId, body), null);
    }

    // ------------------------------------------------------------------------------

    private interface Request {
        Call<ResponseBody> make(String bearer, String operationId);
    }

    private void perform(String actionKey, Request request, String successText) {
        String bearer = session.getBearerToken();
        if (bearer == null) {
            events.setValue(new Event(false, "Session expired. Sign in again."));
            return;
        }
        String opId;
        synchronized (operationIds) {
            opId = operationIds.get(actionKey);
            if (opId == null) {
                opId = UUID.randomUUID().toString();
                operationIds.put(actionKey, opId);
            }
        }
        final String operationId = opId;
        loading.setValue(true);
        io.execute(() -> {
            try {
                Response<ResponseBody> res = request.make(bearer, operationId).execute();
                synchronized (operationIds) {
                    operationIds.remove(actionKey);   // the server answered: a new attempt is a new action
                }
                JsonObject o = parse(res.isSuccessful()
                        ? (res.body() != null ? res.body().string() : null)
                        : (res.errorBody() != null ? res.errorBody().string() : null));
                if (res.isSuccessful()) {
                    String text = successText != null ? successText : str(o, "message", "Sent.");
                    events.postValue(new Event(true, text));
                } else {
                    events.postValue(new Event(false, errorText(res.code(), o)));
                }
            } catch (Exception e) {
                // Kept: retrying sends the same operation ID, so it can't be applied twice.
                events.postValue(new Event(false, "Couldn't reach the server. Check your connection and try again."));
            }
            loading.postValue(false);
            loadRequests();
        });
    }

    private static String errorText(int code, JsonObject o) {
        String error = str(o, "error", null);
        String message = str(o, "message", null);
        if ("ALREADY_RESOLVED".equals(error)) return "Already answered (maybe by another lecturer).";
        if ("NUMBER_TAKEN".equals(error)) return "Another student already uses that number. Reject this request.";
        if (message != null) return message;
        if (code == 401) return "Session expired. Sign in again.";
        if (code == 403) return "Only lecturers can do this.";
        return "The server refused this (" + code + (error != null ? " " + error : "") + ").";
    }

    private static JsonObject decision(boolean approve, String note) {
        JsonObject body = new JsonObject();
        body.addProperty("decision", approve ? "APPROVE" : "REJECT");
        if (note != null && !note.trim().isEmpty()) body.addProperty("note", note.trim());
        return body;
    }

    private static JsonObject json(Response<ResponseBody> res) throws java.io.IOException {
        if (!res.isSuccessful() || res.body() == null) return null;
        return parse(res.body().string());
    }

    private static JsonObject parse(String text) {
        if (text == null || text.isEmpty()) return new JsonObject();
        try {
            JsonElement e = JsonParser.parseString(text);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (Exception ex) {
            return new JsonObject();
        }
    }

    private static List<JsonObject> list(JsonObject o, String name) {
        List<JsonObject> out = new ArrayList<>();
        JsonElement e = o.get(name);
        if (e != null && e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (JsonElement item : a) if (item.isJsonObject()) out.add(item.getAsJsonObject());
        }
        return out;
    }

    public static String str(JsonObject o, String name, String fallback) {
        if (o == null) return fallback;
        JsonElement e = o.get(name);
        return e != null && !e.isJsonNull() ? e.getAsString() : fallback;
    }

    @Override
    protected void onCleared() {
        io.shutdown();
    }
}
