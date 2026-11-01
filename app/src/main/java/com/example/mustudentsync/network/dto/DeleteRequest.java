package com.example.mustudentsync.network.dto;

import com.google.gson.annotations.SerializedName;

public class DeleteRequest {
    @SerializedName("expected_version")
    private final int expectedVersion;

    public DeleteRequest(int expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}