package com.example.mustudentsync;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.data.AccountManager;
import com.example.mustudentsync.data.SyncManager;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.model.SyncState;
import com.example.mustudentsync.util.BackToExit;
import com.example.mustudentsync.util.SessionManager;
import com.example.mustudentsync.util.StudentNav;
import com.example.mustudentsync.viewmodel.StudentProfileViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Calendar;

/**
 * Student Home tab (landing page after sign-in): my group and how full it is, anything
 * waiting for the server, a reminder to add a recovery e-mail, and quick actions.
 * Reads this phone's copy (Room) at once; the live occupancy comes from the server when online.
 */
public class StudentHomeActivity extends AppCompatActivity {

    private StudentProfileViewModel viewModel;
    private TextView tvName, chipSync, tvGroupCode, tvOccupancy, tvPending, tvRecovery;
    private LinearProgressIndicator bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_home);

        StudentNav.setup(this, findViewById(R.id.bottomNavStudent), R.id.nav_student_home);
        BackToExit.install(this);

        tvName = findViewById(R.id.tvStudentHomeName);
        chipSync = findViewById(R.id.chipStudentSync);
        tvGroupCode = findViewById(R.id.tvHomeGroupCode);
        tvOccupancy = findViewById(R.id.tvHomeGroupOccupancy);
        tvPending = findViewById(R.id.tvHomePending);
        tvRecovery = findViewById(R.id.tvHomeRecoveryReminder);
        bar = findViewById(R.id.barHomeGroup);
        ((TextView) findViewById(R.id.tvStudentGreeting)).setText(greeting());

        String studentId = getIntent().getStringExtra("STUDENT_ID");
        if (studentId == null) studentId = new SessionManager(this).getStudentId();
        viewModel = new ViewModelProvider(this).get(StudentProfileViewModel.class);
        viewModel.setStudentId(studentId);
        viewModel.getStudent().observe(this, this::showStudent);
        viewModel.getServerInfo().observe(this, this::showServerInfo);

        findViewById(R.id.btnHomeChooseGroup).setOnClickListener(v ->
                StudentNav.open(this, R.id.nav_student_groups, R.id.nav_student_home));
        findViewById(R.id.btnHomeMyDetails).setOnClickListener(v ->
                StudentNav.open(this, R.id.nav_student_profile, R.id.nav_student_home));
        tvRecovery.setOnClickListener(v ->
                StudentNav.open(this, R.id.nav_student_profile, R.id.nav_student_home));
        findViewById(R.id.btnHomeSync).setOnClickListener(v -> {
            Toast.makeText(this, R.string.sync_in_progress, Toast.LENGTH_SHORT).show();
            new SyncManager(this).syncNow((ok, msg) -> {
                viewModel.refreshServerInfo();
                Toast.makeText(this, ok ? getString(R.string.sync_done) : msg, Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Fresh data each time Home is shown: pull changes, live occupancy, recovery e-mail.
        new SyncManager(this).syncNow((ok, msg) -> viewModel.refreshServerInfo());
        viewModel.refreshServerInfo();
        new AccountManager(this).getRecoveryEmail((ok, email) -> {
            if (isFinishing()) return;
            // Only remind when the server answered and has no address (offline: say nothing).
            tvRecovery.setVisibility(ok && (email == null || email.isEmpty()) ? View.VISIBLE : View.GONE);
        });
    }

    private void showStudent(StudentEntity s) {
        if (s == null) return;
        tvName.setText(s.fullName);

        boolean assigned = s.labGroup != null && !s.labGroup.isEmpty() && !"Unassigned".equals(s.labGroup);
        tvGroupCode.setText(assigned ? s.labGroup : getString(R.string.student_home_no_group));
        cardDescription();

        String state = s.syncState != null ? s.syncState : SyncState.SYNCED;
        boolean synced = SyncState.SYNCED.equals(state);
        boolean attention = SyncState.ACTION_REQUIRED.equals(state);
        chipSync.setText(labelFor(state));
        chipSync.setBackgroundResource(synced ? R.drawable.bg_campus_chip_ok : R.drawable.bg_campus_chip_warn);
        chipSync.setTextColor(ContextCompat.getColor(this, synced ? R.color.campus_ok_text : R.color.campus_warn_text));

        // What is waiting: a refused change (with the reason), or a group request not yet confirmed.
        JsonObject pending = pending(s);
        String waiting = null;
        if (attention) {
            waiting = getString(R.string.student_home_attention,
                    s.syncMessage != null ? s.syncMessage : getString(R.string.sync_state_action_required));
        } else if (!synced && pending != null && pending.has("group_code")) {
            waiting = getString(R.string.student_home_group_pending, pending.get("group_code").getAsString(), labelFor(state));
        } else if (!synced) {
            waiting = getString(R.string.student_home_changes_pending, labelFor(state));
        }
        tvPending.setVisibility(waiting != null ? View.VISIBLE : View.GONE);
        if (waiting != null) tvPending.setText(waiting);
        tvPending.setOnClickListener(attention
                ? v -> StudentNav.open(this, R.id.nav_student_profile, R.id.nav_student_home) : null);
    }

    private void showServerInfo(StudentProfileViewModel.ServerInfo info) {
        if (info == null) return;
        if (!info.online) {
            tvOccupancy.setText(R.string.student_home_offline);
            bar.setVisibility(View.GONE);
        } else if (info.groupCode == null) {
            tvGroupCode.setText(R.string.student_home_no_group);
            tvOccupancy.setText(R.string.student_home_unassigned_hint);
            bar.setVisibility(View.GONE);
        } else {
            tvGroupCode.setText(info.groupCode);
            tvOccupancy.setText(getString(R.string.student_home_occupancy, info.activeStudents, info.capacity));
            bar.setVisibility(View.VISIBLE);
            bar.setProgress(info.capacity > 0 ? Math.min(100, info.activeStudents * 100 / info.capacity) : 0);
        }
        cardDescription();
    }

    /** TalkBack reads the whole group card as one sentence. */
    private void cardDescription() {
        findViewById(R.id.cardMyGroup).setContentDescription(
                getString(R.string.student_home_group_label) + ": " + tvGroupCode.getText() + ". " + tvOccupancy.getText());
    }

    private String labelFor(String state) {
        switch (state) {
            case SyncState.SAVED_LOCALLY: return getString(R.string.sync_state_saved_locally);
            case SyncState.PENDING: return getString(R.string.sync_state_pending);
            case SyncState.SYNCING: return getString(R.string.sync_state_syncing);
            case SyncState.ACTION_REQUIRED: return getString(R.string.sync_state_action_required);
            default: return getString(R.string.chip_synced);
        }
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return getString(R.string.greeting_morning);
        if (hour < 17) return getString(R.string.greeting_afternoon);
        return getString(R.string.greeting_evening);
    }

    private static JsonObject pending(StudentEntity s) {
        if (s.pendingJson == null) return null;
        try {
            return JsonParser.parseString(s.pendingJson).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
