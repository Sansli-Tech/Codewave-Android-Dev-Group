package com.example.mustudentsync.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mustudentsync.data.OutboxRepository;
import com.example.mustudentsync.data.RoomStudentRepository;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.util.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Student profile. The profile shown is the signed-in student's own Room copy (kept up to date by
 * sync). Changes go through the offline queue, so they are saved even with no connection and stay
 * Pending until the server confirms them. Live group occupancy and the correction request status
 * come from GET /api/me/student when online.
 */
public class StudentProfileViewModel extends AndroidViewModel {

    /** What only the server knows: live occupancy and the latest correction request. */
    public static class ServerInfo {
        public boolean online;
        public String groupCode;
        public int activeStudents;
        public int capacity;
        public String correctionNumber;   // null if the student never asked
        public String correctionStatus;   // PENDING, APPROVED or REJECTED
    }

    private final RoomStudentRepository repository;
    private final OutboxRepository outbox;
    private final SessionManager session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<ServerInfo> serverInfo = new MutableLiveData<>();
    private String studentId;

    public StudentProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new RoomStudentRepository(application);
        outbox = new OutboxRepository(application);
        session = new SessionManager(application);
    }

    /** The intent extra if given, otherwise the signed-in student's own ID. */
    public void setStudentId(String fromIntent) {
        studentId = fromIntent != null && !fromIntent.isEmpty() ? fromIntent : session.getStudentId();
    }

    public String getStudentId() {
        return studentId;
    }

    public LiveData<StudentEntity> getStudent() {
        return repository.getStudent(studentId);
    }

    public LiveData<ServerInfo> getServerInfo() {
        return serverInfo;
    }

    /** Off the main thread; posts online=false if the server can't be reached. */
    public void refreshServerInfo() {
        executor.execute(() -> {
            ServerInfo info = new ServerInfo();
            try {
                Response<ResponseBody> res = RetrofitClient.getApiService()
                        .getMyProfile(session.getBearerToken())
                        .execute();
                if (res.isSuccessful() && res.body() != null) {
                    JsonObject o = JsonParser.parseString(res.body().string()).getAsJsonObject();
                    info.online = true;
                    JsonObject group = obj(o, "group");
                    if (group != null) {
                        info.groupCode = text(group, "group_code");
                        info.activeStudents = number(group, "active_students");
                        info.capacity = number(group, "capacity");
                    }
                    JsonObject correction = obj(o, "correction_request");
                    if (correction != null) {
                        info.correctionNumber = text(correction, "requested_number");
                        info.correctionStatus = text(correction, "status");
                    }
                }
            } catch (Exception ignored) {
                info.online = false;
            }
            serverInfo.postValue(info);
        });
    }

    public void saveProfile(String name, String programName, OutboxRepository.Callback cb) {
        outbox.enqueueOwnProfile(studentId, name, programName, cb);
    }

    public void requestGroup(String groupCode, OutboxRepository.Callback cb) {
        outbox.enqueueGroupRequest(studentId, groupCode, cb);
    }

    public void requestCorrection(String number, OutboxRepository.Callback cb) {
        outbox.enqueueCorrectionRequest(studentId, number, cb);
    }

    public void discardChanges(OutboxRepository.Callback cb) {
        outbox.discardChanges(studentId, cb);
    }

    private static JsonObject obj(JsonObject o, String name) {
        JsonElement e = o.get(name);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String text(JsonObject o, String name) {
        JsonElement e = o.get(name);
        return e != null && !e.isJsonNull() ? e.getAsString() : null;
    }

    private static int number(JsonObject o, String name) {
        JsonElement e = o.get(name);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
    }
}
