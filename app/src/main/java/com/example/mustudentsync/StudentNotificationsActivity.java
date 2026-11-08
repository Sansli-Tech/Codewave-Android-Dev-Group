package com.example.mustudentsync;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.util.InboxRows;
import com.example.mustudentsync.util.StudentNav;
import com.example.mustudentsync.viewmodel.InboxViewModel;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Student Notifications tab: announcements from lecturers and answers to the student's requests
 * (group place, number correction). Opening the tab marks them read; new ones stay highlighted
 * until the student leaves the tab.
 */
public class StudentNotificationsActivity extends AppCompatActivity {

    private InboxViewModel vm;
    private LinearLayout list;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_notifications);
        StudentNav.setup(this, findViewById(R.id.bottomNavStudent), R.id.nav_student_notifications);

        list = findViewById(R.id.listStudentNotifications);
        tvStatus = findViewById(R.id.tvStudentNotificationsStatus);
        View progress = findViewById(R.id.progressStudentNotifications);
        View retry = findViewById(R.id.btnStudentNotificationsRetry);

        vm = new ViewModelProvider(this).get(InboxViewModel.class);
        vm.getNotifications().observe(this, this::render);
        vm.isLoading().observe(this, l -> progress.setVisibility(Boolean.TRUE.equals(l) ? View.VISIBLE : View.GONE));
        vm.isOffline().observe(this, off -> {
            boolean o = Boolean.TRUE.equals(off);
            retry.setVisibility(o ? View.VISIBLE : View.GONE);
            tvStatus.setVisibility(o ? View.VISIBLE : View.GONE);
        });
        retry.setOnClickListener(v -> vm.loadNotifications());
        if (savedInstanceState == null) vm.loadNotifications();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        vm.loadNotifications();   // back from another app: something new may have arrived
    }

    private void render(List<JsonObject> items) {
        list.removeAllViews();
        if (items == null || items.isEmpty()) {
            InboxRows.empty(list, getString(R.string.notifications_empty));
            return;
        }
        for (JsonObject n : items) {
            String kind = InboxViewModel.str(n, "kind", "");
            String meta = ("ANNOUNCEMENT".equals(kind) ? getString(R.string.notifications_kind_announcement) + " · " : "")
                    + InboxRows.when(InboxViewModel.str(n, "created_at", null));
            InboxRows.add(list, InboxViewModel.str(n, "title", ""), InboxViewModel.str(n, "body", ""), meta,
                    InboxViewModel.str(n, "read_at", null) == null, null, null, null, null);
        }
    }
}
