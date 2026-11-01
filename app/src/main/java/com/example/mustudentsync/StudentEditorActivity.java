package com.example.mustudentsync;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.data.SyncManager;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.util.GroupCatalog;
import com.example.mustudentsync.util.ProgrammeCatalog;
import com.example.mustudentsync.viewmodel.StudentEditorViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class StudentEditorActivity extends AppCompatActivity {

    private static final String UNASSIGNED = "Unassigned";

    private TextInputLayout tilEditorName, tilEditorNumber, tilEditorProgram;
    private TextInputEditText etEditorName, etEditorNumber;
    private AutoCompleteTextView actvEditorProgram;
    private Spinner spnEditorGroup;
    private TextView tvGroupFullIndicator;
    private MaterialButton btnEditorSave, btnEditorCancel;

    // "Unassigned" followed by every group code (G01–G14), from GroupCatalog.
    private final List<String> groups = new ArrayList<>();

    private StudentEditorViewModel viewModel;

    private StudentEntity editing;   // null when adding a new student
    private boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_editor);

        viewModel = new ViewModelProvider(this).get(StudentEditorViewModel.class);

        initViews();
        setupDropdowns();
        setupListeners();

        String editId = getIntent().getStringExtra("STUDENT_ID");
        if (getIntent().getBooleanExtra("IS_EDIT_MODE", false) && editId != null) {
            viewModel.getStudent(editId).observe(this, s -> {
                if (s == null || editing != null) return;
                editing = s;
                etEditorName.setText(s.fullName);
                etEditorNumber.setText(s.studentNumber);
                actvEditorProgram.setText(s.program, false);
                int index = groups.indexOf(s.labGroup);
                spnEditorGroup.setSelection(index >= 0 ? index : 0);
            });
        }
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

        groups.clear();
        groups.add(UNASSIGNED);
        groups.addAll(GroupCatalog.codes(this));
        spnEditorGroup.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, groups));

        // Selecting a group only checks whether it is full. Saving happens on the Save button.
        spnEditorGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                checkGroupCapacity(groups.get(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    /** Shows the FULL warning and blocks Save if the chosen group has no free place. */
    private void checkGroupCapacity(String group) {
        if (isCurrentGroup(group) || UNASSIGNED.equals(group)) {
            showGroupFull(false, group);
            return;
        }
        btnEditorSave.setEnabled(false); // while checking
        viewModel.isGroupFull(group, isFull -> showGroupFull(isFull, group));
    }

    private void showGroupFull(boolean full, String group) {
        if (full) {
            int cap = GroupCatalog.capacityFor(this, group);
            tvGroupFullIndicator.setText(getString(R.string.group_full_warning, cap, cap));
        }
        tvGroupFullIndicator.setVisibility(full ? View.VISIBLE : View.GONE);
        btnEditorSave.setEnabled(!full && !saving);
    }

    /** A student staying in the group they are already in never counts against capacity. */
    private boolean isCurrentGroup(String group) {
        return editing != null && group.equals(editing.labGroup);
    }

    private void setupListeners() {
        btnEditorCancel.setOnClickListener(v -> finish());
        btnEditorSave.setOnClickListener(v -> validateThenSave());
    }

    private void validateThenSave() {
        String name = etEditorName.getText() != null ? etEditorName.getText().toString().trim() : "";
        String number = etEditorNumber.getText() != null ? etEditorNumber.getText().toString().trim() : "";
        String program = actvEditorProgram.getText() != null ? actvEditorProgram.getText().toString().trim() : "";
        String group = spnEditorGroup.getSelectedItem() != null
                ? spnEditorGroup.getSelectedItem().toString() : UNASSIGNED;

        tilEditorName.setError(null);
        tilEditorNumber.setError(null);
        tilEditorProgram.setError(null);

        boolean valid = true;
        if (TextUtils.isEmpty(name)) {
            tilEditorName.setError(getString(R.string.err_name_empty));
            valid = false;
        }
        if (!number.matches("\\d{9}")) {
            tilEditorNumber.setError(getString(R.string.err_student_number_invalid));
            valid = false;
        }
        if (TextUtils.isEmpty(program) || ProgrammeCatalog.codeFor(program) == null) {
            tilEditorProgram.setError(getString(R.string.err_program_unselected));
            valid = false;
        }
        if (!valid) return;

        if (isCurrentGroup(group) || UNASSIGNED.equals(group)) {
            send(name, number, program, group);
            return;
        }
        // Re-check at the moment of saving, in case the group filled up since it was picked.
        // The server checks again inside a transaction and has the final say (GROUP_FULL).
        btnEditorSave.setEnabled(false);
        viewModel.isGroupFull(group, isFull -> {
            if (isFull) {
                showGroupFull(true, group);
            } else {
                send(name, number, program, group);
            }
        });
    }

    /** Sends the create/update to the server; Room is updated from the server's reply. */
    private void send(String name, String number, String program, String group) {
        saving = true;
        btnEditorSave.setEnabled(false);

        SyncManager.Callback done = (ok, msg) -> {
            saving = false;
            if (ok) {
                Toast.makeText(this, R.string.toast_student_saved, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                btnEditorSave.setEnabled(true);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        };

        SyncManager sync = new SyncManager(this);
        if (editing != null) {
            sync.updateStudent(editing, name, number, program, group, done);
        } else {
            sync.createStudent(name, number, program, group, done);
        }
    }
}