package com.example.mustudentsync.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.mustudentsync.data.RoomStudentRepository;
import com.example.mustudentsync.data.StudentRepository;
import com.example.mustudentsync.model.RegistrationDraftEntity;
import com.example.mustudentsync.model.StudentEntity;

import java.util.UUID;

public class RegisterViewModel extends AndroidViewModel {

    private final StudentRepository repository;

    public RegisterViewModel(@NonNull Application application) {
        super(application);
        this.repository = new RoomStudentRepository(application);
    }

    public void saveDraftToRoom(String fullName, String studentNumber, String program, String claimCode) {
        RegistrationDraftEntity draft = new RegistrationDraftEntity();
        draft.fullName = fullName;
        draft.studentNumber = studentNumber;
        draft.program = program;
        draft.claimCode = claimCode;
        repository.saveRegistrationDraft(draft);
    }

    public void loadDraftFromRoom(StudentRepository.DraftLoadedCallback callback) {
        repository.loadRegistrationDraft(callback);
    }

    public void clearDraft() {
        repository.clearRegistrationDraft();
    }

    /**
     * Actually creates and saves the new student record (this was missing before —
     * Register only navigated to Profile without writing anything to Room).
     * Returns the generated studentId so the caller can pass it to Profile.
     *
     * TODO (Activity C/D): replace with the real server registration call; this
     * local-only version exists so the UI has real data to show meanwhile.
     */
    public String registerNewStudent(String fullName, String studentNumber, String program) {
        String studentId = UUID.randomUUID().toString();
        StudentEntity entity = new StudentEntity(studentId, studentNumber, fullName, program, "Unassigned");
        repository.saveStudent(entity);
        return studentId;
    }
}