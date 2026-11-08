package com.example.mustudentsync;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.data.SyncManager;
import com.example.mustudentsync.util.GroupCatalog;
import com.example.mustudentsync.util.InboxRows;
import com.example.mustudentsync.viewmodel.InboxViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecturer inbox (opened from the bell on Home):
 *  - group requests waiting for a decision (Approve moves the student; Reject tells them why);
 *  - student-number corrections waiting for a decision;
 *  - Send announcement (all students, or one group);
 *  - recent notifications (opening the screen marks them read).
 */
public class LecturerInboxActivity extends AppCompatActivity {

    private InboxViewModel vm;
    private LinearLayout listRequests, listNotifications;
    private TextView tvOffline;
    private List<JsonObject> groupRequests = new ArrayList<>();
    private List<JsonObject> corrections = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lecturer_inbox);
        MaterialToolbar toolbar = findViewById(R.id.toolbarInbox);
        toolbar.setNavigationOnClickListener(v -> finish());

        listRequests = findViewById(R.id.listInboxRequests);
        listNotifications = findViewById(R.id.listInboxNotifications);
        tvOffline = findViewById(R.id.tvInboxOffline);
        View progress = findViewById(R.id.progressInbox);

        vm = new ViewModelProvider(this).get(InboxViewModel.class);
        vm.getGroupRequests().observe(this, r -> {
            groupRequests = r != null ? r : new ArrayList<>();
            renderRequests();
        });
        vm.getCorrections().observe(this, c -> {
            corrections = c != null ? c : new ArrayList<>();
            renderRequests();
        });
        vm.getNotifications().observe(this, this::renderNotifications);
        vm.isLoading().observe(this, l -> progress.setVisibility(Boolean.TRUE.equals(l) ? View.VISIBLE : View.GONE));
        vm.isOffline().observe(this, off -> tvOffline.setVisibility(Boolean.TRUE.equals(off) ? View.VISIBLE : View.GONE));
        vm.getEvents().observe(this, e -> {
            if (e == null || !e.take()) return;
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show();
            if (e.ok) new SyncManager(this).syncNow((ok, msg) -> { });   // roster shows the move
        });

        findViewById(R.id.btnInboxAnnounce).setOnClickListener(v -> showAnnouncementDialog());
        findViewById(R.id.btnInboxRefresh).setOnClickListener(v -> reload());
        if (savedInstanceState == null) reload();
    }

    private void reload() {
        vm.loadRequests();
        vm.loadNotifications();
    }

    // ------------------------------------------------------------------ requests

    private void renderRequests() {
        listRequests.removeAllViews();
        ((TextView) findViewById(R.id.tvInboxRequestsTitle)).setText(
                getString(R.string.inbox_requests_title, groupRequests.size() + corrections.size()));
        if (groupRequests.isEmpty() && corrections.isEmpty()) {
            InboxRows.empty(listRequests, getString(R.string.inbox_requests_empty));
            return;
        }
        for (JsonObject r : groupRequests) {
            String id = InboxViewModel.str(r, "request_id", "");
            String name = InboxViewModel.str(r, "student_name", "");
            String number = InboxViewModel.str(r, "student_number", "");
            String wanted = InboxViewModel.str(r, "requested_group", "");
            String current = InboxViewModel.str(r, "current_group", null);
            int active = r.has("active_students") && !r.get("active_students").isJsonNull() ? r.get("active_students").getAsInt() : 0;
            int capacity = r.has("capacity") && !r.get("capacity").isJsonNull() ? r.get("capacity").getAsInt() : 0;
            String body = getString(R.string.inbox_group_request_body, name, number,
                    current != null ? current : getString(R.string.student_home_no_group), wanted, active, capacity);
            boolean full = capacity > 0 && active >= capacity;
            InboxRows.add(listRequests, getString(R.string.inbox_group_request_title, wanted),
                    full ? body + "\n" + getString(R.string.inbox_group_full_warning) : body,
                    InboxRows.when(InboxViewModel.str(r, "created_at", null)), false,
                    getString(R.string.inbox_approve), () -> confirm(true, name, wanted,
                            note -> vm.resolveGroupRequest(id, true, note)),
                    getString(R.string.inbox_reject), () -> confirm(false, name, wanted,
                            note -> vm.resolveGroupRequest(id, false, note)));
        }
        for (JsonObject r : corrections) {
            String id = InboxViewModel.str(r, "request_id", "");
            String name = InboxViewModel.str(r, "student_name", "");
            String from = InboxViewModel.str(r, "current_number", "");
            String to = InboxViewModel.str(r, "requested_number", "");
            InboxRows.add(listRequests, getString(R.string.inbox_correction_title),
                    getString(R.string.inbox_correction_body, name, from, to),
                    InboxRows.when(InboxViewModel.str(r, "created_at", null)), false,
                    getString(R.string.inbox_approve), () -> confirm(true, name, to,
                            note -> vm.resolveCorrection(id, true, note)),
                    getString(R.string.inbox_reject), () -> confirm(false, name, to,
                            note -> vm.resolveCorrection(id, false, note)));
        }
    }

    private interface WithNote {
        void go(String note);
    }

    /** Approve / reject with an optional note that the student sees. */
    private void confirm(boolean approve, String name, String what, WithNote action) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_note, null);
        TextInputEditText note = view.findViewById(R.id.etDialogNote);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(approve ? R.string.inbox_approve_title : R.string.inbox_reject_title, name, what))
                .setView(view)
                .setPositiveButton(approve ? R.string.inbox_approve : R.string.inbox_reject, (d, w) ->
                        action.go(note.getText() != null ? note.getText().toString() : null))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ announcements

    private void showAnnouncementDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_announcement, null);
        TextInputLayout tilTitle = view.findViewById(R.id.tilAnnouncementTitle);
        TextInputLayout tilBody = view.findViewById(R.id.tilAnnouncementBody);
        TextInputEditText etTitle = view.findViewById(R.id.etAnnouncementTitle);
        TextInputEditText etBody = view.findViewById(R.id.etAnnouncementBody);
        Spinner audience = view.findViewById(R.id.spinnerAnnouncementAudience);

        List<String> choices = new ArrayList<>();
        choices.add(getString(R.string.announce_audience_all));
        List<String> codes = GroupCatalog.codes(this);
        choices.addAll(codes);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audience.setAdapter(adapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.announce_title)
                .setView(view)
                .setPositiveButton(R.string.announce_send, null)   // set below, so errors keep it open
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
            String body = etBody.getText() != null ? etBody.getText().toString().trim() : "";
            tilTitle.setError(title.length() < 3 || title.length() > 120 ? getString(R.string.announce_title_error) : null);
            tilBody.setError(body.isEmpty() || body.length() > 1000 ? getString(R.string.announce_body_error) : null);
            if (tilTitle.getError() != null || tilBody.getError() != null) return;
            int pick = audience.getSelectedItemPosition();
            String target = pick <= 0 ? "ALL" : codes.get(pick - 1);
            String who = pick <= 0 ? getString(R.string.announce_audience_all) : target;
            new MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.announce_confirm, who))
                    .setPositiveButton(R.string.announce_send, (c, w) -> {
                        vm.sendAnnouncement(title, body, target);
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        }));
        dialog.show();
    }

    // ------------------------------------------------------------------ notifications

    private void renderNotifications(List<JsonObject> items) {
        listNotifications.removeAllViews();
        if (items == null || items.isEmpty()) {
            InboxRows.empty(listNotifications, getString(R.string.notifications_empty));
            return;
        }
        for (JsonObject n : items) {
            InboxRows.add(listNotifications, InboxViewModel.str(n, "title", ""), InboxViewModel.str(n, "body", ""),
                    InboxRows.when(InboxViewModel.str(n, "created_at", null)),
                    InboxViewModel.str(n, "read_at", null) == null, null, null, null, null);
        }
    }
}
