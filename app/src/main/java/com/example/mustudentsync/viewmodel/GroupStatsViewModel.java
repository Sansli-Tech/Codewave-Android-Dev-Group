package com.example.mustudentsync.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.mustudentsync.data.RoomStudentRepository;
import com.example.mustudentsync.data.local.GroupCount;

import java.util.List;

/** Live counts from Room for the Home, Groups and Sync tabs. Updates by itself after every sync. */
public class GroupStatsViewModel extends AndroidViewModel {

    private final LiveData<List<GroupCount>> groupCounts;
    private final LiveData<Integer> activeCount;

    public GroupStatsViewModel(@NonNull Application application) {
        super(application);
        RoomStudentRepository repository = new RoomStudentRepository(application);
        groupCounts = repository.getGroupCounts();
        activeCount = repository.countActive();
    }

    public LiveData<List<GroupCount>> getGroupCounts() { return groupCounts; }

    public LiveData<Integer> getActiveCount() { return activeCount; }
}
