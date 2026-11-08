package com.example.mustudentsync.push;

import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mustudentsync.R;

/** The bell on the lecturer's Home: a red count of unread notifications (hidden at 0). */
public final class BellBadge {

    private BellBadge() { }

    public static void bind(AppCompatActivity activity, View bellFrame, Runnable onOpen) {
        View button = bellFrame.findViewById(R.id.btnBell);
        TextView badge = bellFrame.findViewById(R.id.tvBellBadge);
        button.setOnClickListener(v -> onOpen.run());
        NotificationCenter.unread().observe(activity, n -> {
            int count = n != null ? n : 0;
            badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
            // TalkBack: "Notifications, 3 unread"
            button.setContentDescription(count > 0
                    ? activity.getString(R.string.bell_description_unread, count)
                    : activity.getString(R.string.bell_description));
        });
    }
}
