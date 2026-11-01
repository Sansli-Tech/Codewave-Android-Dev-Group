package com.example.mustudentsync;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.data.SyncManager;
import com.example.mustudentsync.util.LecturerNav;
import com.example.mustudentsync.util.SessionManager;
import com.example.mustudentsync.viewmodel.GroupStatsViewModel;
import com.google.android.material.button.MaterialButton;

/** Lecturer Sync tab: when the phone last synced, what it holds, and a Sync now button. */
public class SyncActivity extends AppCompatActivity {

    private TextView tvLastSync, tvSyncStatus;
    private MaterialButton btnSyncNow;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);
        session = new SessionManager(this);

        LecturerNav.setup(this, findViewById(R.id.bottomNav), R.id.nav_sync);

        tvLastSync = findViewById(R.id.tvLastSync);
        tvSyncStatus = findViewById(R.id.tvSyncStatus);
        btnSyncNow = findViewById(R.id.btnSyncNow);
        TextView tvLocalCount = findViewById(R.id.tvLocalCount);

        new ViewModelProvider(this).get(GroupStatsViewModel.class)
                .getActiveCount()
                .observe(this, n -> tvLocalCount.setText(String.valueOf(n != null ? n : 0)));

        btnSyncNow.setOnClickListener(v -> syncNow());
        showLastSync();
    }

    private void syncNow() {
        btnSyncNow.setEnabled(false);
        tvSyncStatus.setText(R.string.sync_in_progress);
        new SyncManager(this).syncNow((ok, msg) -> {
            btnSyncNow.setEnabled(true);
            tvSyncStatus.setText(ok ? getString(R.string.sync_done) : msg);
            showLastSync();
        });
    }

    private void showLastSync() {
        long last = session.getLastSync();
        tvLastSync.setText(last > 0
                ? DateUtils.getRelativeDateTimeString(this, last,
                        DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0)
                : getString(R.string.sync_never));
    }
}
