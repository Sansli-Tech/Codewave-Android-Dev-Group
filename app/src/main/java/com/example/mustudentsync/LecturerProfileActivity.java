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
        findViewById(R.id.btnOpenSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        findViewById(R.id.btnSignOut).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setMessage(R.string.profile_sign_out_confirm)
                        .setPositiveButton(R.string.profile_sign_out, (d, w) -> signOut())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
    }

    /** Same sign-out as Settings: clears the session and cached data, alerts become generic. */
    private void signOut() {
        com.example.mustudentsync.util.SignOut.run(this);
    }
}
