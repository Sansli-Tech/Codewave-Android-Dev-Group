package zm.ac.mu.ict361.studentregistrationapp.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import zm.ac.mu.ict361.studentregistrationapp.R;

public class SyncStatusView extends LinearLayout {

    public enum State { HIDDEN, OFFLINE, SYNCING, SYNCED, CONFLICT, ERROR }

    private View root;
    private ProgressBar spinner;
    private ImageView icon;
    private TextView text;

    private OnClickListener retryListener;

    public SyncStatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_sync_status, this, true);
        root = findViewById(R.id.syncStatusRoot);
        spinner = findViewById(R.id.syncStatusSpinner);
        icon = findViewById(R.id.syncStatusIcon);
        text = findViewById(R.id.syncStatusText);
    }

    public void setState(State state) {
        // Reset per-state visuals before applying the new state
        spinner.setVisibility(GONE);
        icon.setVisibility(GONE);
        root.setOnClickListener(null);
        root.setBackgroundColor(0x00000000);

        switch (state) {
            case HIDDEN:
                root.setVisibility(GONE);
                return;

            case OFFLINE:
                root.setVisibility(VISIBLE);
                icon.setVisibility(VISIBLE);
                icon.setImageResource(android.R.drawable.stat_sys_warning);
                text.setText(R.string.sync_status_offline);
                root.setBackgroundColor(0x33FF9800); // translucent amber
                break;

            case SYNCING:
                root.setVisibility(VISIBLE);
                spinner.setVisibility(VISIBLE);
                text.setText(R.string.sync_status_syncing);
                root.setBackgroundColor(0x332196F3); // translucent blue
                break;

            case SYNCED:
                root.setVisibility(VISIBLE);
                icon.setVisibility(VISIBLE);
                icon.setImageResource(android.R.drawable.presence_online);
                text.setText(R.string.sync_status_synced);
                root.setBackgroundColor(0x334CAF50); // translucent green
                break;

            case CONFLICT:
                root.setVisibility(VISIBLE);
                icon.setVisibility(VISIBLE);
                icon.setImageResource(android.R.drawable.stat_notify_error);
                text.setText(R.string.sync_status_conflict);
                root.setBackgroundColor(0x33F44336); // translucent red
                if (retryListener != null) root.setOnClickListener(retryListener);
                break;

            case ERROR:
                root.setVisibility(VISIBLE);
                icon.setVisibility(VISIBLE);
                icon.setImageResource(android.R.drawable.stat_notify_error);
                text.setText(R.string.sync_status_error);
                root.setBackgroundColor(0x33F44336);
                if (retryListener != null) root.setOnClickListener(retryListener);
                break;
        }
    }

    public void setOnRetryListener(OnClickListener listener) {
        this.retryListener = listener;
    }
}