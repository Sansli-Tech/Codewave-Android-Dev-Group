package com.example.mustudentsync;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Start-up screen. The logo builds itself piece by piece:
 *   1. the blue circle grows in (with its shadow),
 *   2. the open book rises from below,
 *   3. the "MU" letters drop into place,
 *   4. the graduation cap falls on top with a little tilt,
 *   5. the app name fades up.
 * About 1.7 seconds in total; tap anywhere to skip. If the phone's animations are turned off
 * (accessibility), the finished logo is shown briefly instead. Then the landing page opens,
 * which sends signed-in users straight to their home screen as before.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long HOLD_MS = 300;        // finished logo stays this long
    private static final long STILL_MS = 600;       // when animations are off

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable next = this::openLanding;
    private AnimatorSet show;
    private boolean finished;   // animation done (or skipped)
    private boolean left;       // landing page already opened

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        findViewById(R.id.splashRoot).setOnClickListener(v -> skip());

        if (!animationsEnabled()) {
            showFinishedLogo();
            finished = true;
            main.postDelayed(next, STILL_MS);
            return;
        }
        View logo = findViewById(R.id.splashLogo);
        logo.post(this::play);   // after layout, so sizes and pivots are known
    }

    private void play() {
        float dp = getResources().getDisplayMetrics().density;
        View shadow = findViewById(R.id.splashShadow);
        View disk = findViewById(R.id.splashDisk);
        View book = findViewById(R.id.splashBook);
        View letters = findViewById(R.id.splashLetters);
        View cap = findViewById(R.id.splashCap);
        View name = findViewById(R.id.splashName);
        View tagline = findViewById(R.id.splashTagline);

        // Each piece turns or grows around its own centre, not the logo's centre.
        letters.setPivotY(letters.getHeight() * 0.55f);
        cap.setPivotX(cap.getWidth() * 0.5f);
        cap.setPivotY(cap.getHeight() * 0.25f);

        List<Animator> parts = new ArrayList<>();
        parts.add(part(disk, 0, 450, new OvershootInterpolator(1.6f),
                fade(), PropertyValuesHolder.ofFloat(View.SCALE_X, 0.3f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.3f, 1f)));
        parts.add(part(shadow, 150, 450, new DecelerateInterpolator(), fade()));
        parts.add(part(book, 300, 400, new DecelerateInterpolator(2f),
                fade(), PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 40 * dp, 0f)));
        parts.add(part(letters, 550, 400, new OvershootInterpolator(1.2f),
                fade(), PropertyValuesHolder.ofFloat(View.SCALE_X, 1.6f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.6f, 1f)));
        parts.add(part(cap, 800, 550, new OvershootInterpolator(1.4f),
                fade(), PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -150 * dp, 0f),
                PropertyValuesHolder.ofFloat(View.ROTATION, -35f, 0f)));
        parts.add(part(name, 1150, 350, new DecelerateInterpolator(),
                fade(), PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 14 * dp, 0f)));
        parts.add(part(tagline, 1250, 350, new DecelerateInterpolator(), fade()));

        show = new AnimatorSet();
        show.playTogether(parts);
        show.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (finished) return;
                finished = true;
                main.postDelayed(next, HOLD_MS);
            }
        });
        show.start();
    }

    private static PropertyValuesHolder fade() {
        return PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f);
    }

    private static Animator part(View v, long delay, long duration, TimeInterpolator how,
                                 PropertyValuesHolder... changes) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, changes);
        a.setStartDelay(delay);
        a.setDuration(duration);
        a.setInterpolator(how);
        return a;
    }

    /** Tap to skip: jump to the finished logo and go on. */
    private void skip() {
        if (show != null) show.end();   // every piece lands in its final place
        showFinishedLogo();
        finished = true;
        main.removeCallbacks(next);
        openLanding();
    }

    private void showFinishedLogo() {
        int[] ids = {R.id.splashShadow, R.id.splashDisk, R.id.splashBook, R.id.splashLetters,
                R.id.splashCap, R.id.splashName, R.id.splashTagline};
        for (int id : ids) {
            View v = findViewById(id);
            v.setAlpha(1f);
            v.setTranslationY(0f);
            v.setScaleX(1f);
            v.setScaleY(1f);
            v.setRotation(0f);
        }
    }

    private void openLanding() {
        if (left || isFinishing()) return;
        left = true;
        startActivity(new Intent(this, LandingActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        main.removeCallbacks(next);   // don't open a screen while the app is in the background
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (finished) openLanding();   // came back after the animation ended
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(next);
        if (show != null) {
            show.removeAllListeners();
            show.cancel();
        }
        super.onDestroy();
    }

    /** False when "Remove animations" is on or the animator scale is 0 (accessibility). */
    private boolean animationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return ValueAnimator.areAnimatorsEnabled();
        return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }
}
