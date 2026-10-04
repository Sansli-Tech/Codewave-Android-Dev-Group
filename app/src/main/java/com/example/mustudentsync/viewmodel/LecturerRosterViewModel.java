package com.example.mustudentsync.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mustudentsync.data.RoomStudentRepository;
import com.example.mustudentsync.data.StudentRepository;
import com.example.mustudentsync.model.StudentEntity;

import java.util.List;

public class LecturerRosterViewModel extends AndroidViewModel {

    private final StudentRepository repository;

    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> programFilter = new MutableLiveData<>("All Programs");
    private final MutableLiveData<String> groupFilter = new MutableLiveData<>("All Groups");

    private final MediatorLiveData<List<StudentEntity>> roster = new MediatorLiveData<>();

    public LecturerRosterViewModel(@NonNull Application application) {
        super(application);
        this.repository = new RoomStudentRepository(application);

        Runnable refresh = () -> {
            LiveData<List<StudentEntity>> source = repository.getRoster(
                    searchQuery.getValue(), programFilter.getValue(), groupFilter.getValue());
            roster.addSource(source, roster::setValue);
        };

        roster.addSource(searchQuery, q -> refresh.run());
        roster.addSource(programFilter, p -> refresh.run());
        roster.addSource(groupFilter, g -> refresh.run());
    }

    public LiveData<List<StudentEntity>> getRoster() {
        return roster;
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }

    public void setProgramFilter(String program) {
        programFilter.setValue(program);
    }

    public void setGroupFilter(String group) {
        groupFilter.setValue(group);
    }

    public String getCurrentSearchQuery() {
        return searchQuery.getValue();
    }

    /** Result arrives on the main thread via callback — never blocks the UI thread. */
    public void isGroupFull(String labGroup, StudentRepository.GroupFullCallback callback) {
        repository.isGroupFullAsync(labGroup, callback);
    }

    public void deleteStudent(StudentEntity student) {
        repository.deleteStudent(student);
    }
}