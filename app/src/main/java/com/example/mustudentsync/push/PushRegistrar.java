package com.example.mustudentsync.push;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.util.SessionManager;
import com.google.gson.JsonObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tells the server which phone to send alerts to.
 *   Signed in  -> POST /api/devices            (alerts say how many notifications are unread)
 *   Signed out -> POST /api/devices/signed-out (alerts say only "Sign in to read it")
 * If Firebase isn't set up (no google-services.json), everything here quietly does nothing.
 */
public final class PushRegistrar {

    private static final String PREFS = "mu_push";
    private static final String KEY_TOKEN = "fcm_token";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private PushRegistrar() { }

    /** After sign-in (and whenever the app starts while signed in). */
    public static void register(Context context) {
        Context app = context.getApplicationContext();
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> send(app, token));
        } catch (Exception | NoClassDefFoundError firebaseNotSetUp) {
            // No google-services.json: push is off, in-app notifications still work.
        }
    }

    /** Firebase gave this phone a new token. */
    public static void send(Context context, String token) {
        Context app = context.getApplicationContext();
        prefs(app).edit().putString(KEY_TOKEN, token).apply();
        String bearer = new SessionManager(app).getBearerToken();
        if (bearer == null) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", token);
        IO.execute(() -> {
            try {
                RetrofitClient.getApiService().registerDevice(bearer, body).execute();
            } catch (Exception ignored) {
                // tried again at the next sign-in / app start
            }
        });
    }

    /** Call BEFORE the session is cleared. Alerts continue, but without any detail. */
    public static void signedOut(Context context) {
        String token = prefs(context.getApplicationContext()).getString(KEY_TOKEN, null);
        if (token == null) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", token);
        IO.execute(() -> {
            try {
                RetrofitClient.getApiService().deviceSignedOut(body).execute();
            } catch (Exception ignored) {
                // offline: the server still never sends details in alerts
            }
        });
        NotificationCenter.setUnread(0);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
