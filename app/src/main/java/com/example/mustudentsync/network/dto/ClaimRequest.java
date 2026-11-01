package com.example.mustudentsync.network.dto;

import com.google.gson.annotations.SerializedName;

/** Body of POST /api/auth/claim — turns a lecturer-issued claim code into a student account. */
public class ClaimRequest {

    @SerializedName("student_number")
    private final String studentNumber;

    @SerializedName("claim_code")
    private final String claimCode;

    @SerializedName("username")
    private final String username;

    @SerializedName("password")
    private final String password;

    public ClaimRequest(String studentNumber, String claimCode, String username, String password) {
        this.studentNumber = studentNumber;
        this.claimCode = claimCode;
        this.username = username;
        this.password = password;
    }
}