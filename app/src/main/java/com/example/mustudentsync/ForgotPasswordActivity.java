package com.example.mustudentsync;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mustudentsync.data.AccountManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * "Forgot password?" from the sign-in screen.
 *
 * Code mode (everyone): enter student number or staff e-mail -> "E-mail me a code" -> type the
 * 6-digit code from the e-mail (or the 16-character code from a lecturer) and a new password.
 * Faculty mode (lecturers without e-mail access): staff e-mail + staff ID + faculty code.
 *
 * Typed text and the chosen mode survive rotation. Passwords are never logged or stored.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String KEY_FACULTY_MODE = "faculty_mode";

    private View codeMode, facultyMode;
    private TextView tvStatus;
    private TextInputLayout tilLogin, tilCode, tilStaffId, tilFaculty, tilNew, tilConfirm;
    private TextInputEditText etLogin, etCode, etStaffId, etFaculty, etNew, etConfirm;
    private MaterialButton btnSubmit, btnSendCode, btnSwitch;
    private boolean faculty = false;
    private boolean sending = false;
    private android.content.res.ColorStateList normalStatusColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        codeMode = findViewById(R.id.groupCodeMode);
        facultyMode = findViewById(R.id.groupFacultyMode);
        tvStatus = findViewById(R.id.tvForgotStatus);
        normalStatusColor = tvStatus.getTextColors();
        tilLogin = findViewById(R.id.tilLogin);
        tilCode = findViewById(R.id.tilResetCode);
        tilStaffId = findViewById(R.id.tilResetStaffId);
        tilFaculty = findViewById(R.id.tilResetFaculty);
        tilNew = findViewById(R.id.tilNewPassword);
        tilConfirm = findViewById(R.id.tilConfirmPassword);
        etLogin = findViewById(R.id.etLogin);
        etCode = findViewById(R.id.etResetCode);
        etStaffId = findViewById(R.id.etResetStaffId);
        etFaculty = findViewById(R.id.etResetFaculty);
        etNew = findViewById(R.id.etNewPassword);
        etConfirm = findViewById(R.id.etConfirmPassword);
        btnSubmit = findViewById(R.id.btnResetSubmit);
        btnSendCode = findViewById(R.id.btnSendCode);
        btnSwitch = findViewById(R.id.btnSwitchMode);

        faculty = savedInstanceState != null && savedInstanceState.getBoolean(KEY_FACULTY_MODE);
        showMode();

        btnSendCode.setOnClickListener(v -> sendCode());
        btnSubmit.setOnClickListener(v -> submit());
        btnSwitch.setOnClickListener(v -> {
            faculty = !faculty;
            showMode();
        });
        findViewById(R.id.btnResetBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FACULTY_MODE, faculty);
    }

    private void showMode() {
        codeMode.setVisibility(faculty ? View.GONE : View.VISIBLE);
        facultyMode.setVisibility(faculty ? View.VISIBLE : View.GONE);
        btnSwitch.setText(faculty ? R.string.forgot_use_code : R.string.forgot_use_faculty);
        tvStatus.setVisibility(View.GONE);
    }

    private static String text(TextInputEditText e) {
        return e.getText() != null ? e.getText().toString().trim() : "";
    }

    private void showStatus(String message, boolean error) {
        tvStatus.setText(message);
        if (error) {
            tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.campus_full));
        } else {
            tvStatus.setTextColor(normalStatusColor);
        }
        tvStatus.setVisibility(View.VISIBLE);
    }

    private void sendCode() {
        if (sending) return;
        tilLogin.setError(null);
        if (text(etLogin).length() < 3) {
            tilLogin.setError(getString(R.string.forgot_err_login));
            return;
        }
        setSending(true);
        new AccountManager(this).requestResetCode(text(etLogin), (ok, message) -> {
            if (isFinishing()) return;
            setSending(false);
            showStatus(message, !ok);
            if (ok) etCode.requestFocus();
        });
    }

    private void submit() {
        if (sending) return;
        for (TextInputLayout t : new TextInputLayout[]{tilLogin, tilCode, tilStaffId, tilFaculty, tilNew, tilConfirm}) {
            t.setError(null);
        }
        tvStatus.setVisibility(View.GONE);

        boolean ok = true;
        String login = text(etLogin);
        if (login.length() < 3) { tilLogin.setError(getString(R.string.forgot_err_login)); ok = false; }
        String password = etNew.getText() != null ? etNew.getText().toString() : "";
        String confirm = etConfirm.getText() != null ? etConfirm.getText().toString() : "";
        if (password.length() < 12) {
            tilNew.setError(getString(R.string.forgot_err_password));
            ok = false;
        } else if (!password.equals(confirm)) {
            tilConfirm.setError(getString(R.string.forgot_err_match));
            ok = false;
        }

        AccountManager.Callback done = (success, message) -> {
            if (isFinishing()) return;
            setSending(false);
            if (success) {
                etNew.setText("");
                etConfirm.setText("");
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.forgot_done_title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.forgot_back, (d, w) -> finish())
                        .show();
            } else {
                showStatus(message, true);
            }
        };

        AccountManager accounts = new AccountManager(this);
        if (faculty) {
            if (TextUtils.isEmpty(text(etStaffId))) { tilStaffId.setError(getString(R.string.forgot_err_required)); ok = false; }
            if (TextUtils.isEmpty(text(etFaculty))) { tilFaculty.setError(getString(R.string.forgot_err_required)); ok = false; }
            if (!ok) return;
            setSending(true);
            accounts.resetLecturerPassword(login, text(etStaffId), text(etFaculty), password, done);
        } else {
            if (!text(etCode).matches("\\d{6}|[0-9A-Fa-f]{16}")) {
                tilCode.setError(getString(R.string.forgot_err_code));
                ok = false;
            }
            if (!ok) return;
            setSending(true);
            accounts.resetWithCode(login, text(etCode), password, done);
        }
    }

    private void setSending(boolean value) {
        sending = value;
        btnSubmit.setEnabled(!value);
        btnSendCode.setEnabled(!value);
        btnSwitch.setEnabled(!value);
    }
}
