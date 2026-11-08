package com.example.mustudentsync.util;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.example.mustudentsync.LandingActivity;
import com.example.mustudentsync.data.local.AppDatabase;
import com.example.mustudentsync.sync.SyncScheduler;

import java.util.concurrent.Executors;

/**
 * Signs out from any screen: stops background sync, clears the session (token, role, sync
 * cursor) and this phone's cached data, then returns to the landing page. Unsent changes stay
 * queued for that account only, so the next person to sign in never sends or sees them.
 */
public final class SignOut {

    private SignOut() { }

    public static void run(Activity activity) {
        SessionManager session = new SessionManager(activity);
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            SyncScheduler.stop(activity.getApplicationContext());
            AppDatabase.clearCachedData(activity.getApplicationContext());   // off the main thread
            session.clearSession();
            main.post(() -> {
                Intent landing = new Intent(activity, LandingActivity.class);
                landing.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                activity.startActivity(landing);
                activity.finish();
            });
        });
    }
}
