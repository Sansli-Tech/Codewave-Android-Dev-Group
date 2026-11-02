package com.example.mustudentsync;

import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.model.SyncState;
import com.example.mustudentsync.sync.SyncScheduler;
import com.example.mustudentsync.util.GroupCatalog;
import com.example.mustudentsync.util.ProgrammeCatalog;
import com.example.mustudentsync.viewmodel.StudentProfileViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Student self-service: own profile, confirmed group with live occupancy, edit name and programme,
 * request a group, request a student-number correction. Every change is saved on the phone first
 * and shown as Saved locally / Pending until the server confirms it; a locally saved group request
 * never reserves a place.
 */
public class StudentProfileActivity extends AppCompatActivity {

    private static final String KEY_POPULATED = "populated";

    private TextView tvStudentFullName, tvStudentNumberDisplay, tvCurrentGroup, tvGroupOccupancy;
    private TextView tvProfileSyncMessage, tvCorrectionStatus;
    private TextInputLayout tilProfileName;
    private TextInputEditText etProfileName;
    private AutoCompleteTextView actvProfileProgram;
    private Chip chipSyncStatus;
    private MaterialButton btnSaveProfile, btnRequestGroupChange, btnRequestCorrection;

    private StudentProfileViewModel viewModel;
    private StudentEntity current;
    /** Fields are filled once; after that (and after rotation) what the student typed is kept. */
    private boolean populated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_profile);
        com.example.mustudentsync.util.BackToExit.install(this); // Back twice to exit, not by accident
        populated = savedInstanceState != null && savedInstanceState.getBoolean(KEY_POPULATED);

        viewModel = new ViewModelProvider(this).get(StudentProfileViewModel.class);
        viewModel.setStudentId(getIntent().getStringExtra("STUDENT_ID"));

        initViews();
        setupProgramDropdown();
        setupActions();

        findViewById(R.id.btnStudentSignOut).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setMessage(R.string.profile_sign_out_confirm)
                        .setPositiveButton(R.string.profile_sign_out, (d, w) -> signOut())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());

        viewModel.getStudent().observe(this, this::showStudent);
        viewModel.getServerInfo().observe(this, this::showServerInfo);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SyncScheduler.requestSync(getApplicationContext());   // send anything waiting, pull updates
        viewModel.refreshServerInfo();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_POPULATED, populated);
    }

    /** Clears the session and this phone's cached data; unsent changes stay queued for this account. */
    private void signOut() {
        com.example.mustudentsync.util.SessionManager session =
                new com.example.mustudentsync.util.SessionManager(this);
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            SyncScheduler.stop(getApplicationContext());
            com.example.mustudentsync.data.local.AppDatabase
                    .clearCachedData(getApplicationContext()); // off the main thread
            session.clearSession();
            main.post(() -> {
                android.content.Intent landing = new android.content.Intent(this, LandingActivity.class);
                landing.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(landing);
                finish();
            });
        });
    }

    private void initViews() {
        tvStudentFullName = findViewById(R.id.tvStudentFullName);
        tvStudentNumberDisplay = findViewById(R.id.tvStudentNumberDisplay);
        tvCurrentGroup = findViewById(R.id.tvCurrentGroup);
        tvGroupOccupancy = findViewById(R.id.tvGroupOccupancy);
        tvProfileSyncMessage = findViewById(R.id.tvProfileSyncMessage);
        tvCorrectionStatus = findViewById(R.id.tvCorrectionStatus);
        tilProfileName = findViewById(R.id.tilProfileName);
        etProfileName = findViewById(R.id.etProfileName);
        actvProfileProgram = findViewById(R.id.actvProfileProgram);
        chipSyncStatus = findViewById(R.id.chipSyncStatus);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnRequestGroupChange = findViewById(R.id.btnRequestGroupChange);
        btnRequestCorrection = findViewById(R.id.btnRequestCorrection);
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

    // ------------------------------------------------------------------ showing state

    private void showStudent(StudentEntity s) {
        if (s == null) return;
        current = s;
        JsonObject pending = pending(s);

        // Confirmed values (what the server has accepted).
        tvStudentFullName.setText(s.fullName);
        tvStudentNumberDisplay.setText(getString(R.string.label_student_id_readonly) + ": " + s.studentNumber);
        tvCurrentGroup.setText(isAssigned(s.labGroup)
                ? getString(R.string.label_group_prefix, s.labGroup)
                : getString(R.string.group_unassigned));

        // The editable fields start from the latest proposal, so the student continues from it.
        if (!populated) {
            String name = pending != null && pending.has("student_name")
                    ? pending.get("student_name").getAsString() : s.fullName;
            String program = pending != null && pending.has("programme_code")
                    ? ProgrammeCatalog.nameFor(pending.get("programme_code").getAsString()) : s.program;
            etProfileName.setText(name);
            actvProfileProgram.setText(program, false);
            populated = true;
        }

        // Status chip: Saved locally / Pending / Syncing / Synced / Action required.
        String state = s.syncState != null ? s.syncState : SyncState.SYNCED;
        chipSyncStatus.setText(labelFor(state));
        boolean actionRequired = SyncState.ACTION_REQUIRED.equals(state);
        chipSyncStatus.setClickable(actionRequired);
        chipSyncStatus.setOnClickListener(actionRequired ? v -> confirmDiscard() : null);

        boolean showMessage = s.syncMessage != null;
        tvProfileSyncMessage.setVisibility(showMessage ? View.VISIBLE : View.GONE);
        if (showMessage) tvProfileSyncMessage.setText(s.syncMessage);

        // A requested group stays a request (Pending) until the server confirms it.
        boolean groupRequested = pending != null && pending.has("group_code") && !SyncState.SYNCED.equals(state);
        if (groupRequested) {
            btnRequestGroupChange.setEnabled(false);
            btnRequestGroupChange.setText(getString(R.string.group_requested,
                    pending.get("group_code").getAsString(), labelFor(state)));
        } else {
            btnRequestGroupChange.setEnabled(true);
            btnRequestGroupChange.setText(R.string.btn_request_group_change);
        }
    }

    private void showServerInfo(StudentProfileViewModel.ServerInfo info) {
        if (info == null) return;
        if (!info.online) {
            tvGroupOccupancy.setText(R.string.group_occupancy_offline);
        } else if (info.groupCode == null) {
            tvGroupOccupancy.setText(R.string.group_unassigned);
        } else {
            boolean full = info.activeStudents >= info.capacity;
            tvGroupOccupancy.setText(getString(full ? R.string.group_occupancy_full : R.string.group_occupancy_live,
                    info.groupCode, info.activeStudents, info.capacity));
        }

        if (info.online && info.correctionNumber != null) {
            String status = "APPROVED".equals(info.correctionStatus) ? getString(R.string.correction_approved)
                    : "REJECTED".equals(info.correctionStatus) ? getString(R.string.correction_rejected)
                    : getString(R.string.correction_pending);
            tvCorrectionStatus.setVisibility(View.VISIBLE);
            tvCorrectionStatus.setText(getString(R.string.correction_status, info.correctionNumber, status));
            btnRequestCorrection.setEnabled(!"PENDING".equals(info.correctionStatus));
        } else if (info.online) {
            tvCorrectionStatus.setVisibility(View.GONE);
            btnRequestCorrection.setEnabled(true);
        }
    }

    // ------------------------------------------------------------------ actions

    private void setupActions() {
        btnSaveProfile.setOnClickListener(v -> saveProfile());
        btnRequestGroupChange.setOnClickListener(v -> chooseGroup());
        btnRequestCorrection.setOnClickListener(v -> askForCorrection());
    }

    private void saveProfile() {
        if (current == null) return;
        tilProfileName.setError(null);
        String name = etProfileName.getText() != null ? etProfileName.getText().toString().trim() : "";
        String program = actvProfileProgram.getText() != null ? actvProfileProgram.getText().toString().trim() : "";

        if (name.length() < 2 || name.length() > 100) {
            tilProfileName.setError(getString(R.string.err_name_length));
            return;   // what the student typed stays in the field
        }
        if (!com.example.mustudentsync.util.NameRule.isValid(name)) {
            tilProfileName.setError(getString(R.string.err_name_characters));
            return;
        }
        if (TextUtils.isEmpty(program) || ProgrammeCatalog.codeFor(program) == null) {
            Toast.makeText(this, R.string.err_program_unselected, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean nameChanged = !name.equals(current.fullName);
        boolean programChanged = !program.equals(current.program);
        if (!nameChanged && !programChanged) {
            Toast.makeText(this, R.string.profile_nothing_changed, Toast.LENGTH_SHORT).show();
            return;
        }
        btnSaveProfile.setEnabled(false);
        viewModel.saveProfile(nameChanged ? name : null, programChanged ? program : null, (ok, msg) -> {
            btnSaveProfile.setEnabled(true);
            Toast.makeText(this, msg, ok ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        });
    }

    private void chooseGroup() {
        if (current == null) return;
        List<String> options = new ArrayList<>();
        for (String code : GroupCatalog.codes(this)) {
            if (!code.equals(current.labGroup)) options.add(code);
        }
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.group_request_none, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = options.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(R.string.group_request_title)
                .setItems(items, (d, which) -> viewModel.requestGroup(items[which], (ok, msg) ->
                        Toast.makeText(this, msg, ok ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show()))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void askForCorrection() {
        TextInputLayout layout = new TextInputLayout(this);
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        layout.setHint(getString(R.string.correction_hint));
        layout.addView(input);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.correction_dialog_title)
                .setMessage(R.string.correction_dialog_message)
                .setView(layout)
                .setPositiveButton(R.string.correction_send, null)   // set below so it can refuse to close
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String number = input.getText() != null ? input.getText().toString().trim() : "";
            if (!number.matches("\\d{9}")) {
                layout.setError(getString(R.string.err_student_number_invalid));
                return;
            }
            if (current != null && number.equals(current.studentNumber)) {
                layout.setError(getString(R.string.correction_same_number));
                return;
            }
            dialog.dismiss();
            viewModel.requestCorrection(number, (ok, msg) ->
                    Toast.makeText(this, msg, ok ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
        }));
        dialog.show();
    }

    private void confirmDiscard() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.profile_discard_title)
                .setMessage(current != null && current.syncMessage != null
                        ? getString(R.string.discard_change_message_with_reason, current.syncMessage)
                        : getString(R.string.discard_change_message))
                .setPositiveButton(R.string.discard_change_confirm, (d, w) -> {
                    populated = false;   // refill the fields from the server's values
                    viewModel.discardChanges((ok, msg) -> Toast.makeText(this,
                            ok ? getString(R.string.discard_change_done) : msg, Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ helpers

    private String labelFor(String state) {
        switch (state) {
            case SyncState.SAVED_LOCALLY: return getString(R.string.sync_state_saved_locally);
            case SyncState.PENDING: return getString(R.string.sync_state_pending);
            case SyncState.SYNCING: return getString(R.string.sync_state_syncing);
            case SyncState.ACTION_REQUIRED: return getString(R.string.sync_state_action_required);
            default: return getString(R.string.chip_synced);
        }
    }

    private static boolean isAssigned(String group) {
        return group != null && !group.isEmpty() && !"Unassigned".equals(group);
    }

    private static JsonObject pending(StudentEntity s) {
        if (s.pendingJson == null) return null;
        try {
            return JsonParser.parseString(s.pendingJson).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
