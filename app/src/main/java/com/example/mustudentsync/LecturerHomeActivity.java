package com.example.mustudentsync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.data.SyncManager;
import com.example.mustudentsync.data.local.GroupCount;
import com.example.mustudentsync.util.BackToExit;
import com.example.mustudentsync.util.GroupCatalog;
import com.example.mustudentsync.util.GroupRows;
import com.example.mustudentsync.util.LecturerNav;
import com.example.mustudentsync.util.SessionManager;
import com.example.mustudentsync.viewmodel.GroupStatsViewModel;

import java.util.Calendar;
import java.util.List;

/** Lecturer Home tab: greeting, summary card, quick actions and the fullest groups. */
public class LecturerHomeActivity extends AppCompatActivity {

    private TextView tvGreeting, tvUsername, chipSyncState;
    private TextView tvStatStudents, tvStatGroups, tvStatUnassigned;
    private LinearLayout listHomeGroups;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lecturer_home);
        session = new SessionManager(this);

        tvGreeting = findViewById(R.id.tvGreeting);
        tvUsername = findViewById(R.id.tvUsername);
        chipSyncState = findViewById(R.id.chipSyncState);
        tvStatStudents = findViewById(R.id.tvStatStudents);
        tvStatGroups = findViewById(R.id.tvStatGroups);
        tvStatUnassigned = findViewById(R.id.tvStatUnassigned);
        listHomeGroups = findViewById(R.id.listHomeGroups);

        LecturerNav.setup(this, findViewById(R.id.bottomNav), R.id.nav_home);
        BackToExit.install(this);

        tvGreeting.setText(greeting());
        String name = session.getUsername();
        tvUsername.setText(name != null ? name : getString(R.string.nav_home));

        setupQuickActions();
        observeStats();
        findViewById(R.id.btnSeeAllGroups).setOnClickListener(v -> openTab(GroupsActivity.class));
        findViewById(R.id.btnLecturerHomeSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        com.example.mustudentsync.push.BellBadge.bind(this, findViewById(R.id.bellFrame),
                () -> startActivity(new Intent(this, LecturerInboxActivity.class)));
        com.example.mustudentsync.push.NotifyPermission.askOnce(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showSyncState();
        com.example.mustudentsync.push.NotificationCenter.refreshUnread(this);
        // Pull the latest changes each time Home is shown; the counts update by themselves.
        new SyncManager(this).syncNow((ok, msg) -> showSyncState());
    }

    private void setupQuickActions() {
        findViewById(R.id.qaAddStudent).setOnClickListener(v -> {
            Intent i = new Intent(this, StudentEditorActivity.class);
            i.putExtra("IS_EDIT_MODE", false);
            startActivity(i);
        });
        findViewById(R.id.qaClaimCode).setOnClickListener(v -> {
            Toast.makeText(this, R.string.claim_code_hint, Toast.LENGTH_LONG).show();
            openTab(LecturerRosterActivity.class);
        });
        findViewById(R.id.qaSearch).setOnClickListener(v -> {
            Intent i = new Intent(this, LecturerRosterActivity.class);
            i.putExtra(LecturerRosterActivity.EXTRA_FOCUS_SEARCH, true);
            startActivity(i);
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.qaSyncNow).setOnClickListener(v -> {
            Toast.makeText(this, R.string.sync_in_progress, Toast.LENGTH_SHORT).show();
            new SyncManager(this).syncNow((ok, msg) -> {
                showSyncState();
                Toast.makeText(this, ok ? getString(R.string.sync_done) : msg, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void observeStats() {
        GroupStatsViewModel vm = new ViewModelProvider(this).get(GroupStatsViewModel.class);

        vm.getActiveCount().observe(this, n -> tvStatStudents.setText(String.valueOf(n != null ? n : 0)));

        vm.getGroupCounts().observe(this, counts -> {
            tvStatGroups.setText(String.valueOf(GroupCatalog.codes(this).size()));
            tvStatUnassigned.setText(String.valueOf(unassigned(counts)));

            List<GroupRows.Row> rows = GroupRows.build(this, counts);
            GroupRows.render(listHomeGroups, GroupRows.fullestFirst(rows, 3), this::openGroup);
        });
    }

    private static int unassigned(List<GroupCount> counts) {
        if (counts == null) return 0;
        for (GroupCount c : counts) {
            if (c.labGroup == null || "Unassigned".equals(c.labGroup)) return c.total;
        }
        return 0;
    }

    private void openGroup(String groupCode) {
        Intent i = new Intent(this, LecturerRosterActivity.class);
        i.putExtra(LecturerRosterActivity.EXTRA_GROUP_FILTER, groupCode);
        startActivity(i);
        overridePendingTransition(0, 0);
    }

    private void openTab(Class<?> screen) {
        startActivity(new Intent(this, screen));
        overridePendingTransition(0, 0);
    }

    private void showSyncState() {
        boolean synced = session.getLastSync() > 0;
        chipSyncState.setText(synced ? R.string.chip_synced : R.string.chip_not_synced);
        chipSyncState.setBackgroundResource(synced ? R.drawable.bg_campus_chip_ok : R.drawable.bg_campus_chip_warn);
        chipSyncState.setTextColor(ContextCompat.getColor(this,
                synced ? R.color.campus_ok_text : R.color.campus_warn_text));
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.greeting_morning);
        if (hour < 17) return getString(R.string.greeting_afternoon);
        return getString(R.string.greeting_evening);
    }
}
