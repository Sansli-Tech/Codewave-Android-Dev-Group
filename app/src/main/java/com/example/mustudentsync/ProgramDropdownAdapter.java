package com.example.mustudentsync;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Custom adapter for the Program of Study dropdown (AutoCompleteTextView).
 * Displays school names as bold, non-selectable section headers, with each
 * school's programmes listed underneath as selectable rows.
 *
 * Usage:
 *   ProgramDropdownAdapter adapter = ProgramDropdownAdapter.create(this);
 *   actvProgram.setAdapter(adapter);
 *   actvProgram.setOnItemClickListener((parent, view, position, id) -> {
 *       if (!adapter.isEnabled(position)) {
 *           actvProgram.setText("", false); // block header selection
 *       }
 *   });
 */
public class ProgramDropdownAdapter extends ArrayAdapter<String> {

    private final Set<Integer> headerPositions = new HashSet<>();

    public ProgramDropdownAdapter(Context context, List<String> items, Set<Integer> headerPositions) {
        super(context, android.R.layout.simple_dropdown_item_1line, items);
        this.headerPositions.addAll(headerPositions);
    }

    @Override
    public boolean isEnabled(int position) {
        return !headerPositions.contains(position);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return buildRow(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return buildRow(position, convertView, parent);
    }

    private View buildRow(int position, View convertView, ViewGroup parent) {
        TextView row;
        if (convertView instanceof TextView) {
            row = (TextView) convertView;
        } else {
            row = new TextView(getContext());
        }

        String label = getItem(position);
        row.setText(label);
        row.setPadding(32, 24, 32, 24);
        row.setGravity(Gravity.CENTER_VERTICAL);

        if (headerPositions.contains(position)) {
            row.setTypeface(null, Typeface.BOLD);
            row.setTextColor(getContext().getColor(android.R.color.darker_gray));
            row.setClickable(false);
            row.setPadding(24, 28, 32, 8);
        } else {
            row.setTypeface(null, Typeface.NORMAL);
            row.setTextColor(getContext().getColor(android.R.color.black));
            row.setPadding(56, 24, 32, 24); // indent under its header
        }

        return row;
    }

    /**
     * Builds the grouped Mulungushi University programme list.
     * Edit the arrays below to add/remove programmes per school.
     */
    public static ProgramDropdownAdapter create(Context context) {
        List<String> items = new ArrayList<>();
        Set<Integer> headers = new HashSet<>();

        addSchool(items, headers, "School of Engineering and Technology", new String[]{
                "Computer Science",
                "Information Technology",
                "Civil Engineering",
                "Mechanical Engineering",
                "Electrical and Electronics Engineering",
                "Agricultural Engineering",
                "Water Engineering",
                "Industrial Engineering",
                "Geomatics Engineering"
        });

        addSchool(items, headers, "School of Natural and Applied Sciences", new String[]{
                "Mathematics and Statistics",
                "Statistics",
                "Physics",
                "Physics and Mathematics",
                "Chemistry",
                "Biological Sciences",
                "Bio-Chemistry",
                "Laboratory Technology",
                "Natural Resources Management"
        });

        addSchool(items, headers, "School of Medicine and Health Sciences", new String[]{
                "Medicine and Surgery (MBChB)",
                "Biomedical Sciences",
                "Pharmacy"
        });

        return new ProgramDropdownAdapter(context, items, headers);
    }

    /**
     * Flat list of every selectable programme (headers excluded) — used by the
     * Lecturer Roster filter spinner so it stays in sync with this dropdown
     * instead of keeping its own separate, shorter CS/IT/DS list.
     */
    public static String[] getFlatProgramList() {
        java.util.List<String> items = new ArrayList<>();
        java.util.Set<Integer> headers = new HashSet<>();
        addSchool(items, headers, "School of Engineering and Technology", new String[]{
                "Computer Science", "Information Technology", "Civil Engineering",
                "Mechanical Engineering", "Electrical and Electronics Engineering",
                "Agricultural Engineering", "Water Engineering", "Industrial Engineering",
                "Geomatics Engineering"
        });
        addSchool(items, headers, "School of Natural and Applied Sciences", new String[]{
                "Mathematics and Statistics", "Statistics", "Physics", "Physics and Mathematics",
                "Chemistry", "Biological Sciences", "Bio-Chemistry", "Laboratory Technology",
                "Natural Resources Management"
        });
        addSchool(items, headers, "School of Medicine and Health Sciences", new String[]{
                "Medicine and Surgery (MBChB)", "Biomedical Sciences", "Pharmacy"
        });

        java.util.List<String> flat = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!headers.contains(i)) flat.add(items.get(i));
        }
        return flat.toArray(new String[0]);
    }

    private static void addSchool(List<String> items, Set<Integer> headers, String schoolName, String[] programmes) {
        headers.add(items.size());
        items.add(schoolName);
        for (String programme : programmes) {
            items.add(programme);
        }
    }
}
