package com.example.mustudentsync.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;

import com.example.mustudentsync.LandingActivity;
import com.example.mustudentsync.data.local.AppDatabase;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executors;

/**
 * Signs the user (student or lecturer) out after 30 minutes without using the app.
 *
 * "Using" means touching the screen or pressing a key. The clock keeps running while
 * the app is closed or in the background, so opening it again after more than 30 minutes
 * also lands on the landing page. On sign-out the session and this phone's copy of the
 * data are cleared, exactly like tapping Sign out.
 *
 * Hooked up once in MUSyncApp; no screen needs its own code for this.
 */
public final class SessionTimeout {

    public static final long TIMEOUT_MS = 30 * 60 * 1000L;   // 30 minutes
    private static final long CHECK_EVERY_MS = 30 * 1000L;   // check twice a minute while open
    public static final String EXTRA_EXPIRED = "SESSION_EXPIRED";

    private static final String PREFS = "mu_studentsync_session"; // same file as SessionManager
    private static final String KEY_LAST_ACTIVE = "last_active_millis";

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> resumed = new WeakReference<>(null);
    private static volatile long lastActive = 0;
    private static boolean expiring = false;

    private SessionTimeout() { }

    /** Counts every touch / key press on this screen as activity. Safe to call repeatedly. */
    public static void watchTouches(Activity activity) {
        Window window = activity.getWindow();
        Window.Callback original = window.getCallback();
        if (original == null || Proxy.isProxyClass(original.getClass())) return; // already watched

        // A pass-through wrapper: every window event goes to the screen exactly as before;
        // touches and key presses also reset the inactivity clock.
        Window.Callback watcher = (Window.Callback) Proxy.newProxyInstance(
                Window.Callback.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("dispatchTouchEvent".equals(name) || "dispatchKeyEvent".equals(name)) {
                        lastActive = System.currentTimeMillis();
                    }
                    try {
                        return method.invoke(original, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        window.setCallback(watcher);
    }

    /** A screen came to the front: sign out if the user has been away too long. */
    public static void onResumed(Activity activity) {
        resumed = new WeakReference<>(activity);
        if (lastActive == 0) lastActive = prefs(activity).getLong(KEY_LAST_ACTIVE, 0);

        if (isExpired(activity)) {
            expire(activity);
            return;
        }
        lastActive = System.currentTimeMillis();
        main.removeCallbacks(CHECK);
        main.postDelayed(CHECK, CHECK_EVERY_MS);
    }

    /** A screen went to the back: remember when, so the clock survives the app being closed. */
    public static void onPaused(Activity activity) {
        if (resumed.get() == activity) resumed = new WeakReference<>(null);
        main.removeCallbacks(CHECK);
        prefs(activity).edit().putLong(KEY_LAST_ACTIVE, lastActive).apply();
    }

    /** While the app is open: sign out once 30 minutes pass without a touch. */
    private static final Runnable CHECK = new Runnable() {
        @Override
        public void run() {
            Activity activity = resumed.get();
            if (activity == null) return;
            if (isExpired(activity)) {
                expire(activity);
            } else {
                main.postDelayed(this, CHECK_EVERY_MS);
            }
        }
    };

    private static boolean isExpired(Context context) {
        return new SessionManager(context).isLoggedIn()
                && lastActive > 0
                && System.currentTimeMillis() - lastActive > TIMEOUT_MS;
    }

    private static void expire(Activity activity) {
        if (expiring) return;
        expiring = true;
        main.removeCallbacks(CHECK);
        Context app = activity.getApplicationContext();

        Executors.newSingleThreadExecutor().execute(() -> {
            // Unsent changes stay queued for this account and resume at the next sign-in.
            com.example.mustudentsync.sync.SyncScheduler.stop(app);
            AppDatabase.clearCachedData(app); // off the main thread
            new SessionManager(app).clearSession();
            lastActive = 0;
            main.post(() -> {
                expiring = false;
                Intent landing = new Intent(app, LandingActivity.class);
                landing.putExtra(EXTRA_EXPIRED, true);
                landing.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                app.startActivity(landing);
            });
        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}