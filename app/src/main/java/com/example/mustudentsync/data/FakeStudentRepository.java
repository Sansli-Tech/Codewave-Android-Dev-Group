package com.example.mustudentsync.data;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mustudentsync.model.RegistrationDraftEntity;
import com.example.mustudentsync.model.StudentEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory fake used for fast JUnit tests — no Room, no Android framework,
 * no real threading. Callbacks fire synchronously/immediately, which is fine
 * for tests since there's no real main-thread/background-thread split to honour.
 */
public class FakeStudentRepository implements StudentRepository {

    private final Map<String, StudentEntity> students = new HashMap<>();
    private final MutableLiveData<List<StudentEntity>> rosterLiveData = new MutableLiveData<>(new ArrayList<>());
    private RegistrationDraftEntity draft;

    @Override
    public LiveData<List<StudentEntity>> getRoster(String searchQuery, String program, String group) {
        return rosterLiveData;
    }

    @Override
    public LiveData<StudentEntity> getStudent(String studentId) {
        return new MutableLiveData<>(students.get(studentId));
    }

    @Override
    public void isGroupFullAsync(String labGroup, GroupFullCallback callback) {
        if ("Unassigned".equals(labGroup)) {
            callback.onResult(false);
            return;
        }
        int count = 0;
        for (StudentEntity s : students.values()) {
            if (s.isActive && labGroup.equals(s.labGroup)) count++;
        }
        callback.onResult(count >= 15);
    }

    @Override
    public void saveStudent(StudentEntity student) {
        students.put(student.studentId, student);
        rosterLiveData.setValue(new ArrayList<>(students.values()));
    }

    @Override
    public void deleteStudent(StudentEntity student) {
        students.remove(student.studentId);
        rosterLiveData.setValue(new ArrayList<>(students.values()));
    }

    @Override
    public void saveRegistrationDraft(RegistrationDraftEntity draft) {
        this.draft = draft;
    }

    @Override
    public void loadRegistrationDraft(DraftLoadedCallback callback) {
        callback.onDraftLoaded(draft);
    }

    @Override
    public void clearRegistrationDraft() {
        draft = null;
    }

    // Test-only helper, not part of the interface — lets a test pre-populate students directly.
    public void seedStudent(StudentEntity student) {
        students.put(student.studentId, student);
    }
}