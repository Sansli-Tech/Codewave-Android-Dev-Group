package com.example.mustudentsync.push;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Receives pushes. When the app is closed or in the background, Android shows the alert itself
 * (generic text, private on the lock screen). When the app is open, we show the same generic
 * alert and refresh the badge.
 */
public class MUMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        PushRegistrar.send(this, token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        String body = message.getNotification() != null ? message.getNotification().getBody() : null;
        NotificationCenter.showGeneric(this, body);
        String unread = message.getData().get("unread");
        if (unread != null) {
            try {
                NotificationCenter.setUnread(Integer.parseInt(unread));
            } catch (NumberFormatException ignored) {
                NotificationCenter.refreshUnread(this);
            }
        } else {
            NotificationCenter.refreshUnread(this);
        }
    }
}
