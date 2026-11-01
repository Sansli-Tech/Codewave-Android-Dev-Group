package com.example.mustudentsync.util;

import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.mustudentsync.R;
import com.google.android.material.button.MaterialButtonToggleGroup;

/** Wires up layout_theme_picker.xml (Phone / Light / Dark). */
public final class ThemePicker {

    private ThemePicker() { }

    /** @param pickerRoot the view of the included layout_theme_picker */
    public static void bind(View pickerRoot) {
        MaterialButtonToggleGroup group = pickerRoot.findViewById(R.id.toggleTheme);

        int mode = ThemePrefs.get(pickerRoot.getContext());
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) group.check(R.id.btnThemeLight);
        else if (mode == AppCompatDelegate.MODE_NIGHT_YES) group.check(R.id.btnThemeDark);
        else group.check(R.id.btnThemeSystem);

        // Added after the initial check, so opening the screen doesn't trigger a switch.
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked) return;
            int chosen = checkedId == R.id.btnThemeLight ? AppCompatDelegate.MODE_NIGHT_NO
                    : checkedId == R.id.btnThemeDark ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (chosen != ThemePrefs.get(g.getContext())) {
                ThemePrefs.set(g.getContext(), chosen);
            }
        });
    }
}