package com.example.mustudentsync.util;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mustudentsync.R;
import com.example.mustudentsync.StudentGroupsActivity;
import com.example.mustudentsync.StudentHomeActivity;
import com.example.mustudentsync.StudentNotificationsActivity;
import com.example.mustudentsync.StudentProfileActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * The student's bottom bar: Home, Groups, Notifications (with an unread badge), Profile. Works like the lecturer's (LecturerNav):
 * Home stays at the bottom of the back stack, so Back on any tab returns Home, and Back on
 * Home exits after "Press back again".
 */
public final class StudentNav {

    private StudentNav() { }

    public static void setup(AppCompatActivity activity, BottomNavigationView nav, int currentTabId) {
        nav.setSelectedItemId(currentTabId);
        // Unread badge on the Notifications tab; updated by pushes and on every tab.
        com.google.android.material.badge.BadgeDrawable badge =
                nav.getOrCreateBadge(R.id.nav_student_notifications);
        badge.setVisible(false);
        com.example.mustudentsync.push.NotificationCenter.unread().observe(activity, n -> {
            int count = n != null ? n : 0;
            badge.setNumber(count);
            badge.setVisible(count > 0);
        });
        com.example.mustudentsync.push.NotificationCenter.refreshUnread(activity);
        nav.setOnItemReselectedListener(item -> { /* already here */ });
        nav.setOnItemSelectedListener(item -> {
            int target = item.getItemId();
            if (target != currentTabId) open(activity, target, currentTabId);
            return false;   // the new screen highlights its own tab
        });
    }

    /** Opens a tab from code (e.g. a button on Home), with the same back-stack rules. */
    public static void open(AppCompatActivity activity, int target, int current) {
        String studentId = new SessionManager(activity).getStudentId();
        if (target == R.id.nav_student_home) {
            Intent home = new Intent(activity, StudentHomeActivity.class);
            home.putExtra("STUDENT_ID", studentId);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(home);
            activity.finish();
        } else {
            Class<?> screen = target == R.id.nav_student_groups ? StudentGroupsActivity.class
                    : target == R.id.nav_student_notifications ? StudentNotificationsActivity.class
                    : StudentProfileActivity.class;
            Intent tab = new Intent(activity, screen);
            tab.putExtra("STUDENT_ID", studentId);
            activity.startActivity(tab);
            if (current != R.id.nav_student_home) activity.finish();
        }
        activity.overridePendingTransition(0, 0);
    }
}
