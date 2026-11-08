package com.example.mustudentsync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.data.AccountManager;
import com.example.mustudentsync.viewmodel.RegisterViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

/**
 * Student registration with a lecturer-issued claim code.
 *
 * The lecturer generates a one-time code for an existing student record
 * (roster -> ⋮ -> Generate claim code). The student enters their student number,
 * that code and a new password; the server checks the code and creates the account
 * (POST /api/auth/claim). The student number is the username for signing in.
 *
 * Student number and claim code are kept as a Room draft so they survive rotation
 * and process death. Passwords are never saved.
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String KEY_STUDENT_NUM_ERROR = "key_student_num_error";
    private static final String KEY_CLAIM_ERROR = "key_claim_error";
    private static final String KEY_PASSWORD_ERROR = "key_password_error";
    private static final String KEY_CONFIRM_ERROR = "key_confirm_error";

    private TextInputLayout tilStudentNumber, tilClaimCode, tilPassword, tilConfirmPassword;
    private TextInputEditText etStudentNumber, etClaimCode, etPassword, etConfirmPassword;
    private MaterialCardView cardOfflineDraftBanner;
    private MaterialButton btnRegister, btnGoToSignIn, btnRegisterAsLecturer;

    private RegisterViewModel viewModel;
    private boolean submitting = false;
    private boolean registered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        com.example.mustudentsync.util.FloatingLogo.start(findViewById(R.id.imgRegisterLogo));

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);

        initViews();
        setupListeners();

        if (savedInstanceState != null) {
            // Recreated by rotation: restore inline errors (field text is restored by Android).
            tilStudentNumber.setError(savedInstanceState.getString(KEY_STUDENT_NUM_ERROR));
            tilClaimCode.setError(savedInstanceState.getString(KEY_CLAIM_ERROR));
            tilPassword.setError(savedInstanceState.getString(KEY_PASSWORD_ERROR));
            tilConfirmPassword.setError(savedInstanceState.getString(KEY_CONFIRM_ERROR));
        } else {
            // Fresh launch (e.g. after process death): restore any saved draft from Room.
            restoreDraftIfPresent();
        }
    }

    private void restoreDraftIfPresent() {
        viewModel.loadDraftFromRoom(draft -> {
            if (draft != null && (!TextUtils.isEmpty(draft.studentNumber) || !TextUtils.isEmpty(draft.claimCode))) {
                etStudentNumber.setText(draft.studentNumber);
                etClaimCode.setText(draft.claimCode);
                cardOfflineDraftBanner.setVisibility(View.VISIBLE);
            }
        });
    }

    private void initViews() {
        tilStudentNumber = findViewById(R.id.tilStudentNumber);
        tilClaimCode = findViewById(R.id.tilClaimCode);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etStudentNumber = findViewById(R.id.etStudentNumber);
        etClaimCode = findViewById(R.id.etClaimCode);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        cardOfflineDraftBanner = findViewById(R.id.cardOfflineDraftBanner);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoToSignIn = findViewById(R.id.btnGoToSignIn);
        btnRegisterAsLecturer = findViewById(R.id.btnRegisterAsLecturer);
    }

    private void setupListeners() {
        btnRegister.setOnClickListener(v -> validateAndSubmit());
        btnGoToSignIn.setOnClickListener(v -> {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
        });
        btnRegisterAsLecturer.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterLecturerActivity.class));
            finish();
        });
    }

    private static String text(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }

    private void validateAndSubmit() {
        if (submitting) return;

        tilStudentNumber.setError(null);
        tilClaimCode.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);

        String number = text(etStudentNumber).trim();
        String claimCode = text(etClaimCode).trim().toUpperCase(Locale.ROOT);
        String password = text(etPassword);
        String confirm = text(etConfirmPassword);

        boolean isValid = true;
        if (!number.matches("\\d{9}")) {
            tilStudentNumber.setError(getString(R.string.err_student_number_invalid));
            isValid = false;
        }
        if (!claimCode.matches("[0-9A-F]{16}")) {
            tilClaimCode.setError(getString(R.string.err_claim_code_format));
            isValid = false;
        }
        if (password.length() < 12) {
            tilPassword.setError(getString(R.string.err_password_short));
            isValid = false;
        }
        if (!password.equals(confirm)) {
            tilConfirmPassword.setError(getString(R.string.err_password_mismatch));
            isValid = false;
        }
        if (!isValid) return;

        setSubmitting(true);
        Toast.makeText(this, R.string.msg_registering, Toast.LENGTH_SHORT).show();

        new AccountManager(this).claimAccount(number, claimCode, password, (ok, message) -> {
            if (isFinishing()) return;
            setSubmitting(false);

            if (ok) {
                registered = true;
                viewModel.clearDraft();
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, SignInActivity.class);
                intent.putExtra("PREFILL_LOGIN_ID", number);
                startActivity(intent);
                finish();
            } else {
                // Show code problems on the code field; everything else as a message.
                if (message.contains("code")) {
                    tilClaimCode.setError(message);
                } else if (message.contains("student number")) {
                    tilStudentNumber.setError(message);
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        btnRegister.setEnabled(!value);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (registered) return;
        // Keep the student number and claim code so they survive process death.
        // The password is deliberately never stored.
        String number = text(etStudentNumber);
        String claimCode = text(etClaimCode);
        if (!TextUtils.isEmpty(number) || !TextUtils.isEmpty(claimCode)) {
            viewModel.saveDraftToRoom("", number, "", claimCode);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (tilStudentNumber.getError() != null) outState.putString(KEY_STUDENT_NUM_ERROR, tilStudentNumber.getError().toString());
        if (tilClaimCode.getError() != null) outState.putString(KEY_CLAIM_ERROR, tilClaimCode.getError().toString());
        if (tilPassword.getError() != null) outState.putString(KEY_PASSWORD_ERROR, tilPassword.getError().toString());
        if (tilConfirmPassword.getError() != null) outState.putString(KEY_CONFIRM_ERROR, tilConfirmPassword.getError().toString());
    }
}