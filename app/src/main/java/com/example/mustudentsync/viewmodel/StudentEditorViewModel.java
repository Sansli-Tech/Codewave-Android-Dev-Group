package com.example.mustudentsync.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.example.mustudentsync.data.RoomStudentRepository;
import com.example.mustudentsync.data.StudentRepository;
import com.example.mustudentsync.model.StudentEntity;

import java.util.UUID;

public class StudentEditorViewModel extends AndroidViewModel {

    private final StudentRepository repository;

    public StudentEditorViewModel(@NonNull Application application) {
        super(application);
        this.repository = new RoomStudentRepository(application);
    }

    /** Result arrives on the main thread via callback — never blocks the UI thread. */
    public void isGroupFull(String labGroup, StudentRepository.GroupFullCallback callback) {
        repository.isGroupFullAsync(labGroup, callback);
    }

    public void saveNewStudent(String fullName, String studentNumber, String program, String labGroup) {
        String immutableId = UUID.randomUUID().toString();
        StudentEntity entity = new StudentEntity(immutableId, studentNumber, fullName, program, labGroup);
        repository.saveStudent(entity);
    }

    public void updateExistingStudent(StudentEntity existing, String fullName, String studentNumber,
                                      String program, String labGroup) {
        existing.fullName = fullName;
        existing.studentNumber = studentNumber;
        existing.program = program;
        existing.labGroup = labGroup;
        existing.recordVersion += 1;
        repository.saveStudent(existing);
    }
}