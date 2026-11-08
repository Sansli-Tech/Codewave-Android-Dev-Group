package com.example.mustudentsync;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mustudentsync.util.BackToExit;
import com.example.mustudentsync.util.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * First screen of the app (launcher).
 * Signed in already? Goes straight to the right home screen.
 * Signed out: landing page with Home, Guide, Help and Account tabs.
 * Back on Guide/Help/Account returns to Home; Back on Home asks "Press back again to exit".
 */
public class LandingActivity extends AppCompatActivity {

    private static final String KEY_TAB = "landing_tab";

    private View panelHome, panelGuide, panelHelp, panelAccount, homeBackground;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (routeIfSignedIn()) return;

        setContentView(R.layout.activity_landing);

        panelHome = findViewById(R.id.panelHome);
        panelGuide = findViewById(R.id.panelGuide);
        panelHelp = findViewById(R.id.panelHelp);
        panelAccount = findViewById(R.id.panelAccount);
        bottomNav = findViewById(R.id.bottomNavLanding);
        homeBackground = findViewById(R.id.imgHomeBackground);
        com.example.mustudentsync.util.ThemePicker.bind(findViewById(R.id.themePickerLanding));

        fillTexts();

        findViewById(R.id.btnLandingSignIn).setOnClickListener(v -> openSignIn());
        findViewById(R.id.btnAccountSignIn).setOnClickListener(v -> openSignIn());
        findViewById(R.id.btnLandingRegister).setOnClickListener(v -> openRegister());
        findViewById(R.id.btnAccountRegister).setOnClickListener(v -> openRegister());

        bottomNav.setOnItemSelectedListener(item -> {
            showPanel(item.getItemId());
            return true;
        });

        int tab = savedInstanceState != null
                ? savedInstanceState.getInt(KEY_TAB, R.id.landing_home) : R.id.landing_home;
        bottomNav.setSelectedItemId(tab);
        showPanel(tab);

        // Back: other tab -> Home tab; Home tab -> "Press back again to exit".
        BackToExit.install(this, () -> {
            if (bottomNav.getSelectedItemId() != R.id.landing_home) {
                bottomNav.setSelectedItemId(R.id.landing_home);
                return true;
            }
            return false;
        });


        if (getIntent().getBooleanExtra(com.example.mustudentsync.util.SessionTimeout.EXTRA_EXPIRED, false)) {
            android.widget.Toast.makeText(this, R.string.session_expired, android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Signed-in users skip the landing page. Returns true if it navigated away. */
    private boolean routeIfSignedIn() {
        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) return false;

        Intent next;
        if ("lecturer".equalsIgnoreCase(session.getRole())) {
            next = new Intent(this, LecturerHomeActivity.class);
        } else if ("student".equalsIgnoreCase(session.getRole()) && session.getStudentId() != null) {
            next = new Intent(this, StudentHomeActivity.class);
            next.putExtra("STUDENT_ID", session.getStudentId());
        } else {
            return false;
        }
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(next);
        finish();
        return true;
    }

    private void showPanel(int tabId) {
        panelHome.setVisibility(tabId == R.id.landing_home ? View.VISIBLE : View.GONE);
        homeBackground.setVisibility(tabId == R.id.landing_home ? View.VISIBLE : View.GONE);
        panelGuide.setVisibility(tabId == R.id.landing_guide ? View.VISIBLE : View.GONE);
        panelHelp.setVisibility(tabId == R.id.landing_help ? View.VISIBLE : View.GONE);
        panelAccount.setVisibility(tabId == R.id.landing_account ? View.VISIBLE : View.GONE);
    }

    // Sign in / Register open on top of the landing page, so Back returns here.
    private void openSignIn() {
        startActivity(new Intent(this, SignInActivity.class));
    }

    private void openRegister() {
        startActivity(new Intent(this, RegisterActivity.class));
    }

    private void fillTexts() {
        step(R.id.step1, "1", R.string.landing_step1_title, R.string.landing_step1_body);
        step(R.id.step2, "2", R.string.landing_step2_title, R.string.landing_step2_body);
        step(R.id.step3, "3", R.string.landing_step3_title, R.string.landing_step3_body);
        info(R.id.guideStudents, R.string.guide_students_title, R.string.guide_students_body);
        info(R.id.guideLecturers, R.string.guide_lecturers_title, R.string.guide_lecturers_body);
        info(R.id.helpCode, R.string.help_code_title, R.string.help_code_body);
        info(R.id.helpOffline, R.string.help_offline_title, R.string.help_offline_body);
        info(R.id.helpAccount, R.string.help_account_title, R.string.help_account_body);
    }

    private void step(int includeId, String number, int title, int body) {
        View root = findViewById(includeId);
        ((TextView) root.findViewById(R.id.tvStepNumber)).setText(number);
        ((TextView) root.findViewById(R.id.tvStepTitle)).setText(title);
        ((TextView) root.findViewById(R.id.tvStepBody)).setText(body);
    }

    private void info(int includeId, int title, int body) {
        View root = findViewById(includeId);
        ((TextView) root.findViewById(R.id.tvInfoTitle)).setText(title);
        ((TextView) root.findViewById(R.id.tvInfoBody)).setText(body);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bottomNav != null) outState.putInt(KEY_TAB, bottomNav.getSelectedItemId());
    }
}
