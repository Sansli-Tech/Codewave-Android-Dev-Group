package com.example.mustudentsync;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SignInActivity extends AppCompatActivity {

    private TextInputLayout tilLoginId, tilPassword;
    private TextInputEditText etLoginId, etPassword;
    private TextView tvGenericError;
    private MaterialButton btnSignIn, btnGoToRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        initViews();
        setupListeners();

        // TODO: LoginViewModel = new ViewModelProvider(this).get(LoginViewModel.class);
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
        btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });
    }

    private void handleSignIn() {
        tvGenericError.setVisibility(View.GONE);
        tilLoginId.setError(null);
        tilPassword.setError(null);

        String loginId = etLoginId.getText() != null ? etLoginId.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (TextUtils.isEmpty(loginId)) {
            tilLoginId.setError(getString(R.string.err_invalid_credentials));
            return;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.err_invalid_credentials));
            return;
        }

        // TEMPORARY for UI testing — real role check comes from the server in Activity C/D.
        // Type "lecturer" as the login ID to preview the Lecturer Roster screen.
        if (loginId.equalsIgnoreCase("lecturer")) {
            startActivity(new Intent(this, LecturerRosterActivity.class));
        } else {
            startActivity(new Intent(this, StudentProfileActivity.class));
        }
        finish();
    }
}