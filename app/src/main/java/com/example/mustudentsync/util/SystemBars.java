package com.example.mustudentsync.util;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Keeps every screen clear of the phone's status bar (time, battery, signal) and
 * navigation bar, on every Android version, and makes their icons readable:
 * dark icons in light mode, light icons in dark mode.
 *
 * The screen's content is padded by exactly the size of those bars (and of the
 * keyboard when it is open). The space behind the bars shows the theme background.
 */
public final class SystemBars {

    private SystemBars() { }

    @SuppressWarnings("deprecation") // setStatusBarColor: still needed below Android 15
    public static void fit(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED; // screens below don't add the space a second time
        });
        ViewCompat.requestApplyInsets(content);

        boolean dark = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat icons = WindowCompat.getInsetsController(window, window.getDecorView());
        icons.setAppearanceLightStatusBars(!dark);
        icons.setAppearanceLightNavigationBars(!dark);
    }
}