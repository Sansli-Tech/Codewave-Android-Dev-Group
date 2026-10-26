package com.example.mustudentsync.share;

import android.app.Activity;
import android.content.Intent;

// Opens the Android Sharesheet with the group summary text.
// The text holds only group codes and counts, no personal data.
public class ShareHelper {

    public static void shareSummary(Activity activity, String summaryText) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, summaryText);
        send.putExtra(Intent.EXTRA_TITLE, "Lab group summary");
        activity.startActivity(Intent.createChooser(send, "Share group summary"));
    }
}