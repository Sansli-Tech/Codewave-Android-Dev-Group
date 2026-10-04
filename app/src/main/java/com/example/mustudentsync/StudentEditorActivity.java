package com.example.mustudentsync;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.mustudentsync.viewmodel.StudentEditorViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class StudentEditorActivity extends AppCompatActivity {

    private TextInputLayout tilEditorName, tilEditorNumber, tilEditorProgram;
    private TextInputEditText etEditorName, etEditorNumber;
    private AutoCompleteTextView actvEditorProgram;
    private Spinner spnEditorGroup;
    private TextView tvGroupFullIndicator;
    private MaterialButton btnEditorSave, btnEditorCancel;

    private static final String[] GROUPS = new String[]{"Unassigned", "G01", "G02", "G03", "G04"};

    private StudentEditorViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_editor);

        viewModel = new ViewModelProvider(this).get(StudentEditorViewModel.class);

        initViews();
        setupDropdowns();
        setupListeners();
    }

    private void initViews() {
        tilEditorName = findViewById(R.id.tilEditorName);
        tilEditorNumber = findViewById(R.id.tilEditorNumber);
        tilEditorProgram = findViewById(R.id.tilEditorProgram);
        etEditorName = findViewById(R.id.etEditorName);
        etEditorNumber = findViewById(R.id.etEditorNumber);
        actvEditorProgram = findViewById(R.id.actvEditorProgram);
        spnEditorGroup = findViewById(R.id.spnEditorGroup);
        tvGroupFullIndicator = findViewById(R.id.tvGroupFullIndicator);
        btnEditorSave = findViewById(R.id.btnEditorSave);
        btnEditorCancel = findViewById(R.id.btnEditorCancel);
    }

    private void setupDropdowns() {
        ProgramDropdownAdapter programAdapter = ProgramDropdownAdapter.create(this);
        actvEditorProgram.setAdapter(programAdapter);
        actvEditorProgram.setOnItemClickListener((parent, view, position, id) -> {
            if (!programAdapter.isEnabled(position)) {
                actvEditorProgram.setText("", false);
            }
        });

        spnEditorGroup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, GROUPS));

        spnEditorGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedGroup = GROUPS[position];
                btnEditorSave.setEnabled(false); // disable while checking, re-enabled by the callback
                viewModel.isGroupFull(selectedGroup, isFull -> {
                    tvGroupFullIndicator.setVisibility(isFull ? View.VISIBLE : View.GONE);
                    btnEditorSave.setEnabled(!isFull);
                });
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupListeners() {
        btnEditorCancel.setOnClickListener(v -> finish());
        btnEditorSave.setOnClickListener(v -> validateThenSave());
    }

    private void validateThenSave() {
        String name = etEditorName.getText() != null ? etEditorName.getText().toString().trim() : "";
        String number = etEditorNumber.getText() != null ? etEditorNumber.getText().toString().trim() : "";
        String program = actvEditorProgram.getText() != null ? actvEditorProgram.getText().toString().trim() : "";
        String group = spnEditorGroup.getSelectedItem().toString();

        boolean valid = true;
        if (TextUtils.isEmpty(name)) {
            tilEditorName.setError(getString(R.string.err_name_empty));
            valid = false;
        }
        if (TextUtils.isEmpty(number) || number.length() != 9) {
            tilEditorNumber.setError(getString(R.string.err_student_number_invalid));
            valid = false;
        }
        if (TextUtils.isEmpty(program)) {
            tilEditorProgram.setError(getString(R.string.err_program_unselected));
            valid = false;
        }

        if (!valid) return;

        // Re-check capacity at the moment of saving (off the main thread), in case
        // the group filled up between selection and tapping Save.
        viewModel.isGroupFull(group, isFull -> {
            if (isFull) {
                tvGroupFullIndicator.setVisibility(View.VISIBLE);
                return;
            }
            // TODO (Activity C/D): branch between saveNewStudent and updateExistingStudent
            // based on the IS_EDIT_MODE intent extra once a real "edit" flow exists.
            viewModel.saveNewStudent(name, number, program, group);
            finish();
        });
    }
}
