package com.example.mustudentsync;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mustudentsync.data.local.AppDatabase;
import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.util.LecturerNav;
import com.example.mustudentsync.util.SessionManager;

import java.util.concurrent.Executors;

/** Lecturer Profile tab: who is signed in, which server, and Sign out. */
public class LecturerProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lecturer_profile);

        LecturerNav.setup(this, findViewById(R.id.bottomNav), R.id.nav_profile);

        SessionManager session = new SessionManager(this);
        String username = session.getUsername();
        ((TextView) findViewById(R.id.tvProfileUsername)).setText(username != null ? username : "—");
        ((TextView) findViewById(R.id.tvProfileRole)).setText("Lecturer");
        ((TextView) findViewById(R.id.tvProfileServer)).setText(RetrofitClient.BASE_URL);
        com.example.mustudentsync.util.ThemePicker.bind(findViewById(R.id.themePickerProfile));

        findViewById(R.id.btnSignOut).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setMessage(R.string.profile_sign_out_confirm)
                        .setPositiveButton(R.string.profile_sign_out, (d, w) -> signOut())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
    }

    /**
     * Clears the session (token, role, sync cursor) and the local Room copy, so the
     * next person to sign in on this phone never sees this lecturer's data.
     */
    private void signOut() {
        SessionManager session = new SessionManager(this);
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            // Removes the cached roster; unsent changes stay queued for this lecturer only.
            com.example.mustudentsync.sync.SyncScheduler.stop(getApplicationContext());
            AppDatabase.clearCachedData(getApplicationContext()); // must run off the main thread
            session.clearSession();
            main.post(() -> {
                Intent landing = new Intent(this, LandingActivity.class);
                landing.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(landing);
                finish();
            });
        });
    }
}
