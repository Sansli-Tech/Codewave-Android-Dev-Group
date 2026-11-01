package com.example.mustudentsync.util;

import android.os.SystemClock;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mustudentsync.R;

/**
 * "Press back again to exit": the first Back on a home screen shows a message,
 * a second Back within 2 seconds closes the app. Works with the back gesture too.
 */
public final class BackToExit {

    private static final long WINDOW_MS = 2000;

    private BackToExit() { }

    /** Use on screens where Back should only ever mean "exit". */
    public static void install(AppCompatActivity activity) {
        install(activity, null);
    }

    /**
     * @param beforeExit runs first on every Back; return true if it handled Back
     *                   (e.g. switched to the Home panel), false to count towards exit.
     */
    public static void install(AppCompatActivity activity, BackHandler beforeExit) {
        activity.getOnBackPressedDispatcher().addCallback(activity, new OnBackPressedCallback(true) {
            private long lastPress = 0;

            @Override
            public void handleOnBackPressed() {
                if (beforeExit != null && beforeExit.handleBack()) return;
                long now = SystemClock.elapsedRealtime();
                if (now - lastPress < WINDOW_MS) {
                    activity.finishAffinity();
                } else {
                    lastPress = now;
                    Toast.makeText(activity, R.string.press_back_again, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public interface BackHandler {
        boolean handleBack();
    }
}
