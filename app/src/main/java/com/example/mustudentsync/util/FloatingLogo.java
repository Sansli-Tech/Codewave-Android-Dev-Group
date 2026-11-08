package com.example.mustudentsync.util;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Gentle up-and-down "floating" motion for the logo on Sign in and Register.
 * Respects the phone's "Remove animations" accessibility setting: if animations are off,
 * the logo simply stays still. Stops by itself when the screen goes away.
 */
public final class FloatingLogo {

    private static final long HALF_CYCLE_MS = 1800;
    private static final float RISE_DP = 10f;

    private FloatingLogo() { }

    public static void start(View logo) {
        if (logo == null || !animationsEnabled(logo)) return;
        float rise = RISE_DP * logo.getResources().getDisplayMetrics().density;
        ObjectAnimator floatUp = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -rise);
        floatUp.setDuration(HALF_CYCLE_MS);
        floatUp.setInterpolator(new AccelerateDecelerateInterpolator());
        floatUp.setRepeatMode(ValueAnimator.REVERSE);
        floatUp.setRepeatCount(ValueAnimator.INFINITE);
        floatUp.start();

        logo.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { }

            @Override public void onViewDetachedFromWindow(View v) {
                floatUp.cancel();   // no animation keeps running after the screen closes
                v.removeOnAttachStateChangeListener(this);
            }
        });
    }

    private static boolean animationsEnabled(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return ValueAnimator.areAnimatorsEnabled();
        float scale = Settings.Global.getFloat(view.getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return scale > 0f;
    }
}
