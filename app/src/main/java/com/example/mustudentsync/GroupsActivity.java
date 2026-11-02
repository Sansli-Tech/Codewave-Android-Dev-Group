package com.example.mustudentsync;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import com.example.mustudentsync.data.local.GroupCount;
import com.example.mustudentsync.util.GroupRows;
import com.example.mustudentsync.util.LecturerNav;
import com.example.mustudentsync.util.SessionManager;
import com.example.mustudentsync.viewmodel.GroupAdminViewModel;
import com.example.mustudentsync.viewmodel.GroupStatsViewModel;

import java.io.File;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecturer Groups tab: every lab group with its fill level. Tap one to see its students.
 * "Share summary" sends the group labels and counts through the Android Sharesheet.
 * Group management (lecturer request): New group, Auto-assign unassigned students
 * (groups of up to 15) and Export to Excel (.xlsx shared through the Sharesheet).
 */
public class GroupsActivity extends AppCompatActivity {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private List<GroupCount> latestCounts = new ArrayList<>();
    private LinearLayout list;
    private GroupAdminViewModel admin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);

        LecturerNav.setup(this, findViewById(R.id.bottomNav), R.id.nav_groups);

        list = findViewById(R.id.listAllGroups);
        new ViewModelProvider(this).get(GroupStatsViewModel.class)
                .getGroupCounts()
                .observe(this, counts -> {
                    latestCounts = counts != null ? counts : new ArrayList<>();
                    GroupRows.render(list, GroupRows.build(this, counts), this::openGroup);
                });

        findViewById(R.id.btnShareGroupSummary).setOnClickListener(v -> shareSummary());

        admin = new ViewModelProvider(this).get(GroupAdminViewModel.class);
        View newGroup = findViewById(R.id.btnNewGroup);
        View autoAssign = findViewById(R.id.btnAutoAssign);
        View export = findViewById(R.id.btnExportExcel);
        View progress = findViewById(R.id.progressGroupAdmin);
        newGroup.setOnClickListener(v -> confirmNewGroup());
        autoAssign.setOnClickListener(v -> admin.previewAutoAssign());
        export.setOnClickListener(v -> admin.exportToExcel());

        admin.isBusy().observe(this, isBusy -> {
            boolean b = Boolean.TRUE.equals(isBusy);
            progress.setVisibility(b ? View.VISIBLE : View.GONE);
            newGroup.setEnabled(!b);
            autoAssign.setEnabled(!b);
            export.setEnabled(!b);
        });
        admin.getEvents().observe(this, event -> {
            if (event == null || !event.take()) return;
            switch (event.kind) {
                case PREVIEW:
                    confirmAutoAssign(event.text);
                    break;
                case EXPORTED:
                    shareExcel(event.file);
                    break;
                default:
                    redrawGroups();   // a new group may have been added to the list
                    Snackbar.make(list, event.text, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void redrawGroups() {
        GroupRows.render(list, GroupRows.build(this, latestCounts), this::openGroup);
    }

    private void confirmNewGroup() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.group_new_title)
                .setMessage(R.string.group_new_message)
                .setPositiveButton(R.string.group_new_confirm, (d, w) -> admin.createGroup())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmAutoAssign(String plan) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.group_auto_title)
                .setMessage(plan)
                .setPositiveButton(R.string.group_auto_confirm, (d, w) -> admin.autoAssign())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
    }

    /**
     * Shares the .xlsx through the Sharesheet (WhatsApp, Gmail, Drive, Files...). The file is
     * offered through a FileProvider content:// link with temporary read permission, so no
     * storage permission is needed and no other app can read the app's files.
     */
    private void shareExcel(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(XLSX_MIME);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.group_export_subject));
        send.putExtra(Intent.EXTRA_TITLE, file.getName());
        send.setClipData(ClipData.newRawUri(file.getName(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.group_export_chooser)));
    }

    /**
     * Unit 7 Sharesheet: an implicit ACTION_SEND intent with plain text, wrapped in
     * createChooser so the person picks the app every time. Only group labels and numbers
     * are shared - no names, student numbers or any other personal data - and no
     * permission is needed. Cancelling the Sharesheet sends nothing and changes nothing.
     */
    private void shareSummary() {
        List<GroupRows.Row> rows = GroupRows.build(this, latestCounts);
        StringBuilder text = new StringBuilder(getString(R.string.share_summary_heading)).append('\n');

        long lastSync = new SessionManager(this).getLastSync();
        text.append(lastSync > 0
                ? getString(R.string.share_summary_as_of, DateUtils.getRelativeTimeSpanString(lastSync))
                : getString(R.string.share_summary_not_synced)).append("\n\n");

        int total = 0;
        for (GroupRows.Row row : rows) {
            text.append(getString(row.isFull() ? R.string.share_summary_row_full : R.string.share_summary_row,
                    row.code, row.count, row.capacity)).append('\n');
            total += row.count;
        }
        int unassigned = 0;
        for (GroupCount c : latestCounts) {
            if ("Unassigned".equals(c.labGroup)) unassigned = c.total;
        }
        text.append(getString(R.string.share_summary_unassigned, unassigned)).append('\n');
        text.append(getString(R.string.share_summary_total, total + unassigned));

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_summary_heading));
        send.putExtra(Intent.EXTRA_TEXT, text.toString());
        // Shown as the preview title in the Sharesheet (Android 10+).
        send.putExtra(Intent.EXTRA_TITLE, getString(R.string.share_summary_preview_title));
        startActivity(Intent.createChooser(send, getString(R.string.share_summary_chooser)));
    }

    private void openGroup(String groupCode) {
        // Opens the Roster tab filtered to this group (replacing this tab, so Back still goes Home).
        Intent i = new Intent(this, LecturerRosterActivity.class);
        i.putExtra(LecturerRosterActivity.EXTRA_GROUP_FILTER, groupCode);
        startActivity(i);
        finish();
        overridePendingTransition(0, 0);
    }
}
