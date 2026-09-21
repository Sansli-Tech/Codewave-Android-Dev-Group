package zm.ac.mu.ict361.studentregistrationapp.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import zm.ac.mu.ict361.studentregistrationapp.R;

public class StudentRowAdapter extends RecyclerView.Adapter<StudentRowAdapter.RowHolder> {

    public interface OnEditClickListener {
        void onEditClicked(int position);
    }

    private final List<DummyStudent> students;
    private final OnEditClickListener editListener;

    public StudentRowAdapter(List<DummyStudent> students, OnEditClickListener editListener) {
        this.students = students;
        this.editListener = editListener;
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_student_row, parent, false);
        return new RowHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        DummyStudent s = students.get(position);
        holder.name.setText(s.name);
        holder.details.setText(s.studentNumber + " · " + s.programme + " · " + s.labGroup);
        holder.editButton.setOnClickListener(v -> {
            if (editListener != null) editListener.onEditClicked(position);
        });
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        TextView name, details;
        ImageButton editButton;

        RowHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.textRowName);
            details = itemView.findViewById(R.id.textRowDetails);
            editButton = itemView.findViewById(R.id.btnRowEdit);
        }
    }

    // Throwaway dummy model — replaced by the real Student entity later
    public static class DummyStudent {
        public String name, studentNumber, programme, labGroup;

        public DummyStudent(String name, String studentNumber, String programme, String labGroup) {
            this.name = name;
            this.studentNumber = studentNumber;
            this.programme = programme;
            this.labGroup = labGroup;
        }
    }
}
