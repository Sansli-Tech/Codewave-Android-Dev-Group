package com.example.mustudentsync.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mustudentsync.data.RoomStudentRepository;
import com.example.mustudentsync.model.StudentEntity;

public class StudentProfileViewModel extends AndroidViewModel {

    private static final String DEMO_STUDENT_ID = "demo-student-001";

    private final RoomStudentRepository repository;
    private String activeStudentId = DEMO_STUDENT_ID;
    private final MutableLiveData<Boolean> isPendingGroupChange = new MutableLiveData<>(false);

    public StudentProfileViewModel(@NonNull Application application) {
        super(application);
        this.repository = new RoomStudentRepository(application);
    }

    /** Call this before loadProfile(), with the intent extra (maybe null). */
    public void setStudentId(String studentId) {
        if (studentId != null && !studentId.isEmpty()) {
            this.activeStudentId = studentId;
        }
    }

    public void loadProfile(Runnable onReady) {
        if (DEMO_STUDENT_ID.equals(activeStudentId)) {
            repository.ensureDemoStudentExists(DEMO_STUDENT_ID, onReady);
        } else {
            onReady.run(); // a real student was already saved by RegisterActivity
        }
    }

    public LiveData<StudentEntity> getStudent() {
        return repository.getStudent(activeStudentId);
    }

    public LiveData<Boolean> isPendingGroupChange() {
        return isPendingGroupChange;
    }

    public void updateProgram(StudentEntity current, String newProgram) {
        if (current == null) return;
        current.program = newProgram;
        repository.saveStudent(current);
    }

    public void submitGroupChangeRequest() {
        isPendingGroupChange.setValue(true);
    }
}