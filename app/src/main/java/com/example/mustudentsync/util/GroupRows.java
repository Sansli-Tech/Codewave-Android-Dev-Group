package com.example.mustudentsync.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.mustudentsync.R;
import com.example.mustudentsync.data.local.GroupCount;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the lab-group rows (code, "12/15", capacity bar) used on Home and the Groups tab. */
public final class GroupRows {

    public static class Row {
        public final String code;
        public final int count;
        public final int capacity;

        Row(String code, int count, int capacity) {
            this.code = code;
            this.count = count;
            this.capacity = capacity;
        }

        public boolean isFull() { return count >= capacity; }
        double fill() { return capacity > 0 ? (double) count / capacity : 0; }
    }

    public interface OnGroupClick {
        void onClick(String groupCode);
    }

    private GroupRows() { }

    /** Every known group, in order, with its active-student count (0 if none). */
    public static List<Row> build(Context context, List<GroupCount> counts) {
        Map<String, Integer> byCode = new HashMap<>();
        if (counts != null) {
            for (GroupCount c : counts) {
                if (c.labGroup != null) byCode.put(c.labGroup, c.total);
            }
        }
        List<Row> rows = new ArrayList<>();
        for (String code : GroupCatalog.codes(context)) {
            Integer n = byCode.get(code);
            rows.add(new Row(code, n != null ? n : 0, GroupCatalog.capacityFor(context, code)));
        }
        return rows;
    }

    /** The fullest groups first (for "Groups filling up" on Home). */
    public static List<Row> fullestFirst(List<Row> rows, int limit) {
        List<Row> sorted = new ArrayList<>(rows);
        Collections.sort(sorted, (a, b) -> Double.compare(b.fill(), a.fill()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public static void render(LinearLayout container, List<Row> rows, OnGroupClick onClick) {
        Context context = container.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        container.removeAllViews();

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            View item = inflater.inflate(R.layout.item_group_capacity, container, false);
            TextView code = item.findViewById(R.id.tvGroupCode);
            TextView count = item.findViewById(R.id.tvGroupCount);
            LinearProgressIndicator bar = item.findViewById(R.id.barGroupCapacity);

            code.setText(row.code);
            boolean full = row.isFull();
            count.setText(context.getString(full ? R.string.group_full_format : R.string.group_count_format,
                    row.count, row.capacity));
            int color = ContextCompat.getColor(context, full ? R.color.campus_full : R.color.campus_blue);
            count.setTextColor(full ? color : ContextCompat.getColor(context, R.color.campus_muted));
            bar.setIndicatorColor(color);
            bar.setProgress((int) Math.round(Math.min(1.0, row.fill()) * 100));

            item.setContentDescription(row.code + ", " + count.getText());
            if (onClick != null) {
                item.setOnClickListener(v -> onClick.onClick(row.code));
            } else {
                item.setClickable(false);
                item.setBackground(null);
            }

            if (i > 0) {
                View divider = new View(context);
                divider.setBackgroundColor(ContextCompat.getColor(context, R.color.campus_line));
                container.addView(divider, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
            container.addView(item);
        }
    }
}
