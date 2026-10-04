package com.example.mustudentsync;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.viewmodel.LecturerRosterViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

public class LecturerRosterActivity extends AppCompatActivity
        implements StudentRosterAdapter.OnStudentActionListener {

    private TextInputEditText etSearchRoster;
    private Spinner spnProgramFilter, spnGroupFilter;
    private RecyclerView rvRoster;
    private ProgressBar progressRoster;
    private TextView tvEmptyRoster;
    private FloatingActionButton fabAddStudent;

    private static final String[] FILTER_PROGRAMS = buildProgramFilterOptions();

    private static String[] buildProgramFilterOptions() {
        String[] programs = ProgramDropdownAdapter.getFlatProgramList();
        String[] withAllOption = new String[programs.length + 1];
        withAllOption[0] = "All Programs";
        System.arraycopy(programs, 0, withAllOption, 1, programs.length);
        return withAllOption;
    }
    private static final String[] FILTER_GROUPS = new String[]{"All Groups", "G01", "G02", "G03", "G04", "Unassigned"};

    private LecturerRosterViewModel viewModel;
    private StudentRosterAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lecturer_roster);

        viewModel = new ViewModelProvider(this).get(LecturerRosterViewModel.class);

        initViews();
        setupFilters();
        setupRecyclerView();
        setupListeners();
        observeRoster();
    }

    private void initViews() {
        etSearchRoster = findViewById(R.id.etSearchRoster);
        spnProgramFilter = findViewById(R.id.spnProgramFilter);
        spnGroupFilter = findViewById(R.id.spnGroupFilter);
        rvRoster = findViewById(R.id.rvRoster);
        progressRoster = findViewById(R.id.progressRoster);
        tvEmptyRoster = findViewById(R.id.tvEmptyRoster);
        fabAddStudent = findViewById(R.id.fabAddStudent);
    }

    private void setupFilters() {
        ArrayAdapter<String> progAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, FILTER_PROGRAMS);
        spnProgramFilter.setAdapter(progAdapter);
        spnProgramFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                viewModel.setProgramFilter(FILTER_PROGRAMS[pos]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, FILTER_GROUPS);
        spnGroupFilter.setAdapter(groupAdapter);
        spnGroupFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                viewModel.setGroupFilter(FILTER_GROUPS[pos]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }

    private void setupRecyclerView() {
        rvRoster.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentRosterAdapter(this);
        rvRoster.setAdapter(adapter);
    }

    private void observeRoster() {
        viewModel.getRoster().observe(this, students -> {
            progressRoster.setVisibility(View.GONE);
            adapter.submitList(students);
            boolean isEmpty = students == null || students.isEmpty();
            tvEmptyRoster.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            rvRoster.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });
    }

    private void setupListeners() {
        etSearchRoster.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setSearchQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        fabAddStudent.setOnClickListener(v -> {
            Intent intent = new Intent(this, StudentEditorActivity.class);
            intent.putExtra("IS_EDIT_MODE", false);
            startActivity(intent);
        });
    }

    @Override
    public void onEditClicked(StudentEntity student) {
        Intent intent = new Intent(this, StudentEditorActivity.class);
        intent.putExtra("IS_EDIT_MODE", true);
        intent.putExtra("STUDENT_ID", student.studentId);
        startActivity(intent);
    }

    @Override
    public void onDeleteClicked(StudentEntity student) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, student.fullName, student.studentNumber))
                .setPositiveButton(R.string.btn_confirm_delete, (dialog, which) -> viewModel.deleteStudent(student))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
