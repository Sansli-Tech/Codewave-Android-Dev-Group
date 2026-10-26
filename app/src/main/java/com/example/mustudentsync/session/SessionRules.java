package com.example.mustudentsync.session;

// Decides when the app must treat the session as expired.
// The team's server answers 401 for a missing, invalid or expired token,
// and also 401 for a wrong password on login. Only the first kind means
// the session is over.
public class SessionRules {

    private static final String LOGIN_PATH = "/auth/login";

    public static boolean isSessionExpired(int httpStatus, String requestPath) {
        if (httpStatus != 401) return false;
        if (requestPath == null) return true;
        return !requestPath.contains(LOGIN_PATH);
    }
}