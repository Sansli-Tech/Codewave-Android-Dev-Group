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

    @GET("sync/changes")
    Call<SyncResponse> getChanges(@Header("Authorization") String bearer,
                                  @Query("since") long since,
                                  @Query("limit") int limit);
}


