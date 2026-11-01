package com.example.mustudentsync;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.util.GroupRows;
import com.example.mustudentsync.util.LecturerNav;
import com.example.mustudentsync.viewmodel.GroupStatsViewModel;

/** Lecturer Groups tab: every lab group with its fill level. Tap one to see its students. */
public class GroupsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);

        LecturerNav.setup(this, findViewById(R.id.bottomNav), R.id.nav_groups);

        LinearLayout list = findViewById(R.id.listAllGroups);
        new ViewModelProvider(this).get(GroupStatsViewModel.class)
                .getGroupCounts()
                .observe(this, counts -> GroupRows.render(list, GroupRows.build(this, counts), this::openGroup));
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
