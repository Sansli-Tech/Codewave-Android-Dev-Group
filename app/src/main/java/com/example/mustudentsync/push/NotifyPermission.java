package com.example.mustudentsync.push;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Android 13+ asks the user before an app may show alerts. We ask once, from Home.
 * If they say no, nothing breaks: the bell and the Notifications tab still show the count.
 */
public final class NotifyPermission {

    private static final String PREFS = "mu_push";
    private static final String KEY_ASKED = "asked_post_notifications";

    private NotifyPermission() { }

    public static void askOnce(Activity activity) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ASKED, false)) return;
        prefs.edit().putBoolean(KEY_ASKED, true).apply();
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4101);
    }
}
