package zm.ac.mu.ict361.studentregistrationapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import zm.ac.mu.ict361.studentregistrationapp.R;

public class StudentEditorActivity extends AppCompatActivity {

    public static final String EXTRA_STUDENT_NAME = "extra_student_name";
    public static final String EXTRA_STUDENT_NUMBER = "extra_student_number";
    public static final String EXTRA_STUDENT_PROGRAMME = "extra_student_programme";
    public static final String EXTRA_STUDENT_GROUP = "extra_student_group";

    private boolean isEditMode = false;

    private TextInputEditText inputName, inputNumber, inputGroup;
    private AutoCompleteTextView inputProgramme;
    private TextInputLayout layoutName, layoutNumber, layoutProgramme, layoutGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_editor);

        inputName = findViewById(R.id.inputEditorName);
        inputNumber = findViewById(R.id.inputEditorNumber);
        inputGroup = findViewById(R.id.inputEditorGroup);
        inputProgramme = findViewById(R.id.inputEditorProgramme);

        layoutName = findViewById(R.id.layoutEditorName);
        layoutNumber = findViewById(R.id.layoutEditorNumber);
        layoutProgramme = findViewById(R.id.layoutEditorProgramme);
        layoutGroup = findViewById(R.id.layoutEditorGroup);

        ArrayAdapter<String> programmeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.programmes_list)
        );
        inputProgramme.setAdapter(programmeAdapter);

        MaterialButton btnSave = findViewById(R.id.btnSaveStudent);
        MaterialButton btnDelete = findViewById(R.id.btnDeleteStudent);

        String existingName = getIntent().getStringExtra(EXTRA_STUDENT_NAME);
        isEditMode = existingName != null;

        if (isEditMode) {
            ((android.widget.TextView) findViewById(R.id.textEditorTitle))
                    .setText(R.string.editor_title_edit);

            inputName.setText(existingName);
            inputNumber.setText(getIntent().getStringExtra(EXTRA_STUDENT_NUMBER));
            inputProgramme.setText(getIntent().getStringExtra(EXTRA_STUDENT_PROGRAMME), false);
            inputGroup.setText(getIntent().getStringExtra(EXTRA_STUDENT_GROUP));

            // Student number stays editable here since this is the lecturer's
            // editor, not the student's own locked profile view.
            btnDelete.setVisibility(android.view.View.VISIBLE);
        }

        btnSave.setOnClickListener(v -> {
            if (!validateFields()) return;

            // TODO: persist via repository once Room is wired in.
            // For now this just proves the form + validation works.
            Toast.makeText(this,
                    (isEditMode ? "Updated " : "Added ") + inputName.getText(),
                    Toast.LENGTH_SHORT).show();
            finish();
        });

        btnDelete.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete student")
                    .setMessage("Remove " + inputName.getText() + " from the roster?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        // TODO: real delete via repository once Room is wired in.
                        Toast.makeText(this, "Deleted " + inputName.getText(), Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

    }

    private boolean validateFields() {
        boolean valid = true;

        if (inputName.getText() == null || inputName.getText().toString().trim().isEmpty()) {
            layoutName.setError(getString(R.string.error_field_required));
            valid = false;
        } else {
            layoutName.setError(null);
        }

        if (inputNumber.getText() == null || inputNumber.getText().toString().trim().isEmpty()) {
            layoutNumber.setError(getString(R.string.error_field_required));
            valid = false;
        } else {
            layoutNumber.setError(null);
        }

        if (inputProgramme.getText() == null || inputProgramme.getText().toString().trim().isEmpty()) {
            layoutProgramme.setError(getString(R.string.error_field_required));
            valid = false;
        } else {
            layoutProgramme.setError(null);
        }

        if (inputGroup.getText() == null || inputGroup.getText().toString().trim().isEmpty()) {
            layoutGroup.setError(getString(R.string.error_field_required));
            valid = false;
        } else {
            layoutGroup.setError(null);
        }

        return valid;
    }
}