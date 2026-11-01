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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mustudentsync.data.OutboxRepository;
import com.example.mustudentsync.data.SyncManager;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.viewmodel.LecturerRosterViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

public class LecturerRosterActivity extends AppCompatActivity
        implements StudentRosterAdapter.OnStudentActionListener {


    /** Open the roster already filtered to this group code (e.g. "G01"). */
    public static final String EXTRA_GROUP_FILTER = "GROUP_FILTER";
    /** Open the roster with the search box focused. */
    public static final String EXTRA_FOCUS_SEARCH = "FOCUS_SEARCH";

    private TextInputEditText etSearchRoster;
    private Spinner spnProgramFilter, spnGroupFilter;
    private RecyclerView rvRoster;
    private ProgressBar progressRoster;
    private TextView tvEmptyRoster;
    private FloatingActionButton fabAddStudent;

    private static final String[] FILTER_PROGRAMS =
            buildProgramFilterOptions();

    private static String[] buildProgramFilterOptions() {
        String[] programs = ProgramDropdownAdapter.getFlatProgramList();

        String[] withAllOption = new String[programs.length + 1];
        withAllOption[0] = "All Programs";

        System.arraycopy(
                programs, 0,
                withAllOption, 1,
                programs.length
        );

        return withAllOption;
    }

    // Built from GroupCatalog (server list, cached), so it shows every lab group.
    private final java.util.List<String> filterGroups = new java.util.ArrayList<>();
    private ArrayAdapter<String> groupAdapter;

    private LecturerRosterViewModel viewModel;
    private StudentRosterAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lecturer_roster);

        viewModel = new ViewModelProvider(this)
                .get(LecturerRosterViewModel.class);

        initViews();
        setupFilters();
        setupRecyclerView();
        setupListeners();
        observeRoster();

        com.example.mustudentsync.util.LecturerNav.setup(
                this, findViewById(R.id.bottomNav), R.id.nav_roster);
        applyLaunchOptions();
    }


    /** Group filter / search focus requested by Home or the Groups tab. */
    private void applyLaunchOptions() {
        String group = getIntent().getStringExtra(EXTRA_GROUP_FILTER);
        if (group != null) {
            int index = filterGroups.indexOf(group);
            if (index >= 0) spnGroupFilter.setSelection(index);
        }
        if (getIntent().getBooleanExtra(EXTRA_FOCUS_SEARCH, false)) {
            etSearchRoster.requestFocus();
            getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
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
        ArrayAdapter<String> progAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                FILTER_PROGRAMS
        );

        spnProgramFilter.setAdapter(progAdapter);

        spnProgramFilter.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        viewModel.setProgramFilter(
                                FILTER_PROGRAMS[position]
                        );
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {
                    }
                }
        );

        groupAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                filterGroups
        );
        loadGroupOptions();

        spnGroupFilter.setAdapter(groupAdapter);

        spnGroupFilter.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        viewModel.setGroupFilter(
                                filterGroups.get(position)
                        );
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {
                    }
                }
        );
    }

    /** Fills the group filter: All Groups, every group code, Unassigned. Keeps the current choice. */
    private void loadGroupOptions() {
        Object selected = spnGroupFilter.getSelectedItem();
        filterGroups.clear();
        filterGroups.add("All Groups");
        filterGroups.addAll(com.example.mustudentsync.util.GroupCatalog.codes(this));
        filterGroups.add("Unassigned");
        groupAdapter.notifyDataSetChanged();
        if (selected != null) {
            int index = filterGroups.indexOf(selected.toString());
            if (index >= 0) spnGroupFilter.setSelection(index);
        }
    }
    private void setupRecyclerView() {
        rvRoster.setLayoutManager(
                new LinearLayoutManager(this)
        );

        adapter = new StudentRosterAdapter(this);
        rvRoster.setAdapter(adapter);
    }

    private void observeRoster() {
        viewModel.getRoster().observe(this, students -> {
            progressRoster.setVisibility(View.GONE);

            adapter.submitList(students);

            boolean isEmpty =
                    students == null || students.isEmpty();

            tvEmptyRoster.setVisibility(
                    isEmpty ? View.VISIBLE : View.GONE
            );

            rvRoster.setVisibility(
                    isEmpty ? View.GONE : View.VISIBLE
            );
        });
    }

    private void setupListeners() {
        etSearchRoster.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                viewModel.setSearchQuery(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        fabAddStudent.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LecturerRosterActivity.this,
                    StudentEditorActivity.class
            );

            intent.putExtra("IS_EDIT_MODE", false);
            startActivity(intent);
        });
    }

    @Override
    public void onEditClicked(StudentEntity student) {
        Intent intent = new Intent(
                LecturerRosterActivity.this,
                StudentEditorActivity.class
        );

        intent.putExtra("IS_EDIT_MODE", true);
        intent.putExtra("STUDENT_ID", student.studentId);

        startActivity(intent);
    }

    @Override
    public void onDeleteClicked(StudentEntity student) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(
                        getString(
                                R.string.dialog_delete_message,
                                student.fullName,
                                student.studentNumber
                        )
                )
                .setPositiveButton(
                        R.string.btn_confirm_delete,
                        // Saved on this phone first; the server soft-deletes it when the
                        // queued operation is sent (works offline too).
                        (dialog, which) ->
                                new OutboxRepository(
                                        LecturerRosterActivity.this
                                ).enqueueDelete(student.studentId, (ok, msg) -> {
                                    Toast.makeText(
                                            LecturerRosterActivity.this,
                                            msg,
                                            ok
                                                    ? Toast.LENGTH_SHORT
                                                    : Toast.LENGTH_LONG
                                    ).show();
                                })
                )
                .setNegativeButton(
                        R.string.btn_cancel,
                        null
                )
                .show();
    }

    @Override
    public void onDiscardClicked(StudentEntity student) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.discard_change_title)
                .setMessage(student.syncMessage != null
                        ? getString(R.string.discard_change_message_with_reason, student.syncMessage)
                        : getString(R.string.discard_change_message))
                .setPositiveButton(R.string.discard_change_confirm, (d, w) ->
                        new OutboxRepository(this).discardChanges(student.studentId, (ok, msg) ->
                                Toast.makeText(this,
                                        ok ? getString(R.string.discard_change_done) : msg,
                                        Toast.LENGTH_SHORT).show()))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    @Override
    public void onClaimCodeClicked(StudentEntity student) {
        Toast.makeText(this, R.string.claim_code_generating, Toast.LENGTH_SHORT).show();

        new com.example.mustudentsync.data.AccountManager(this)
                .generateClaimCode(student.studentId, (ok, result) -> {
                    if (isFinishing()) return;
                    if (!ok) {
                        Toast.makeText(LecturerRosterActivity.this, result, Toast.LENGTH_LONG).show();
                        return;
                    }
                    showClaimCodeDialog(student, result);
                });
    }

    /** Shows the code large and selectable, with a Copy button. */
    private void showClaimCodeDialog(StudentEntity student, String code) {
        TextView codeView = new TextView(this);
        codeView.setText(code);
        codeView.setTextIsSelectable(true);
        codeView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26);
        codeView.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        codeView.setLetterSpacing(0.08f);
        codeView.setGravity(android.view.Gravity.CENTER);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        codeView.setPadding(pad, pad / 2, pad, 0);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.claim_dialog_title, student.fullName))
                .setMessage(getString(R.string.claim_dialog_message, student.studentNumber))
                .setView(codeView)
                .setPositiveButton(R.string.claim_dialog_done, null)
                .setNeutralButton(R.string.claim_dialog_copy, (d, w) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Claim code", code));
                        Toast.makeText(this, R.string.claim_code_copied, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        new SyncManager(this).syncNow((ok, msg) -> {
            if (ok) {
                // The sync also refreshed the group list from the server.
                loadGroupOptions();
            } else {
                Toast.makeText(
                        LecturerRosterActivity.this,
                        msg,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }
}
