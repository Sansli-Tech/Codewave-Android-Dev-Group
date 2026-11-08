package com.example.mustudentsync.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.network.dto.GroupListResponse;
import com.example.mustudentsync.util.GroupCatalog;
import com.example.mustudentsync.util.SessionManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Live group occupancy for the student Groups tab. Kept in a ViewModel so rotation doesn't refetch. */
public class StudentGroupsViewModel extends AndroidViewModel {

    public enum Status { LOADING, ONLINE, OFFLINE, SESSION_EXPIRED }

    private final MutableLiveData<List<GroupListResponse.Group>> groups = new MutableLiveData<>();
    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.LOADING);
    private final SessionManager session;
    private Call<GroupListResponse> inFlight;

    public StudentGroupsViewModel(@NonNull Application application) {
        super(application);
        session = new SessionManager(application);
        refresh();
    }

    public LiveData<List<GroupListResponse.Group>> getGroups() { return groups; }

    public LiveData<Status> getStatus() { return status; }

    public void refresh() {
        String bearer = session.getBearerToken();
        if (bearer == null) {
            status.setValue(Status.SESSION_EXPIRED);
            return;
        }
        if (inFlight != null) inFlight.cancel();
        status.setValue(Status.LOADING);
        inFlight = RetrofitClient.getApiService().getGroups(bearer);
        inFlight.enqueue(new Callback<GroupListResponse>() {
            @Override
            public void onResponse(@NonNull Call<GroupListResponse> call, @NonNull Response<GroupListResponse> res) {
                if (res.code() == 401) {
                    status.setValue(Status.SESSION_EXPIRED);
                } else if (res.isSuccessful() && res.body() != null && res.body().groups != null) {
                    GroupCatalog.saveFromServer(getApplication(), res.body());
                    groups.setValue(res.body().groups);
                    status.setValue(Status.ONLINE);
                } else {
                    status.setValue(Status.OFFLINE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<GroupListResponse> call, @NonNull Throwable t) {
                if (!call.isCanceled()) status.setValue(Status.OFFLINE);
            }
        });
    }

    @Override
    protected void onCleared() {
        if (inFlight != null) inFlight.cancel();
    }
}
