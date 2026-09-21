package zm.ac.mu.ict361.studentregistrationapp.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;

import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import zm.ac.mu.ict361.studentregistrationapp.R;

public class RosterActivity extends AppCompatActivity {

    private RecyclerView recyclerRoster;
    private TextView textRosterEmpty;
    private ProgressBar progressRoster;
    private StudentRowAdapter adapter;

    // The full unfiltered dummy dataset
    private final List<StudentRowAdapter.DummyStudent> allStudents = new ArrayList<>();
    // What's currently shown, after search + filters are applied
    private final List<StudentRowAdapter.DummyStudent> visibleStudents = new ArrayList<>();

    private String currentSearchQuery = "";
    private String currentGroupFilter = null;     // null = all groups
    private String currentProgrammeFilter = null; // null = all programmes

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_roster);

        recyclerRoster = findViewById(R.id.recyclerRoster);
        textRosterEmpty = findViewById(R.id.textRosterEmpty);
        progressRoster = findViewById(R.id.progressRoster);

        recyclerRoster.setLayoutManager(new LinearLayoutManager(this));

        seedDummyData();

        adapter = new StudentRowAdapter(visibleStudents, position -> {
            StudentRowAdapter.DummyStudent s = visibleStudents.get(position);
            Intent intent = new Intent(this, StudentEditorActivity.class);
            intent.putExtra(StudentEditorActivity.EXTRA_STUDENT_NAME, s.name);
            intent.putExtra(StudentEditorActivity.EXTRA_STUDENT_NUMBER, s.studentNumber);
            intent.putExtra(StudentEditorActivity.EXTRA_STUDENT_PROGRAMME, s.programme);
            intent.putExtra(StudentEditorActivity.EXTRA_STUDENT_GROUP, s.labGroup);
            startActivity(intent);
        });
        recyclerRoster.setAdapter(adapter);

        setupSearch();
        setupFilterChips();

        applyFilters(); // initial render — shows everything

        findViewById(R.id.fabAddStudent).setOnClickListener(v ->
                startActivity(new Intent(this, StudentEditorActivity.class)));

        SyncStatusView syncStatusBar = findViewById(R.id.syncStatusBar);
        syncStatusBar.setOnRetryListener(v ->
                Toast.makeText(this, "Retrying sync…", Toast.LENGTH_SHORT).show());

// Demo only — cycles through states so you can see them.
// Real state will come from WorkManager/repository once sync exists.
        syncStatusBar.setState(SyncStatusView.State.OFFLINE);
    }

    private void seedDummyData() {
        allStudents.add(new StudentRowAdapter.DummyStudent("Jane Mwansa", "202312345", "Computer Science", "G02"));
        allStudents.add(new StudentRowAdapter.DummyStudent("Brian Phiri", "202312346", "Information Technology", "G02"));
        allStudents.add(new StudentRowAdapter.DummyStudent("Chanda Zulu", "202312347", "Data Science", "G05"));
        allStudents.add(new StudentRowAdapter.DummyStudent("Mutale Banda", "202312348", "Cyber Security", "G05"));
        allStudents.add(new StudentRowAdapter.DummyStudent("Natasha Tembo", "202312349", "Computer Science", "G03"));
    }

    private void setupSearch() {
        TextInputEditText inputSearch = findViewById(R.id.inputRosterSearch);
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                applyFilters();
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterChips() {
        Chip chipGroup = findViewById(R.id.chipGroupFilter);
        Chip chipProgramme = findViewById(R.id.chipProgrammeFilter);

        // Tapping "All groups" toggles between filtering to G02 and clearing the filter —
        // a stand-in until this is a real dropdown/dialog with every group listed.
        chipGroup.setOnClickListener(v -> {
            if (currentGroupFilter == null) {
                currentGroupFilter = "G02";
                chipGroup.setText("G02");
            } else {
                currentGroupFilter = null;
                chipGroup.setText(R.string.filter_all_groups);
            }
            applyFilters();
        });

        chipProgramme.setOnClickListener(v -> {
            if (currentProgrammeFilter == null) {
                currentProgrammeFilter = "Computer Science";
                chipProgramme.setText("Computer Science");
            } else {
                currentProgrammeFilter = null;
                chipProgramme.setText(R.string.filter_all_programmes);
            }
            applyFilters();
        });
    }

    private void applyFilters() {
        visibleStudents.clear();

        for (StudentRowAdapter.DummyStudent s : allStudents) {
            boolean matchesSearch = currentSearchQuery.isEmpty()
                    || s.name.toLowerCase().contains(currentSearchQuery)
                    || s.studentNumber.toLowerCase().contains(currentSearchQuery);

            boolean matchesGroup = currentGroupFilter == null || s.labGroup.equals(currentGroupFilter);
            boolean matchesProgramme = currentProgrammeFilter == null || s.programme.equals(currentProgrammeFilter);

            if (matchesSearch && matchesGroup && matchesProgramme) {
                visibleStudents.add(s);
            }
        }

        adapter.notifyDataSetChanged();

        boolean isEmpty = visibleStudents.isEmpty();
        textRosterEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerRoster.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        progressRoster.setVisibility(View.GONE);
    }
}