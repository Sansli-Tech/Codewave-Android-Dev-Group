package com.example.mustudentsync.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Remembers the Light / Dark / Phone choice and applies it.
 * Kept in its own preferences file, so signing out doesn't reset it.
 */
public final class ThemePrefs {

    private static final String PREFS = "mu_studentsync_settings";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemePrefs() { }

    /** One of AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, MODE_NIGHT_NO, MODE_NIGHT_YES. */
    public static int get(Context context) {
        return prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /** Saves the choice and switches the app; open screens redraw in the new mode. */
    public static void set(Context context, int mode) {
        prefs(context).edit().putInt(KEY_NIGHT_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /** Called once when the app starts (MUSyncApp). */
    public static void apply(Context context) {
        AppCompatDelegate.setDefaultNightMode(get(context));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}