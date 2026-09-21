package zm.ac.mu.ict361.studentregistrationapp.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import zm.ac.mu.ict361.studentregistrationapp.R;

public class SignInActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        MaterialButton btnGoToRegister = findViewById(R.id.btnGoToRegister);
        btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        MaterialButtonToggleGroup toggleRole = findViewById(R.id.toggleRole);
        toggleRole.check(R.id.btnRoleStudent); // default selection

        MaterialButton btnSignIn = findViewById(R.id.btnSignIn);
        btnSignIn.setOnClickListener(v -> {
            int checkedId = toggleRole.getCheckedButtonId();
            if (checkedId == R.id.btnRoleLecturer) {
                startActivity(new Intent(this, RosterActivity.class));
            } else {
                startActivity(new Intent(this, ProfileActivity.class));
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
