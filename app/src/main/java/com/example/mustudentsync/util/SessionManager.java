package com.example.mustudentsync.util;

import android.content.Context;
import android.content.SharedPreferences;

// A thin wrapper around SharedPreferences for the logged-in user's session.
// Not encrypted storage -- fine for this assignment's scope, but worth knowing
// that EncryptedSharedPreferences exists if your brief later asks for it.
public class SessionManager {

    private static final String PREFS_NAME = "mu_studentsync_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ROLE = "role";
    private static final String KEY_STUDENT_ID = "student_id";

    private static final String KEY_SYNC_CURSOR = "sync_cursor";

    public long getSyncCursor() { return prefs.getLong(KEY_SYNC_CURSOR, 0L); }
    public void saveSyncCursor(long c) { prefs.edit().putLong(KEY_SYNC_CURSOR, c).apply(); }

    private static final String KEY_USERNAME = "username";
    private static final String KEY_ACCOUNT_ID = "account_id";

    public void saveAccountId(String accountId) { prefs.edit().putString(KEY_ACCOUNT_ID, accountId).apply(); }

    /**
     * Who owns local changes. The queue of offline changes is scoped by this value, so a
     * different person signing in on the same phone never replays someone else's changes.
     * Falls back to the username for sessions saved before account IDs were stored.
     */
    public String getAccountKey() {
        String id = prefs.getString(KEY_ACCOUNT_ID, null);
        return id != null ? id : getUsername();
    }
    private static final String KEY_LAST_SYNC = "last_sync_millis";

    public void saveUsername(String username) { prefs.edit().putString(KEY_USERNAME, username).apply(); }
    public String getUsername() { return prefs.getString(KEY_USERNAME, null); }

    /** Time of the last successful sync (System.currentTimeMillis()), 0 if never. */
    public long getLastSync() { return prefs.getLong(KEY_LAST_SYNC, 0L); }
    public void saveLastSync(long millis) { prefs.edit().putLong(KEY_LAST_SYNC, millis).apply(); }

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, String role, String studentId) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_ROLE, role)
                .putString(KEY_STUDENT_ID, studentId)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    // Convenience: formatted ready to drop straight into an Authorization header.
    public String getBearerToken() {
        String token = getToken();
        return token != null ? "Bearer " + token : null;
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }

    public String getStudentId() {
        return prefs.getString(KEY_STUDENT_ID, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
