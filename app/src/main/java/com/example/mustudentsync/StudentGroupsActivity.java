package com.example.mustudentsync;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.model.SyncState;
import com.example.mustudentsync.network.dto.GroupListResponse;
import com.example.mustudentsync.util.GroupRows;
import com.example.mustudentsync.util.SessionManager;
import com.example.mustudentsync.util.StudentNav;
import com.example.mustudentsync.viewmodel.StudentGroupsViewModel;
import com.example.mustudentsync.viewmodel.StudentProfileViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Student Groups tab: every lab group with live occupancy from the server.
 * Tapping a group with free places asks for a place. The request goes through the same offline
 * outbox as every other change. Once it reaches the server it waits for a lecturer, who approves
 * (the student moves) or rejects it; the student is told in Notifications.
 * Students see counts only, never other students' names.
 */
public class StudentGroupsActivity extends AppCompatActivity {

    private StudentProfileViewModel profile;
    private StudentGroupsViewModel groupsVm;
    private TextView tvMine, tvStatus;
    private LinearLayout list;
    private View progress, retry;
    private StudentEntity me;
    private StudentProfileViewModel.ServerInfo server;
    private List<GroupListResponse.Group> latest = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_groups);
        StudentNav.setup(this, findViewById(R.id.bottomNavStudent), R.id.nav_student_groups);

        tvMine = findViewById(R.id.tvStudentGroupsMine);
        tvStatus = findViewById(R.id.tvStudentGroupsStatus);
        list = findViewById(R.id.listStudentGroups);
        progress = findViewById(R.id.progressStudentGroups);
        retry = findViewById(R.id.btnStudentGroupsRetry);

        String studentId = getIntent().getStringExtra("STUDENT_ID");
        if (studentId == null) studentId = new SessionManager(this).getStudentId();
        profile = new ViewModelProvider(this).get(StudentProfileViewModel.class);
        profile.setStudentId(studentId);
        groupsVm = new ViewModelProvider(this).get(StudentGroupsViewModel.class);

        profile.getStudent().observe(this, s -> {
            me = s;
            showMine();
        });
        groupsVm.getGroups().observe(this, g -> {
            latest = g != null ? g : new ArrayList<>();
            render();
        });
        groupsVm.getStatus().observe(this, this::showStatus);
        profile.getServerInfo().observe(this, info -> {
            server = info;
            showMine();
        });
        tvMine.setOnClickListener(v -> offerCancel());
        retry.setOnClickListener(v -> groupsVm.refresh());
    }

    @Override
    protected void onResume() {
        super.onResume();
        groupsVm.refresh();   // occupancy changes all the time
        profile.refreshServerInfo();   // request status (waiting / approved / rejected)
    }

    private void showStatus(StudentGroupsViewModel.Status s) {
        progress.setVisibility(s == StudentGroupsViewModel.Status.LOADING ? View.VISIBLE : View.GONE);
        retry.setVisibility(s == StudentGroupsViewModel.Status.OFFLINE ? View.VISIBLE : View.GONE);
        switch (s) {
            case ONLINE: tvStatus.setText(R.string.student_groups_online); break;
            case OFFLINE: tvStatus.setText(R.string.student_groups_offline); break;
            case SESSION_EXPIRED: tvStatus.setText(R.string.roster_source_expired); break;
            default: tvStatus.setText(R.string.student_groups_loading);
        }
    }

    private void render() {
        List<GroupRows.Row> rows = new ArrayList<>();
        for (GroupListResponse.Group g : latest) {
            rows.add(GroupRows.Row.of(g.groupCode, g.activeStudents, g.capacity));
        }
        GroupRows.render(list, rows, this::onGroupTapped);
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String myGroup() {
        if (me == null || me.labGroup == null || "Unassigned".equals(me.labGroup) || me.labGroup.isEmpty()) return null;
        return me.labGroup;
    }

    /** A request the lecturer hasn't answered yet (on the server). */
    private String waitingOnServer() {
        return server != null && server.online && "PENDING".equals(server.groupRequestStatus)
                ? server.groupRequestCode : null;
    }

    /** Not sent yet (this phone), or sent and waiting for the lecturer. */
    private String pendingRequest() {
        String local = unsentRequest();
        return local != null ? local : waitingOnServer();
    }

    private String unsentRequest() {
        if (me == null || me.pendingJson == null || SyncState.SYNCED.equals(me.syncState)) return null;
        try {
            JsonObject o = JsonParser.parseString(me.pendingJson).getAsJsonObject();
            return o.has("group_code") && !o.get("group_code").isJsonNull() ? o.get("group_code").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void showMine() {
        String mine = myGroup();
        String text = mine != null ? getString(R.string.student_groups_mine, mine)
                : getString(R.string.student_groups_mine_none);
        String unsent = unsentRequest();
        String waiting = waitingOnServer();
        if (unsent != null) {
            text += "\n" + getString(R.string.student_groups_requested, unsent);
        } else if (waiting != null) {
            text += "\n" + getString(R.string.student_groups_waiting_lecturer, waiting);
        } else if (server != null && "REJECTED".equals(server.groupRequestStatus)) {
            text += "\n" + getString(R.string.student_groups_rejected, server.groupRequestCode)
                    + (server.groupRequestNote != null ? " " + server.groupRequestNote : "");
        }
        if (me != null && SyncState.ACTION_REQUIRED.equals(me.syncState) && me.syncMessage != null) {
            text += "\n" + me.syncMessage;
        }
        tvMine.setText(text);
    }

    private void onGroupTapped(String code) {
        if (code.equals(myGroup())) {
            Toast.makeText(this, getString(R.string.student_groups_already, code), Toast.LENGTH_SHORT).show();
            return;
        }
        if (pendingRequest() != null) {
            Toast.makeText(this, R.string.student_groups_wait, Toast.LENGTH_LONG).show();
            return;
        }
        GroupListResponse.Group g = find(code);
        if (g != null && g.activeStudents >= g.capacity) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.student_groups_full_title, code))
                    .setMessage(R.string.student_groups_full_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        String places = g != null ? getString(R.string.student_groups_places_left, g.capacity - g.activeStudents) : "";
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.student_groups_confirm_title, code))
                .setMessage(getString(R.string.student_groups_confirm_message, places))
                .setPositiveButton(R.string.student_groups_confirm, (d, w) ->
                        profile.requestGroup(code, (ok, message) ->
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /** Tap the "my group" card while a request waits for the lecturer: offer to withdraw it. */
    private void offerCancel() {
        String waiting = waitingOnServer();
        if (waiting == null || unsentRequest() != null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.student_groups_cancel_title, waiting))
                .setMessage(R.string.student_groups_cancel_message)
                .setPositiveButton(R.string.student_groups_cancel_yes, (d, w) ->
                        profile.cancelGroupRequest((ok, message) ->
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private GroupListResponse.Group find(String code) {
        for (GroupListResponse.Group g : latest) if (code.equals(g.groupCode)) return g;
        return null;
    }
}
