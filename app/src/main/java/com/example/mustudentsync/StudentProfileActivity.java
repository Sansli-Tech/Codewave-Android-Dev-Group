package com.example.mustudentsync;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.viewmodel.StudentProfileViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

public class StudentProfileActivity extends AppCompatActivity {

    private TextView tvStudentFullName, tvStudentNumberDisplay, tvCurrentGroup, tvGroupOccupancy;
    private AutoCompleteTextView actvProfileProgram;
    private Chip chipSyncStatus;
    private MaterialButton btnSaveProfile, btnRequestGroupChange;

    private StudentProfileViewModel viewModel;
    private StudentEntity currentStudent; // kept so Save can read the latest loaded record

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_profile);

        viewModel = new ViewModelProvider(this).get(StudentProfileViewModel.class);
        viewModel.setStudentId(getIntent().getStringExtra("STUDENT_ID"));

        initViews();
        setupProgramDropdown();
        setupActions();

        // Loads the real just-registered student if an id was passed in,
        // otherwise seeds/shows the fictitious demo student.
        viewModel.loadProfile(this::observeProfile);
    }

    private void initViews() {
        tvStudentFullName = findViewById(R.id.tvStudentFullName);
        tvStudentNumberDisplay = findViewById(R.id.tvStudentNumberDisplay);
        tvCurrentGroup = findViewById(R.id.tvCurrentGroup);
        tvGroupOccupancy = findViewById(R.id.tvGroupOccupancy);
        actvProfileProgram = findViewById(R.id.actvProfileProgram);
        chipSyncStatus = findViewById(R.id.chipSyncStatus);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnRequestGroupChange = findViewById(R.id.btnRequestGroupChange);
    }

    private void setupProgramDropdown() {
        ProgramDropdownAdapter adapter = ProgramDropdownAdapter.create(this);
        actvProfileProgram.setAdapter(adapter);
        actvProfileProgram.setOnItemClickListener((parent, view, position, id) -> {
            if (!adapter.isEnabled(position)) {
                actvProfileProgram.setText("", false);
            }
        });
    }

    private void observeProfile() {
        viewModel.getStudent().observe(this, student -> {
            if (student == null) return;
            currentStudent = student;
            tvStudentFullName.setText(student.fullName);
            tvStudentNumberDisplay.setText(
                    getString(R.string.label_student_id_readonly) + ": " + student.studentNumber);
            actvProfileProgram.setText(student.program, false);
            tvCurrentGroup.setText("Group " + student.labGroup);
            tvGroupOccupancy.setText(
                    getString(R.string.label_group_capacity, student.labGroup, 1, 15)); // exact live count: TODO Activity C/D
        });

        viewModel.isPendingGroupChange().observe(this, pending -> {
            if (Boolean.TRUE.equals(pending)) {
                btnRequestGroupChange.setEnabled(false);
                btnRequestGroupChange.setText(R.string.btn_group_change_pending);
                chipSyncStatus.setText(R.string.chip_pending);
            }
        });
    }

    private void setupActions() {
        btnSaveProfile.setOnClickListener(v -> {
            String newProgram = actvProfileProgram.getText().toString();
            viewModel.updateProgram(currentStudent, newProgram);
            chipSyncStatus.setText(R.string.chip_saved_local);
            Toast.makeText(this, "Program preference updated locally", Toast.LENGTH_SHORT).show();
        });

        btnRequestGroupChange.setOnClickListener(v -> viewModel.submitGroupChangeRequest());
    }
}
