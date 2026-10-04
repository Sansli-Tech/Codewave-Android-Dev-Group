package com.example.mustudentsync.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.mustudentsync.data.local.AppDatabase;
import com.example.mustudentsync.data.local.DraftDao;
import com.example.mustudentsync.data.local.StudentDao;
import com.example.mustudentsync.model.RegistrationDraftEntity;
import com.example.mustudentsync.model.StudentEntity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RoomStudentRepository implements StudentRepository {

    private final StudentDao studentDao;
    private final DraftDao draftDao;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public RoomStudentRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.studentDao = db.studentDao();
        this.draftDao = db.draftDao();
    }

    @Override
    public LiveData<List<StudentEntity>> getRoster(String searchQuery, String program, String group) {
        String safeQuery = searchQuery == null ? "" : searchQuery;
        String safeProgram = program == null ? "All Programs" : program;
        String safeGroup = group == null ? "All Groups" : group;
        return studentDao.searchAndFilter(safeQuery, safeProgram, safeGroup);
    }

    @Override
    public LiveData<StudentEntity> getStudent(String studentId) {
        return studentDao.getStudentById(studentId);
    }

    @Override
    public void isGroupFullAsync(String labGroup, GroupFullCallback callback) {
        if ("Unassigned".equals(labGroup)) {
            callback.onResult(false);
            return;
        }
        backgroundExecutor.execute(() -> {
            boolean full = studentDao.countActiveInGroup(labGroup) >= 15;
            mainThreadHandler.post(() -> callback.onResult(full));
        });
    }

    @Override
    public void saveStudent(StudentEntity student) {
        backgroundExecutor.execute(() -> studentDao.insert(student));
    }

    @Override
    public void deleteStudent(StudentEntity student) {
        backgroundExecutor.execute(() -> studentDao.delete(student));
    }

    @Override
    public void saveRegistrationDraft(RegistrationDraftEntity draft) {
        draft.savedAtMillis = System.currentTimeMillis();
        backgroundExecutor.execute(() -> draftDao.saveDraft(draft));
    }

    @Override
    public void loadRegistrationDraft(DraftLoadedCallback callback) {
        backgroundExecutor.execute(() -> {
            RegistrationDraftEntity draft = draftDao.getDraft();
            mainThreadHandler.post(() -> callback.onDraftLoaded(draft));
        });
    }

    @Override
    public void clearRegistrationDraft() {
        backgroundExecutor.execute(draftDao::clearAllDrafts);
    }

    /** Seeds one fictitious demo student the first time the Profile screen opens, if none exists. */
    public void ensureDemoStudentExists(String demoId, Runnable onReady) {
        backgroundExecutor.execute(() -> {
            StudentEntity existing = studentDao.findByStudentNumber("000000001");
            if (existing == null) {
                StudentEntity demo = new StudentEntity(demoId, "000000001", "Jane Demo", "Computer Science", "G02");
                studentDao.insert(demo);
            }
            mainThreadHandler.post(onReady);
        });
    }
}