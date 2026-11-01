package com.example.mustudentsync;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.data.local.GroupCount;
import com.example.mustudentsync.util.GroupRows;
import com.example.mustudentsync.util.LecturerNav;
import com.example.mustudentsync.util.SessionManager;
import com.example.mustudentsync.viewmodel.GroupStatsViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecturer Groups tab: every lab group with its fill level. Tap one to see its students.
 * "Share summary" sends the group labels and counts through the Android Sharesheet.
 */
public class GroupsActivity extends AppCompatActivity {

    private List<GroupCount> latestCounts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);

        LecturerNav.setup(this, findViewById(R.id.bottomNav), R.id.nav_groups);

        LinearLayout list = findViewById(R.id.listAllGroups);
        new ViewModelProvider(this).get(GroupStatsViewModel.class)
                .getGroupCounts()
                .observe(this, counts -> {
                    latestCounts = counts != null ? counts : new ArrayList<>();
                    GroupRows.render(list, GroupRows.build(this, counts), this::openGroup);
                });

        findViewById(R.id.btnShareGroupSummary).setOnClickListener(v -> shareSummary());
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
