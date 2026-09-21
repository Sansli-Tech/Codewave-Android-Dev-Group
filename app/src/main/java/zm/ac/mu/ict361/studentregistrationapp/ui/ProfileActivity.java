package zm.ac.mu.ict361.studentregistrationapp.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import zm.ac.mu.ict361.studentregistrationapp.R;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        SyncStatusView syncStatusBar = findViewById(R.id.syncStatusBar);
        syncStatusBar.setOnRetryListener(v ->
                Toast.makeText(this, "Retrying sync…", Toast.LENGTH_SHORT).show());

// Demo only — swap states to preview each look.
        syncStatusBar.setState(SyncStatusView.State.SYNCED);

        // Real name/programme/group/student-number binding comes once
        // the ViewModel + repository exist — this screen is UI-only for now.
    }
}