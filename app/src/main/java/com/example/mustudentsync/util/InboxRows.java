package com.example.mustudentsync.util;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.mustudentsync.R;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Builds the rows of the inbox / notifications lists (a few dozen at most, so no RecyclerView). */
public final class InboxRows {

    private InboxRows() { }

    public interface Action {
        void run();
    }

    /** Adds one row. Pass null actions for an information-only row. */
    public static View add(LinearLayout list, String title, String body, String meta, boolean unread,
                           String positive, Action onPositive, String negative, Action onNegative) {
        Context c = list.getContext();
        View row = LayoutInflater.from(c).inflate(R.layout.item_inbox_row, list, false);
        ((TextView) row.findViewById(R.id.tvInboxTitle)).setText(title);
        TextView tvBody = row.findViewById(R.id.tvInboxBody);
        tvBody.setText(body);
        tvBody.setVisibility(body == null || body.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) row.findViewById(R.id.tvInboxMeta)).setText(meta);
        row.findViewById(R.id.dotInboxUnread).setVisibility(unread ? View.VISIBLE : View.INVISIBLE);
        if (unread) row.setBackgroundColor(ContextCompat.getColor(c, R.color.campus_tile));

        MaterialButton yes = row.findViewById(R.id.btnInboxPositive);
        MaterialButton no = row.findViewById(R.id.btnInboxNegative);
        boolean actions = onPositive != null || onNegative != null;
        row.findViewById(R.id.rowInboxActions).setVisibility(actions ? View.VISIBLE : View.GONE);
        if (onPositive != null) {
            yes.setText(positive);
            yes.setOnClickListener(v -> onPositive.run());
        } else {
            yes.setVisibility(View.GONE);
        }
        if (onNegative != null) {
            no.setText(negative);
            no.setOnClickListener(v -> onNegative.run());
        } else {
            no.setVisibility(View.GONE);
        }
        // TalkBack reads the row as one sentence before the buttons.
        row.findViewById(R.id.groupInboxText).setContentDescription(
                (unread ? c.getString(R.string.inbox_unread_prefix) + " " : "") + title + ". "
                        + (body != null ? body + ". " : "") + meta);
        list.addView(row);
        return row;
    }

    public static void empty(LinearLayout list, String text) {
        TextView tv = new TextView(list.getContext());
        int pad = (int) (16 * list.getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(list.getContext(), R.color.campus_muted));
        list.addView(tv);
    }

    /** "5 min ago", "Yesterday" ... from the server's UTC timestamp. */
    public static String when(String iso) {
        if (iso == null) return "";
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso.length() >= 19 ? iso.substring(0, 19) : iso);
            if (d == null) return "";
            return DateUtils.getRelativeTimeSpanString(d.getTime(), System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS).toString();
        } catch (Exception e) {
            return "";
        }
    }
}
