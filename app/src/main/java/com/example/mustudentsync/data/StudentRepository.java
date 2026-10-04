package com.example.mustudentsync.data;

import androidx.lifecycle.LiveData;

import com.example.mustudentsync.model.RegistrationDraftEntity;
import com.example.mustudentsync.model.StudentEntity;

import java.util.List;

public interface StudentRepository {

    LiveData<List<StudentEntity>> getRoster(String searchQuery, String program, String group);

    LiveData<StudentEntity> getStudent(String studentId);

    /** Off-main-thread group capacity check (max 15 active students per group). */
    void isGroupFullAsync(String labGroup, GroupFullCallback callback);

    void saveStudent(StudentEntity student);

    void deleteStudent(StudentEntity student);

    void saveRegistrationDraft(RegistrationDraftEntity draft);

    void loadRegistrationDraft(DraftLoadedCallback callback);

    void clearRegistrationDraft();

    interface DraftLoadedCallback {
        void onDraftLoaded(RegistrationDraftEntity draft);
    }

    interface GroupFullCallback {
        void onResult(boolean isFull);
    }
}