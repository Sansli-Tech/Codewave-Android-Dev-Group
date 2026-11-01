package com.example.mustudentsync;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.mustudentsync.util.SessionTimeout;
import com.example.mustudentsync.util.SystemBars;
import com.example.mustudentsync.util.ThemePrefs;

/**
 * Runs once when the app starts (registered in AndroidManifest.xml as android:name).
 *  - applies the saved Light / Dark / Phone choice;
 *  - makes every screen leave room for the status and navigation bars;
 *  - signs the user out after 30 minutes of inactivity (SessionTimeout).
 */
public class MUSyncApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.apply(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                // After onCreate, so the screen's layout is already in place.
                SystemBars.fit(activity);
                SessionTimeout.watchTouches(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                SessionTimeout.onResumed(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                SessionTimeout.onPaused(activity);
            }

            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) { }
            @Override public void onActivityStopped(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) { }
            @Override public void onActivityDestroyed(@NonNull Activity a) { }
        });
    }
}