package com.example.mustudentsync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mustudentsync.model.StudentEntity;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class StudentRosterAdapter extends RecyclerView.Adapter<StudentRosterAdapter.StudentViewHolder> {

    public interface OnStudentActionListener {
        void onEditClicked(StudentEntity student);
        void onDeleteClicked(StudentEntity student);
    }

    private List<StudentEntity> students = new ArrayList<>();
    private final OnStudentActionListener listener;

    public StudentRosterAdapter(OnStudentActionListener listener) {
        this.listener = listener;
    }

    /** Call this from the Activity's LiveData observer whenever the roster changes. */
    public void submitList(List<StudentEntity> newList) {
        this.students = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged(); // simple approach; swap for DiffUtil later if the list grows large
    }

    @NonNull
    @Override
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_roster_student, parent, false);
        return new StudentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        StudentEntity student = students.get(position);
        holder.name.setText(student.fullName);
        holder.details.setText(String.format("ID: %s • Program: %s", student.studentNumber, student.program));
        holder.groupChip.setText(student.labGroup);

        holder.editButton.setOnClickListener(v -> listener.onEditClicked(student));
        holder.deleteButton.setOnClickListener(v -> listener.onDeleteClicked(student));
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    static class StudentViewHolder extends RecyclerView.ViewHolder {
        TextView name, details;
        Chip groupChip;
        ImageButton editButton, deleteButton;

        StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvItemStudentName);
            details = itemView.findViewById(R.id.tvItemStudentDetails);
            groupChip = itemView.findViewById(R.id.chipItemGroup);
            editButton = itemView.findViewById(R.id.btnItemEdit);
            deleteButton = itemView.findViewById(R.id.btnItemDelete);
        }
    }
}