package com.example.mustudentsync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.model.SyncState;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class StudentRosterAdapter extends RecyclerView.Adapter<StudentRosterAdapter.StudentViewHolder> {

    // Unchanged: LecturerRosterActivity keeps implementing exactly this interface.
    public interface OnStudentActionListener {
        void onEditClicked(StudentEntity student);
        void onDeleteClicked(StudentEntity student);
        void onClaimCodeClicked(StudentEntity student);
        /** Drop this student's unsent or refused change and go back to the server's values. */
        void onDiscardClicked(StudentEntity student);
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
        holder.details.setText(String.format("ID: %s \u2022 Program: %s", student.studentNumber, student.program));
        holder.groupChip.setText(student.labGroup);
        bindSyncState(holder, student);

        // TalkBack reads "More options for <name>" so each row's button is distinguishable.
        holder.overflowButton.setContentDescription(
                holder.itemView.getContext().getString(R.string.roster_more_options_for, student.fullName));
        holder.overflowButton.setOnClickListener(v -> showOverflowMenu(v, student));
    }

    /**
     * Activity E status label: Saved locally / Pending / Syncing / Action required.
     * Synced students show no label. The row itself shows the CONFIRMED values; a
     * proposed change is only described here until the server accepts it.
     */
    private void bindSyncState(StudentViewHolder holder, StudentEntity student) {
        android.content.Context ctx = holder.itemView.getContext();
        String state = student.syncState != null ? student.syncState : SyncState.SYNCED;
        boolean pendingDelete = student.pendingJson != null && student.pendingJson.contains("\"delete\"");
        boolean neverConfirmed = student.recordVersion == 0;

        String label;
        switch (state) {
            case SyncState.SAVED_LOCALLY:
                label = ctx.getString(R.string.sync_state_saved_locally);
                break;
            case SyncState.PENDING:
                label = ctx.getString(R.string.sync_state_pending);
                break;
            case SyncState.SYNCING:
                label = ctx.getString(R.string.sync_state_syncing);
                break;
            case SyncState.ACTION_REQUIRED:
                label = ctx.getString(R.string.sync_state_action_required);
                break;
            default:
                label = null;
        }
        if (label != null && pendingDelete) {
            label = ctx.getString(R.string.sync_state_delete_prefix, label);
        } else if (label != null && neverConfirmed) {
            label = ctx.getString(R.string.sync_state_new_prefix, label);
        } else if (label != null && student.pendingJson != null) {
            label = ctx.getString(R.string.sync_state_edit_prefix, label);
        }

        if (label == null) {
            holder.syncState.setVisibility(View.GONE);
        } else {
            holder.syncState.setVisibility(View.VISIBLE);
            holder.syncState.setText(SyncState.ACTION_REQUIRED.equals(state) && student.syncMessage != null
                    ? label + " \u2014 " + student.syncMessage
                    : label);
            holder.syncState.setTextColor(androidx.core.content.ContextCompat.getColor(ctx,
                    SyncState.ACTION_REQUIRED.equals(state) ? R.color.campus_warn_text : R.color.campus_ok_text));
        }
    }

    // Opens the little Edit / Claim code / Delete popup anchored to the 3-dot button.
    private void showOverflowMenu(View anchor, StudentEntity student) {
        PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.menu_roster_item, popup.getMenu());
        // "Discard my change" only makes sense while a change is unsent or was refused.
        boolean hasChange = student.syncState != null && !SyncState.SYNCED.equals(student.syncState);
        popup.getMenu().findItem(R.id.action_discard_change).setVisible(hasChange);
        // A student created offline has no server record yet, so no claim code.
        popup.getMenu().findItem(R.id.action_claim_code).setVisible(student.recordVersion > 0);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit_student) {
                listener.onEditClicked(student);       // opens the Student Editor, as before
                return true;
            } else if (id == R.id.action_claim_code) {
                listener.onClaimCodeClicked(student);  // lecturer issues a one-time claim code
                return true;
            } else if (id == R.id.action_delete_student) {
                listener.onDeleteClicked(student);     // was in the menu but never handled before
                return true;
            } else if (id == R.id.action_discard_change) {
                listener.onDiscardClicked(student);
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    static class StudentViewHolder extends RecyclerView.ViewHolder {
        TextView name, details, syncState;
        Chip groupChip;
        ImageButton overflowButton;

        StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvItemStudentName);
            details = itemView.findViewById(R.id.tvItemStudentDetails);
            groupChip = itemView.findViewById(R.id.chipItemGroup);
            overflowButton = itemView.findViewById(R.id.btnItemOverflow);
            syncState = itemView.findViewById(R.id.tvItemSyncState);
        }
    }
}
