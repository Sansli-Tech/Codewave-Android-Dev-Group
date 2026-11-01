package com.example.mustudentsync.network.dto;

import com.google.gson.annotations.SerializedName;

/** Response of POST /api/students/{id}/claim-code (lecturer only). */
public class ClaimCodeResponse {

    @SerializedName("claim_code")
    public String claimCode;

    @SerializedName("expires_in_minutes")
    public int expiresInMinutes;
}