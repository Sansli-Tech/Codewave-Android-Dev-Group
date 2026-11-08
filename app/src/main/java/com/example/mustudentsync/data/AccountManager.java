package com.example.mustudentsync.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.mustudentsync.network.RetrofitClient;
import com.example.mustudentsync.network.dto.ClaimCodeResponse;
import com.example.mustudentsync.network.dto.ClaimRequest;
import com.example.mustudentsync.network.dto.LecturerRegisterRequest;
import com.example.mustudentsync.util.SessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/**
 * Student account creation with claim codes.
 *
 *  1. Lecturer: generateClaimCode() -> POST /api/students/{id}/claim-code
 *     returns a one-time code (16 hex characters) valid for 15 minutes.
 *  2. Student:  claimAccount()      -> POST /api/auth/claim
 *     the server checks the code and creates a STUDENT account linked to the record.
 *
 * Network calls run off the main thread; results come back on the main thread.
 */
public class AccountManager {

    public interface Callback {
        /** On success, message is the claim code (generate) or a confirmation (claim). */
        void onDone(boolean success, String message);
    }

    private final SessionManager session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public AccountManager(Context context) {
        session = new SessionManager(context.getApplicationContext());
    }

    /** Lecturer only. On success the callback's message is the claim code. */
    public void generateClaimCode(String studentId, Callback cb) {
        executor.execute(() -> {
            try {
                Response<ClaimCodeResponse> res = RetrofitClient.getApiService()
                        .generateClaimCode(session.getBearerToken(), studentId)
                        .execute();

                if (res.isSuccessful() && res.body() != null && res.body().claimCode != null) {
                    post(cb, true, res.body().claimCode);
                    return;
                }
                String code = errorCode(res);
                if ("STUDENT_ALREADY_CLAIMED".equals(code)) {
                    post(cb, false, "This student already has an account.");
                } else if (res.code() == 404) {
                    post(cb, false, "This student isn't on the server yet. Sync and try again.");
                } else if (res.code() == 401) {
                    post(cb, false, "Session expired. Sign in again.");
                } else if (res.code() == 403) {
                    post(cb, false, "Only lecturers can generate claim codes.");
                } else {
                    post(cb, false, "Couldn't generate a code (" + res.code() + ").");
                }
            } catch (Exception e) {
                post(cb, false, "Can't reach the server.");
            }
        });
    }

    // ------------------------------------------------------------------ forgot password

    /** Lecturer only. On success the callback's message is the reset code. */
    public void generateResetCode(String studentId, Callback cb) {
        executor.execute(() -> {
            try {
                Response<okhttp3.ResponseBody> res = RetrofitClient.getApiService()
                        .generateResetCode(session.getBearerToken(), studentId)
                        .execute();
                if (res.isSuccessful() && res.body() != null) {
                    com.google.gson.JsonObject o = com.google.gson.JsonParser
                            .parseString(res.body().string()).getAsJsonObject();
                    post(cb, true, o.get("reset_code").getAsString());
                    return;
                }
                if (res.code() == 401) {
                    post(cb, false, "Session expired. Sign in again.");
                } else {
                    String text = serverMessage(res);
                    post(cb, false, text != null ? text : "Couldn't create a reset code (" + res.code() + ").");
                }
            } catch (Exception e) {
                post(cb, false, "Can't reach the server.");
            }
        });
    }

    /** Asks the server to e-mail a code. The reply never says whether the account exists. */
    public void requestResetCode(String login, Callback cb) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("login", login.trim());
        executor.execute(() -> {
            try {
                Response<okhttp3.ResponseBody> res = RetrofitClient.getApiService().forgotPassword(body).execute();
                if (res.isSuccessful() && res.body() != null) {
                    com.google.gson.JsonObject o = com.google.gson.JsonParser
                            .parseString(res.body().string()).getAsJsonObject();
                    post(cb, true, o.has("message") ? o.get("message").getAsString() : "Code sent.");
                    return;
                }
                String text = serverMessage(res);
                post(cb, false, text != null ? text : "Couldn't send a code (" + res.code() + ").");
            } catch (Exception e) {
                post(cb, false, "Can't reach the server. Check your connection and try again.");
            }
        });
    }

    /** Student or lecturer: login + code (e-mailed 6 digits, or 16 characters from the lecturer). */
    public void resetWithCode(String login, String code, String newPassword, Callback cb) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("login", login.trim());
        body.addProperty("code", code.trim().toUpperCase(java.util.Locale.ROOT));
        body.addProperty("new_password", newPassword);
        reset(RetrofitClient.getApiService().resetWithCode(body), cb,
                "Password changed. Sign in with your new password.");
    }

    /** Signed in: the recovery e-mail on the server (message = e-mail, or "" if none). */
    public void getRecoveryEmail(Callback cb) {
        executor.execute(() -> {
            try {
                Response<okhttp3.ResponseBody> res = RetrofitClient.getApiService()
                        .getRecoveryEmail(session.getBearerToken()).execute();
                if (res.isSuccessful() && res.body() != null) {
                    com.google.gson.JsonObject o = com.google.gson.JsonParser
                            .parseString(res.body().string()).getAsJsonObject();
                    post(cb, true, o.has("email") && !o.get("email").isJsonNull() ? o.get("email").getAsString() : "");
                    return;
                }
                post(cb, false, null);
            } catch (Exception e) {
                post(cb, false, null);
            }
        });
    }

    /** Signed in: change password with the current one. */
    public void changePassword(String current, String next, Callback cb) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("current_password", current);
        body.addProperty("new_password", next);
        executor.execute(() -> {
            try {
                Response<okhttp3.ResponseBody> res = RetrofitClient.getApiService()
                        .changePassword(session.getBearerToken(), body).execute();
                if (res.isSuccessful()) {
                    post(cb, true, "Password changed. Use the new password next time you sign in.");
                    return;
                }
                String text = serverMessage(res);
                post(cb, false, res.code() == 401 ? "Session expired. Sign in again."
                        : text != null ? text : "Couldn't change the password (" + res.code() + ").");
            } catch (Exception e) {
                post(cb, false, "Can't reach the server. Changing the password needs a connection.");
            }
        });
    }

    public void setRecoveryEmail(String email, Callback cb) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("email", email.trim());
        executor.execute(() -> {
            try {
                Response<okhttp3.ResponseBody> res = RetrofitClient.getApiService()
                        .setRecoveryEmail(session.getBearerToken(), body).execute();
                if (res.isSuccessful()) {
                    post(cb, true, email.trim().isEmpty() ? "Recovery e-mail removed." : "Recovery e-mail saved.");
                    return;
                }
                String text = serverMessage(res);
                post(cb, false, res.code() == 401 ? "Session expired. Sign in again."
                        : text != null ? text : "Couldn't save (" + res.code() + ").");
            } catch (Exception e) {
                post(cb, false, "Can't reach the server. Saving the e-mail needs a connection.");
            }
        });
    }

    /** Lecturer: reset with staff e-mail, staff ID and the faculty code. */
    public void resetLecturerPassword(String email, String staffId, String facultyCode, String newPassword, Callback cb) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("email", email.trim());
        body.addProperty("staff_id", staffId.trim());
        body.addProperty("faculty_token", facultyCode.trim());
        body.addProperty("new_password", newPassword);
        reset(RetrofitClient.getApiService().resetLecturerPassword(body), cb,
                "Password changed. Sign in with your staff e-mail and new password.");
    }

    private void reset(retrofit2.Call<okhttp3.ResponseBody> call, Callback cb, String okText) {
        executor.execute(() -> {
            try {
                Response<okhttp3.ResponseBody> res = call.execute();
                if (res.isSuccessful()) {
                    post(cb, true, okText);
                    return;
                }
                String text = serverMessage(res);
                post(cb, false, text != null ? text : "The reset failed (" + res.code() + ").");
            } catch (Exception e) {
                post(cb, false, "Can't reach the server. Check your connection and try again.");
            }
        });
    }

    /** The human "message" from an error reply, or null. */
    private static String serverMessage(Response<?> res) {
        try {
            if (res.errorBody() == null) return null;
            com.google.gson.JsonObject o = com.google.gson.JsonParser
                    .parseString(res.errorBody().string()).getAsJsonObject();
            return o.has("message") ? o.get("message").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Student registration. The student number is also used as the username,
     * so the student signs in with their student number and new password.
     */
    public void claimAccount(String studentNumber, String claimCode, String password, Callback cb) {
        executor.execute(() -> {
            try {
                ClaimRequest req = new ClaimRequest(
                        studentNumber,
                        claimCode.trim().toUpperCase(java.util.Locale.ROOT), // codes are uppercase hex
                        studentNumber,
                        password);

                Response<okhttp3.ResponseBody> res = RetrofitClient.getApiService()
                        .claimAccount(req)
                        .execute();

                if (res.isSuccessful()) {
                    post(cb, true, "Account created. Sign in with your student number and password.");
                    return;
                }
                post(cb, false, claimErrorMessage(errorCode(res), res.code()));
            } catch (Exception e) {
                post(cb, false, "Can't reach the server. Your details are kept; try again when online.");
            }
        });
    }

    /** Lecturer registration, protected by the administrator's faculty authorization code. */
    public void registerLecturer(String fullName, String staffId, String email, String school,
                                 String department, String password, String facultyToken, Callback cb) {
        executor.execute(() -> {
            try {
                Response<okhttp3.ResponseBody> res = RetrofitClient.getApiService()
                        .registerLecturer(new LecturerRegisterRequest(
                                fullName, staffId, email, school, department, password, facultyToken))
                        .execute();

                if (res.isSuccessful()) {
                    post(cb, true, "Lecturer account created. Sign in with your staff email.");
                    return;
                }
                String code = errorCode(res);
                if ("INVALID_FACULTY_TOKEN".equals(code)) {
                    post(cb, false, "The faculty authorization code is incorrect.");
                } else if ("ACCOUNT_EXISTS".equals(code)) {
                    post(cb, false, "An account with this email or staff ID already exists.");
                } else if ("LECTURER_REGISTRATION_DISABLED".equals(code)) {
                    post(cb, false, "Lecturer registration isn't enabled on the server. Ask the administrator.");
                } else if ("TOO_MANY_ATTEMPTS".equals(code)) {
                    post(cb, false, "Too many attempts. Try again in 15 minutes.");
                } else if ("VALIDATION_ERROR".equals(code)) {
                    post(cb, false, "Check your details. The password must be at least 12 characters.");
                } else {
                    post(cb, false, "Registration failed (" + res.code() + ").");
                }
            } catch (Exception e) {
                post(cb, false, "Can't reach the server.");
            }
        });
    }

    private static String claimErrorMessage(String code, int status) {
        if ("STUDENT_NOT_FOUND".equals(code)) return "No student with that number. Check it with your lecturer.";
        if ("CLAIM_UNAVAILABLE".equals(code)) return "No claim code is waiting for you. Ask your lecturer for one.";
        if ("CLAIM_EXPIRED".equals(code)) return "That code has expired (codes last 15 minutes). Ask for a new one.";
        if ("INVALID_CLAIM_CODE".equals(code)) return "That claim code is incorrect.";
        if ("ACCOUNT_EXISTS".equals(code)) return "An account already exists for this student number. Sign in instead.";
        if ("VALIDATION_ERROR".equals(code)) return "Check your student number, the 16-character code, and a password of at least 12 characters.";
        return "Registration failed (" + status + ").";
    }

    /** Reads {"error": "CODE"} from an error response, or null. */
    private static String errorCode(Response<?> res) {
        try {
            if (res.errorBody() != null) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser
                        .parseString(res.errorBody().string())
                        .getAsJsonObject();
                if (o.has("error") && o.get("error").isJsonPrimitive()) {
                    return o.get("error").getAsString();
                }
            }
        } catch (Exception ignored) {
            // Not JSON.
        }
        return null;
    }

    private void post(Callback cb, boolean ok, String msg) {
        main.post(() -> {
            if (cb != null) cb.onDone(ok, msg);
        });
    }
}