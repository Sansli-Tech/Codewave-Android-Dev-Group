package com.example.mustudentsync.push;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mustudentsync.LandingActivity;
import com.example.mustudentsync.R;
import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.util.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Phone alerts and the unread count shown on the bell / Notifications tab.
 *
 * PRIVACY: alerts never contain names, numbers or message text (the server only sends
 * "You have N unread notifications", or "Sign in to read it" when signed out). The alert is
 * VISIBILITY_PRIVATE, and its lock-screen version says only "MUStudentSync". Tapping it opens
 * the app, which asks for sign-in if needed; the details are read only after that.
 */
public final class NotificationCenter {

    public static final String CHANNEL_ID = "mu_updates";
    private static final int ALERT_ID = 1001;

    private static final MutableLiveData<Integer> UNREAD = new MutableLiveData<>(0);
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NotificationCenter() { }

    /** Live unread count for badges (bell, Notifications tab). */
    public static LiveData<Integer> unread() {
        return UNREAD;
    }

    public static void setUnread(int n) {
        if (Looper.myLooper() == Looper.getMainLooper()) UNREAD.setValue(Math.max(0, n));
        else UNREAD.postValue(Math.max(0, n));
    }

    /** Created once at app start (MUSyncApp). */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.notif_channel_description));
        channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /** Shows the generic alert (used when a push arrives while the app is open). */
    public static void showGeneric(Context context, String body) {
        Intent open = new Intent(context, LandingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // What the lock screen shows instead of the real alert.
        android.app.Notification publicVersion = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .build();
        NotificationCompat.Builder alert = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(body != null ? body : context.getString(R.string.notif_generic))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setContentIntent(tap)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(ALERT_ID, alert.build());
        } catch (SecurityException noPermission) {
            // The user didn't allow notifications: the in-app badge still shows the count.
        }
    }

    /** Asks the server for the unread count (signed in only). */
    public static void refreshUnread(Context context) {
        Context app = context.getApplicationContext();
        String bearer = new SessionManager(app).getBearerToken();
        if (bearer == null) return;
        IO.execute(() -> {
            try {
                Response<ResponseBody> res = RetrofitClient.getApiService().unreadCount(bearer).execute();
                if (res.isSuccessful() && res.body() != null) {
                    JsonObject o = JsonParser.parseString(res.body().string()).getAsJsonObject();
                    int n = o.get("unread").getAsInt();
                    MAIN.post(() -> setUnread(n));
                }
            } catch (Exception ignored) {
                // offline: keep the last known count
            }
        });
    }
}
