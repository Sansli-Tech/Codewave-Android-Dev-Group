package com.example.mustudentsync.network;

import com.example.mustudentsync.network.dto.DeleteRequest;
import com.example.mustudentsync.network.dto.LecturerRegisterRequest;
import com.example.mustudentsync.network.dto.LoginRequest;
import com.example.mustudentsync.network.dto.LoginResponse;
import com.example.mustudentsync.network.dto.StudentRequest;
import com.example.mustudentsync.network.dto.SyncResponse;
import com.example.mustudentsync.network.dto.GroupListResponse;
import com.example.mustudentsync.network.dto.ClaimCodeResponse;
import com.example.mustudentsync.network.dto.ClaimRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Calls to the StudentSync backend (see API_CONTRACT.md).
 * Lecturer writes go through the offline queue (OutboxRepository / SyncManager).
 */
public interface ApiService {
    @HTTP(method = "DELETE", path = "students/{id}", hasBody = true)
    Call<okhttp3.ResponseBody> deleteStudent(@Header("Authorization") String bearer,
                                             @Path("id") String studentId,
                                             @Body DeleteRequest body);

    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("students")
    Call<okhttp3.ResponseBody> createStudent(@Header("Authorization") String bearer,
                                             @Body StudentRequest body);

    @PUT("students/{id}")
    Call<okhttp3.ResponseBody> updateStudent(@Header("Authorization") String bearer,
                                             @Path("id") String studentId,
                                             @Body StudentRequest body);

    /** Lecturer: one-time claim code for a student (valid 15 minutes). */
    @POST("students/{id}/claim-code")
    Call<ClaimCodeResponse> generateClaimCode(@Header("Authorization") String bearer,
                                              @Path("id") String studentId);

    /** Lecturer: create an account with the administrator's faculty authorization code. */
    @POST("auth/register-lecturer")
    Call<okhttp3.ResponseBody> registerLecturer(@Body LecturerRegisterRequest request);

    /** Student: create an account from a claim code. */
    @POST("auth/claim")
    Call<okhttp3.ResponseBody> claimAccount(@Body ClaimRequest request);

    /** Lab groups with capacity and live counts (lecturer only). */
    @GET("groups")
    Call<GroupListResponse> getGroups(@Header("Authorization") String bearer);

    // ---- Activity E: queued offline changes ----
    // Each one carries its operation ID in the Idempotency-Key header. A retry sends the
    // SAME ID, so if the first reply was lost the server replays it instead of applying
    // the change twice (Challenge 2).

    @POST("students")
    Call<okhttp3.ResponseBody> createStudentOp(@Header("Authorization") String bearer,
                                               @Header("Idempotency-Key") String operationId,
                                               @Body com.google.gson.JsonObject body);

    @PUT("students/{id}")
    Call<okhttp3.ResponseBody> updateStudentOp(@Header("Authorization") String bearer,
                                               @Header("Idempotency-Key") String operationId,
                                               @Path("id") String studentId,
                                               @Body com.google.gson.JsonObject body);

    @HTTP(method = "DELETE", path = "students/{id}", hasBody = true)
    Call<okhttp3.ResponseBody> deleteStudentOp(@Header("Authorization") String bearer,
                                               @Header("Idempotency-Key") String operationId,
                                               @Path("id") String studentId,
                                               @Body com.google.gson.JsonObject body);

    // ---- Student self-service (always the signed-in student's own record) ----

    /** Own profile, confirmed group with occupancy, latest number-correction request. */
    @GET("me/student")
    Call<okhttp3.ResponseBody> getMyProfile(@Header("Authorization") String bearer);

    @PUT("me/student")
    Call<okhttp3.ResponseBody> updateMyProfileOp(@Header("Authorization") String bearer,
                                                 @Header("Idempotency-Key") String operationId,
                                                 @Body com.google.gson.JsonObject body);

    @POST("me/group-request")
    Call<okhttp3.ResponseBody> groupRequestOp(@Header("Authorization") String bearer,
                                              @Header("Idempotency-Key") String operationId,
                                              @Body com.google.gson.JsonObject body);

    @POST("me/correction-requests")
    Call<okhttp3.ResponseBody> correctionRequestOp(@Header("Authorization") String bearer,
                                                   @Header("Idempotency-Key") String operationId,
                                                   @Body com.google.gson.JsonObject body);

    /**
     * Lecturer roster straight from the server: search + programme + group, paginated.
     * Null filters are left out of the URL. group = a code or "UNASSIGNED".
     */
    @GET("students")
    Call<okhttp3.ResponseBody> listStudents(@Header("Authorization") String bearer,
                                            @Query("search") String search,
                                            @Query("programme") String programmeCode,
                                            @Query("group") String group,
                                            @Query("page") int page,
                                            @Query("limit") int limit);

    // ---- Forgot password ----

    /** Lecturer: one-time reset code (16 hex characters, 15 minutes) for a student's account. */
    @POST("students/{id}/reset-code")
    Call<okhttp3.ResponseBody> generateResetCode(@Header("Authorization") String bearer,
                                                 @Path("id") String studentId);

    /** { login }: e-mails a 6-digit code to the account's recovery e-mail. Same reply for every login. */
    @POST("auth/forgot-password")
    Call<okhttp3.ResponseBody> forgotPassword(@Body com.google.gson.JsonObject body);

    /** { login, code, new_password }: code from the e-mail (6 digits) or from the lecturer (16 characters). */
    @POST("auth/reset-with-code")
    Call<okhttp3.ResponseBody> resetWithCode(@Body com.google.gson.JsonObject body);

    /** The signed-in account's recovery e-mail. */
    @GET("account/email")
    Call<okhttp3.ResponseBody> getRecoveryEmail(@Header("Authorization") String bearer);

    /** Signed in: { current_password, new_password }. */
    @PUT("account/password")
    Call<okhttp3.ResponseBody> changePassword(@Header("Authorization") String bearer,
                                              @Body com.google.gson.JsonObject body);

    /** { email } (empty removes it). */
    @PUT("account/email")
    Call<okhttp3.ResponseBody> setRecoveryEmail(@Header("Authorization") String bearer,
                                                @Body com.google.gson.JsonObject body);

    /** Lecturer: { email, staff_id, faculty_token, new_password }. */
    @POST("auth/lecturer-reset-password")
    Call<okhttp3.ResponseBody> resetLecturerPassword(@Body com.google.gson.JsonObject body);

    // ---- Notifications, push, group requests, announcements ----

    @GET("notifications")
    Call<okhttp3.ResponseBody> getNotifications(@Header("Authorization") String bearer, @Query("limit") int limit);

    @GET("notifications/unread-count")
    Call<okhttp3.ResponseBody> unreadCount(@Header("Authorization") String bearer);

    /** { all: true } or { ids: [..] } */
    @POST("notifications/read")
    Call<okhttp3.ResponseBody> markNotificationsRead(@Header("Authorization") String bearer,
                                                     @Body com.google.gson.JsonObject body);

    @POST("devices")
    Call<okhttp3.ResponseBody> registerDevice(@Header("Authorization") String bearer,
                                              @Body com.google.gson.JsonObject body);

    /** No sign-in: the phone keeps getting alerts, but only "Sign in to read it". */
    @POST("devices/signed-out")
    Call<okhttp3.ResponseBody> deviceSignedOut(@Body com.google.gson.JsonObject body);

    @GET("group-requests")
    Call<okhttp3.ResponseBody> getGroupRequests(@Header("Authorization") String bearer, @Query("status") String status);

    /** { decision: APPROVE | REJECT, note? } */
    @POST("group-requests/{id}/resolve")
    Call<okhttp3.ResponseBody> resolveGroupRequestOp(@Header("Authorization") String bearer,
                                                     @Header("Idempotency-Key") String operationId,
                                                     @Path("id") String requestId,
                                                     @Body com.google.gson.JsonObject body);

    @GET("correction-requests")
    Call<okhttp3.ResponseBody> getCorrectionRequests(@Header("Authorization") String bearer);

    @POST("correction-requests/{id}/resolve")
    Call<okhttp3.ResponseBody> resolveCorrectionOp(@Header("Authorization") String bearer,
                                                   @Header("Idempotency-Key") String operationId,
                                                   @Path("id") String requestId,
                                                   @Body com.google.gson.JsonObject body);

    /** { title, body, audience: "ALL" | "G08" } */
    @POST("announcements")
    Call<okhttp3.ResponseBody> sendAnnouncementOp(@Header("Authorization") String bearer,
                                                  @Header("Idempotency-Key") String operationId,
                                                  @Body com.google.gson.JsonObject body);

    @POST("me/group-request/cancel")
    Call<okhttp3.ResponseBody> cancelGroupRequestOp(@Header("Authorization") String bearer,
                                                    @Header("Idempotency-Key") String operationId,
                                                    @Body com.google.gson.JsonObject body);

    // ---- Lecturer: group management and Excel export ----
    // Create and auto-assign carry an Idempotency-Key: a retry after a lost reply is replayed,
    // never applied twice (no second G15, no student moved twice).

    @POST("groups")
    Call<okhttp3.ResponseBody> createGroupOp(@Header("Authorization") String bearer,
                                             @Header("Idempotency-Key") String operationId,
                                             @Body com.google.gson.JsonObject body);

    /** Body: { confirm_students: n }. The group's students become Unassigned; 409 GROUP_CHANGED if n is out of date. */
    @HTTP(method = "DELETE", path = "groups/{code}", hasBody = true)
    Call<okhttp3.ResponseBody> deleteGroupOp(@Header("Authorization") String bearer,
                                             @Header("Idempotency-Key") String operationId,
                                             @Path("code") String groupCode,
                                             @Body com.google.gson.JsonObject body);

    @GET("groups/auto-assign/preview")
    Call<okhttp3.ResponseBody> autoAssignPreview(@Header("Authorization") String bearer);

    @POST("groups/auto-assign")
    Call<okhttp3.ResponseBody> autoAssignOp(@Header("Authorization") String bearer,
                                            @Header("Idempotency-Key") String operationId,
                                            @Body com.google.gson.JsonObject body);

    /** The .xlsx file itself (Summary sheet + one sheet per group). */
    @retrofit2.http.Streaming
    @GET("groups/export.xlsx")
    Call<okhttp3.ResponseBody> exportGroups(@Header("Authorization") String bearer);

    @GET("sync/changes")
    Call<SyncResponse> getChanges(@Header("Authorization") String bearer,
                                  @Query("since") long since,
                                  @Query("limit") int limit);
}


