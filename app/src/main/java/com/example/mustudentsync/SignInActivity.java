package com.example.mustudentsync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mustudentsync.data.RoomStudentRepository;
import com.example.mustudentsync.data.SyncManager;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.network.ApiService;
import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.network.dto.ErrorResponse;
import com.example.mustudentsync.network.dto.LoginRequest;
import com.example.mustudentsync.network.dto.LoginResponse;
import com.example.mustudentsync.network.dto.StudentDto;
import com.example.mustudentsync.util.ProgrammeCatalog;
import com.example.mustudentsync.util.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignInActivity extends AppCompatActivity {

    private TextInputLayout tilLoginId, tilPassword;
    private TextInputEditText etLoginId, etPassword;
    private TextView tvGenericError;
    private MaterialButton btnSignIn, btnGoToRegister;

    private ApiService apiService;
    private SessionManager sessionManager;

    // The sign-in request in flight, if any. Cancelled when the screen closes so a late
    // reply can never open a screen from a dead Activity.
    private Call<LoginResponse> pendingCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        com.example.mustudentsync.util.FloatingLogo.start(findViewById(R.id.imgSignInLogo));

        apiService = RetrofitClient.getApiService();
        sessionManager = new SessionManager(this);

        initViews();
        setupListeners();

        // Coming from a successful registration: fill in the student number.
        String prefill = getIntent().getStringExtra("PREFILL_LOGIN_ID");
        if (savedInstanceState == null && prefill != null) {
            etLoginId.setText(prefill);
            etPassword.requestFocus();
        }
    }

    private void initViews() {
        tilLoginId = findViewById(R.id.tilLoginId);
        tilPassword = findViewById(R.id.tilPassword);
        etLoginId = findViewById(R.id.etLoginId);
        etPassword = findViewById(R.id.etPassword);
        tvGenericError = findViewById(R.id.tvGenericError);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnGoToRegister = findViewById(R.id.btnGoToRegister);
    }

    private void setupListeners() {
        btnSignIn.setOnClickListener(v -> handleSignIn());
        findViewById(R.id.btnForgotPassword).setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
        btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
    }

    private void handleSignIn() {
        if (pendingCall != null) return; // already signing in

        tvGenericError.setVisibility(View.GONE);
        tilLoginId.setError(null);
        tilPassword.setError(null);

        String loginId = etLoginId.getText() != null ? etLoginId.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        boolean valid = true;
        if (TextUtils.isEmpty(loginId)) {
            tilLoginId.setError(getString(R.string.err_login_id_required));
            valid = false;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.err_password_required));
            valid = false;
        }
        if (!valid) return; // what the user typed stays on screen

        setLoading(true);
        pendingCall = apiService.login(new LoginRequest(loginId, password));
        pendingCall.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                pendingCall = null;
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    onSignedIn(response.body());
                } else {
                    showError(messageFor(response));
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                pendingCall = null;
                if (call.isCanceled()) return; // screen was closed on purpose
                setLoading(false);
                showError(getString(R.string.err_server_unreachable));
            }
        });
    }

    private void onSignedIn(LoginResponse body) {
        String role = body.getRole();

        if ("lecturer".equals(role)) {
            sessionManager.saveSession(body.getToken(), role, null);
            sessionManager.saveUsername(body.getUsername());
            sessionManager.saveAccountId(body.getAccountId());
            // Resume any changes this lecturer saved offline before, and keep syncing in the background.
            com.example.mustudentsync.sync.SyncScheduler.start(getApplicationContext());
            // New task: Back from the lecturer Home can't return to Sign in or the landing page.
            Intent home = new Intent(this, LecturerHomeActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(home);
            finish();
        } else if ("student".equals(role) && body.getStudentId() != null) {
            String studentId = body.getStudentId();
            sessionManager.saveSession(body.getToken(), role, studentId);
            sessionManager.saveUsername(body.getUsername());
            sessionManager.saveAccountId(body.getAccountId());
            // Resume this student's queued requests and keep their profile up to date.
            com.example.mustudentsync.sync.SyncScheduler.start(getApplicationContext());

            // Login no longer returns the student record, so pull it with sync first.
            setLoading(true);
            new SyncManager(getApplicationContext())
                    .syncNow((ok, msg) -> {
                setLoading(false);
                if (!ok) {
                    showError(msg);
                    return;
                }
                Intent intent = new Intent(this, StudentHomeActivity.class);
                intent.putExtra("STUDENT_ID", studentId);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });

        } else {
            showError(getString(R.string.err_server_problem));
        }
    }

    // Turns a failed HTTP reply into a message the user can act on.
    private String messageFor(Response<?> response) {
        String code = null;
        try {
            if (response.errorBody() != null) {
                ErrorResponse err = new Gson().fromJson(response.errorBody().string(), ErrorResponse.class);
                if (err != null) code = err.getCode();
            }
        } catch (IOException | JsonParseException ignored) {
            // no readable error body: fall back to the HTTP status below
        }

        if ("INVALID_CREDENTIALS".equals(code) || response.code() == 401) {
            return getString(R.string.err_invalid_credentials);
        }
        if ("ACCOUNT_DISABLED".equals(code)) {
            return getString(R.string.err_account_disabled);
        }
        if (response.code() == 429) {
            return getString(R.string.err_too_many_attempts);
        }
        return getString(R.string.err_server_problem) + "(HTTP " + response.code() + ")";
    }

    private void setLoading(boolean loading) {
        btnSignIn.setEnabled(!loading);
        btnSignIn.setText(loading ? R.string.btn_signing_in : R.string.btn_sign_in);
    }

    private void showError(String message) {
        tvGenericError.setText(message);
        tvGenericError.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (pendingCall != null) pendingCall.cancel();
        super.onDestroy();
    }
}
