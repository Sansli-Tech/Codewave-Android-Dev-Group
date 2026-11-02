package com.example.mustudentsync.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mustudentsync.data.RoomStudentRepository;
import com.example.mustudentsync.data.StudentRepository;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.model.SyncState;
import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.util.ProgrammeCatalog;
import com.example.mustudentsync.util.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lecturer roster (Activity D).
 *
 * ONLINE: search + programme + group filters are sent to GET /api/students, 20 at a time
 * ("Load more" fetches the next page). Typing is debounced, the previous request is
 * cancelled, and any reply that still arrives for an older query is ignored (sequence number).
 *
 * OFFLINE (or session expired): the same filters run on the Room copy, and the screen says
 * clearly that it is this phone's copy and may be incomplete.
 *
 * Either way, changes saved on this phone but not yet synced are shown with their status label.
 */
public class LecturerRosterViewModel extends AndroidViewModel {

    public static final int PAGE_SIZE = 20;
    private static final long DEBOUNCE_MS = 300;

    public enum Source { LOADING, ONLINE, CACHED, SESSION_EXPIRED }

    /** Everything the screen needs to draw the list and the source label. */
    public static class RosterState {
        public List<StudentEntity> students = new ArrayList<>();
        public Source source = Source.LOADING;
        public int total;          // online: total matches on the server
        public boolean hasMore;    // online: another page exists
    }

    private final StudentRepository repository;
    private final SessionManager session;
    private final Handler main = new Handler(Looper.getMainLooper());

    private String searchQuery = "";
    private String programFilter = "All Programs";
    private String groupFilter = "All Groups";

    // Room copy for the current filters (only ONE source attached at a time).
    private final MediatorLiveData<RosterState> state = new MediatorLiveData<>();
    private LiveData<List<StudentEntity>> cachedSource;
    private List<StudentEntity> cached = new ArrayList<>();

    // Server results for the current filters.
    private final List<StudentEntity> online = new ArrayList<>();
    private Source source = Source.LOADING;
    private int page = 0;
    private int total = 0;
    private boolean loadingPage = false;
    private Call<ResponseBody> inFlight;
    private int querySeq = 0;   // bumps on every filter change; older replies are ignored

    private final Runnable runQuery = () -> fetchPage(true);

    public LecturerRosterViewModel(@NonNull Application application) {
        super(application);
        repository = new RoomStudentRepository(application);
        session = new SessionManager(application);
        filtersChanged();
    }

    public LiveData<RosterState> getState() {
        return state;
    }

    public void setSearchQuery(String query) {
        String q = query != null ? query.trim() : "";
        if (q.equals(searchQuery)) return;
        searchQuery = q;
        filtersChanged();
    }

    public void setProgramFilter(String program) {
        if (program == null || program.equals(programFilter)) return;
        programFilter = program;
        filtersChanged();
    }

    public void setGroupFilter(String group) {
        if (group == null || group.equals(groupFilter)) return;
        groupFilter = group;
        filtersChanged();
    }

    public String getCurrentSearchQuery() {
        return searchQuery;
    }

    /** Re-run the current query against the server (e.g. after a sync). */
    public void refresh() {
        main.removeCallbacks(runQuery);
        fetchPage(true);
    }

    /** Next page from the server, if there is one (called when the list is scrolled to the end). */
    public void loadMore() {
        if (source == Source.ONLINE && !loadingPage && online.size() < total) fetchPage(false);
    }

    /** Result arrives on the main thread via callback — never blocks the UI thread. */
    public void isGroupFull(String labGroup, StudentRepository.GroupFullCallback callback) {
        repository.isGroupFullAsync(labGroup, callback);
    }

    // ------------------------------------------------------------------ internals

    private void filtersChanged() {
        // 1. Swap the Room query: remove the old source so its late results can't overwrite.
        if (cachedSource != null) state.removeSource(cachedSource);
        cachedSource = repository.getRoster(searchQuery, programFilter, groupFilter);
        state.addSource(cachedSource, list -> {
            cached = list != null ? list : new ArrayList<>();
            publish();
        });

        // 2. Cancel the old server request and ask again after a short pause in typing.
        querySeq++;
        if (inFlight != null) inFlight.cancel();
        loadingPage = false;
        main.removeCallbacks(runQuery);
        main.postDelayed(runQuery, DEBOUNCE_MS);
    }

    private void fetchPage(boolean first) {
        String bearer = session.getBearerToken();
        if (bearer == null) {
            source = Source.SESSION_EXPIRED;
            publish();
            return;
        }
        final int seq = first ? ++querySeq : querySeq;
        final int wanted = first ? 1 : page + 1;
        if (first && inFlight != null) inFlight.cancel();
        if (first) {
            source = Source.LOADING;
            publish();
        }
        loadingPage = true;

        String programme = "All Programs".equals(programFilter) ? null : ProgrammeCatalog.codeFor(programFilter);
        String group = "All Groups".equals(groupFilter) ? null
                : ("Unassigned".equals(groupFilter) ? "UNASSIGNED" : groupFilter);
        String search = searchQuery.isEmpty() ? null : searchQuery;

        Call<ResponseBody> call = RetrofitClient.getApiService()
                .listStudents(bearer, search, programme, group, wanted, PAGE_SIZE);
        inFlight = call;
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> res) {
                if (seq != querySeq) return;   // an older query: ignore it
                loadingPage = false;
                if (res.code() == 401) {
                    source = Source.SESSION_EXPIRED;
                } else if (res.isSuccessful() && res.body() != null) {
                    try {
                        JsonObject o = JsonParser.parseString(res.body().string()).getAsJsonObject();
                        if (first) online.clear();
                        for (JsonElement e : o.getAsJsonArray("data")) online.add(fromServer(e.getAsJsonObject()));
                        total = o.getAsJsonObject("pagination").get("total").getAsInt();
                        page = wanted;
                        source = Source.ONLINE;
                    } catch (Exception e) {
                        source = Source.CACHED;
                    }
                } else {
                    source = Source.CACHED;
                }
                publish();
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (seq != querySeq || c.isCanceled()) return;
                loadingPage = false;
                source = Source.CACHED;   // offline: fall back to this phone's copy
                publish();
            }
        });
    }

    /** Builds what the screen shows from the server page(s) or the Room copy. */
    private void publish() {
        RosterState s = new RosterState();
        s.source = source;
        if (source == Source.ONLINE) {
            Map<String, StudentEntity> local = new HashMap<>();
            for (StudentEntity e : cached) local.put(e.studentId, e);
            // Students added on this phone and not yet on the server come first.
            for (StudentEntity e : cached) {
                if (e.recordVersion == 0) s.students.add(e);
            }
            for (StudentEntity server : online) {
                StudentEntity mine = local.get(server.studentId);
                if (mine != null && !SyncState.SYNCED.equals(mine.syncState)) {
                    // Keep this phone's pending status/proposal on the server's confirmed values.
                    server.syncState = mine.syncState;
                    server.pendingJson = mine.pendingJson;
                    server.syncMessage = mine.syncMessage;
                }
                s.students.add(server);
            }
            s.total = total;
            s.hasMore = online.size() < total;
        } else {
            s.students.addAll(cached);
            s.total = cached.size();
        }
        state.setValue(s);
    }

    private static StudentEntity fromServer(JsonObject o) {
        String group = o.get("group_code") == null || o.get("group_code").isJsonNull()
                ? "Unassigned" : o.get("group_code").getAsString();
        StudentEntity e = new StudentEntity(
                o.get("student_id").getAsString(),
                o.get("student_number").getAsString(),
                o.get("student_name").getAsString(),
                ProgrammeCatalog.nameFor(o.get("programme_code").getAsString()),
                group);
        e.recordVersion = o.get("record_version").getAsInt();
        e.isActive = true;
        return e;
    }

    @Override
    protected void onCleared() {
        main.removeCallbacks(runQuery);
        if (inFlight != null) inFlight.cancel();
    }
}
