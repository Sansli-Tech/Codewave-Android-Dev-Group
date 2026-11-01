package com.example.mustudentsync.util;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mustudentsync.GroupsActivity;
import com.example.mustudentsync.LecturerHomeActivity;
import com.example.mustudentsync.LecturerProfileActivity;
import com.example.mustudentsync.LecturerRosterActivity;
import com.example.mustudentsync.R;
import com.example.mustudentsync.SyncActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * The lecturer's bottom bar: Home, Roster, Groups, Sync, Profile.
 *
 * Each tab is its own Activity. Home is always at the bottom of the back stack,
 * so Back on any other tab returns to Home, and Back on Home exits (after
 * "Press back again", see BackToExit).
 *
 *  - Home -> a tab: the tab opens on top of Home.
 *  - Tab -> another tab: the new tab replaces the current one (still on top of Home).
 *  - Tab -> Home: the tab closes, revealing Home.
 */
public final class LecturerNav {

    private LecturerNav() { }

    public static void setup(AppCompatActivity activity, BottomNavigationView nav, int currentTabId) {
        nav.setSelectedItemId(currentTabId);
        nav.setOnItemReselectedListener(item -> { /* already here */ });
        nav.setOnItemSelectedListener(item -> {
            int target = item.getItemId();
            if (target != currentTabId) open(activity, target, currentTabId);
            // false: keep this screen's own tab highlighted; the new screen highlights its tab.
            return false;
        });
    }

    private static void open(AppCompatActivity activity, int target, int current) {
        if (target == R.id.nav_home) {
            Intent home = new Intent(activity, LecturerHomeActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(home);
            activity.finish();
        } else {
            activity.startActivity(new Intent(activity, screenFor(target)));
            if (current != R.id.nav_home) activity.finish();
        }
        activity.overridePendingTransition(0, 0); // tabs switch instantly, no slide
    }

    private static Class<?> screenFor(int tabId) {
        if (tabId == R.id.nav_roster) return LecturerRosterActivity.class;
        if (tabId == R.id.nav_groups) return GroupsActivity.class;
        if (tabId == R.id.nav_sync) return SyncActivity.class;
        if (tabId == R.id.nav_profile) return LecturerProfileActivity.class;
        return LecturerHomeActivity.class;
    }
}
