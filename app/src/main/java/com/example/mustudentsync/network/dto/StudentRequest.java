package com.example.mustudentsync.network.dto;

import com.google.gson.annotations.SerializedName;

public class StudentRequest {
    @SerializedName("student_name")    private final String studentName;
    @SerializedName("student_number")  private final String studentNumber;
    @SerializedName("programme_code")  private final String programmeCode;
    @SerializedName("group_code")      private final String groupCode;
    @SerializedName("expected_version") private final Integer expectedVersion;

    public StudentRequest(String studentName, String studentNumber, String programmeCode,
                          String groupCode, Integer expectedVersion) {
        this.studentName = studentName;
        this.studentNumber = studentNumber;
        this.programmeCode = programmeCode;
        this.groupCode = groupCode;
        this.expectedVersion = expectedVersion;
    }
}