package com.example.mustudentsync;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mustudentsync.data.AccountManager;
import com.example.mustudentsync.data.SyncManager;
import com.example.mustudentsync.data.local.AppDatabase;
import com.example.mustudentsync.data.local.PendingOperationDao;
import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.util.SessionManager;
import com.example.mustudentsync.util.SignOut;
import com.example.mustudentsync.util.ThemePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Settings for students and lecturers, in one place:
 * Account (change password, recovery e-mail), Appearance (Phone / Light / Dark),
 * Sync & data, Help, About, and Sign out.
 */
public class SettingsActivity extends AppCompatActivity {

    private SessionManager session;
    private AccountManager accounts;
    private TextView tvRecovery, tvLastSync, tvQueue;
    private String recoveryEmail;      // null = unknown (offline), "" = none
    private int waiting, attention;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        session = new SessionManager(this);
        accounts = new AccountManager(this);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarSettings);
        toolbar.setNavigationOnClickListener(v -> finish());

        boolean lecturer = "LECTURER".equalsIgnoreCase(session.getRole());
        String user = session.getUsername();
        ((TextView) findViewById(R.id.tvSettingsUser)).setText(user != null ? user : "—");
        ((TextView) findViewById(R.id.tvSettingsRole)).setText(lecturer ? R.string.settings_role_lecturer : R.string.settings_role_student);
        tvRecovery = findViewById(R.id.tvSettingsRecovery);
        tvLastSync = findViewById(R.id.tvSettingsLastSync);
        tvQueue = findViewById(R.id.tvSettingsQueue);

        findViewById(R.id.btnChangePassword).setOnClickListener(v -> changePassword());
        findViewById(R.id.btnRecoveryEmail).setOnClickListener(v -> editRecoveryEmail());

        ThemePicker.bind(findViewById(R.id.themePickerSettings));

        findViewById(R.id.btnSettingsSyncNow).setOnClickListener(v -> {
            Toast.makeText(this, R.string.sync_in_progress, Toast.LENGTH_SHORT).show();
            new SyncManager(this).syncNow((ok, msg) -> {
                showLastSync();
                Toast.makeText(this, ok ? getString(R.string.sync_done) : msg, Toast.LENGTH_SHORT).show();
            });
        });
        String account = session.getAccountKey();
        if (account != null) {
            PendingOperationDao queue = AppDatabase.getInstance(this).pendingOperationDao();
            queue.countWaiting(account).observe(this, n -> { waiting = n != null ? n : 0; showQueue(); });
            queue.countActionRequired(account).observe(this, n -> { attention = n != null ? n : 0; showQueue(); });
        }

        info(R.id.settingsHelpPassword, R.string.settings_help_password_title, R.string.settings_help_password_body);
        info(R.id.settingsHelpOffline, R.string.help_offline_title, R.string.settings_help_offline_body);
        info(R.id.settingsHelpPending, R.string.settings_help_pending_title, R.string.settings_help_pending_body);
        info(R.id.settingsHelpGroup, R.string.settings_help_group_title,
                lecturer ? R.string.settings_help_group_body_lecturer : R.string.settings_help_group_body_student);
        info(R.id.settingsHelpRole, lecturer ? R.string.guide_lecturers_title : R.string.guide_students_title,
                lecturer ? R.string.settings_help_role_lecturer : R.string.settings_help_role_student);

        ((TextView) findViewById(R.id.tvSettingsVersion)).setText(
                getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        ((TextView) findViewById(R.id.tvSettingsServer)).setText(
                getString(R.string.settings_server, RetrofitClient.BASE_URL));

        findViewById(R.id.btnSettingsSignOut).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(R.string.profile_sign_out_confirm)
                .setPositiveButton(R.string.profile_sign_out, (d, w) -> SignOut.run(this))
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        showLastSync();
        loadRecoveryEmail();
    }

    // ------------------------------------------------------------------ account

    private void loadRecoveryEmail() {
        accounts.getRecoveryEmail((ok, email) -> {
            if (isFinishing()) return;
            recoveryEmail = ok ? (email != null ? email : "") : null;
            showRecovery();
        });
    }

    private void showRecovery() {
        boolean lecturer = "LECTURER".equalsIgnoreCase(session.getRole());
        if (recoveryEmail == null) {
            tvRecovery.setText(R.string.recovery_hint_offline);
        } else if (!recoveryEmail.isEmpty()) {
            tvRecovery.setText(getString(R.string.settings_recovery_set, recoveryEmail));
        } else if (lecturer) {
            tvRecovery.setText(R.string.settings_recovery_staff);   // codes go to the staff e-mail
        } else {
            tvRecovery.setText(R.string.recovery_hint_missing);
        }
    }

    private void editRecoveryEmail() {
        if (recoveryEmail == null) {
            Toast.makeText(this, R.string.recovery_hint_offline, Toast.LENGTH_LONG).show();
            return;
        }
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_recovery_email, null);
        TextInputLayout til = form.findViewById(R.id.tilDialogEmail);
        TextInputEditText field = form.findViewById(R.id.etDialogEmail);
        field.setText(recoveryEmail);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.recovery_title)
                .setView(form)
                .setPositiveButton(R.string.recovery_save, null)       // set below: keep open on errors
                .setNegativeButton(R.string.btn_cancel, null);
        if (!recoveryEmail.isEmpty()) {
            builder.setNeutralButton(R.string.recovery_delete, (d, w) -> saveRecovery("", null));
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = field.getText() != null ? field.getText().toString().trim() : "";
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                til.setError(getString(R.string.recovery_invalid));
                return;
            }
            saveRecovery(email, dialog);
        }));
        dialog.show();
    }

    private void saveRecovery(String email, AlertDialog dialog) {
        if (dialog != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        accounts.setRecoveryEmail(email, (ok, message) -> {
            if (isFinishing()) return;
            if (dialog != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            if (ok) {
                recoveryEmail = email.toLowerCase(java.util.Locale.ROOT);
                showRecovery();
                if (dialog != null) dialog.dismiss();
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void changePassword() {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        TextInputLayout tilCurrent = form.findViewById(R.id.tilCurrentPassword);
        TextInputLayout tilNew = form.findViewById(R.id.tilNewPasswordSettings);
        TextInputLayout tilConfirm = form.findViewById(R.id.tilConfirmPasswordSettings);
        TextInputEditText etCurrent = form.findViewById(R.id.etCurrentPassword);
        TextInputEditText etNew = form.findViewById(R.id.etNewPasswordSettings);
        TextInputEditText etConfirm = form.findViewById(R.id.etConfirmPasswordSettings);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_change_password)
                .setView(form)
                .setPositiveButton(R.string.forgot_submit, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button change = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            change.setOnClickListener(v -> {
                tilCurrent.setError(null);
                tilNew.setError(null);
                tilConfirm.setError(null);
                String current = etCurrent.getText() != null ? etCurrent.getText().toString() : "";
                String next = etNew.getText() != null ? etNew.getText().toString() : "";
                String confirm = etConfirm.getText() != null ? etConfirm.getText().toString() : "";
                if (current.isEmpty()) { tilCurrent.setError(getString(R.string.forgot_err_required)); return; }
                if (next.length() < 12) { tilNew.setError(getString(R.string.forgot_err_password)); return; }
                if (!next.equals(confirm)) { tilConfirm.setError(getString(R.string.forgot_err_match)); return; }
                change.setEnabled(false);
                accounts.changePassword(current, next, (ok, message) -> {
                    if (isFinishing()) return;
                    change.setEnabled(true);
                    if (ok) {
                        dialog.dismiss();
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    } else {
                        tilCurrent.setError(message);   // e.g. "Your current password is not correct."
                    }
                });
            });
        });
        dialog.show();
    }

    // ------------------------------------------------------------------ sync

    private void showLastSync() {
        long last = session.getLastSync();
        tvLastSync.setText(last > 0
                ? getString(R.string.settings_last_sync, DateUtils.getRelativeDateTimeString(this, last,
                        DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0))
                : getString(R.string.settings_never_synced));
    }

    private void showQueue() {
        if (attention > 0) {
            tvQueue.setText(getResources().getQuantityString(R.plurals.settings_queue_attention, attention, attention));
        } else if (waiting > 0) {
            tvQueue.setText(getResources().getQuantityString(R.plurals.settings_queue_waiting, waiting, waiting));
        } else {
            tvQueue.setText(R.string.settings_queue_empty);
        }
    }

    // ------------------------------------------------------------------ help

    private void info(int includeId, int title, int body) {
        View root = findViewById(includeId);
        ((TextView) root.findViewById(R.id.tvInfoTitle)).setText(title);
        ((TextView) root.findViewById(R.id.tvInfoBody)).setText(body);
    }
}
