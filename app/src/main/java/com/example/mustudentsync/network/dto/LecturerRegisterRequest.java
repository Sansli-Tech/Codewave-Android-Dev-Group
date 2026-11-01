package com.example.mustudentsync.network.dto;

import com.google.gson.annotations.SerializedName;

/** Body of POST /api/auth/register-lecturer. The staff email becomes the sign-in username. */
public class LecturerRegisterRequest {

    @SerializedName("full_name") private final String fullName;
    @SerializedName("staff_id") private final String staffId;
    @SerializedName("email") private final String email;
    @SerializedName("school") private final String school;
    @SerializedName("department") private final String department;
    @SerializedName("password") private final String password;
    @SerializedName("faculty_token") private final String facultyToken;

    public LecturerRegisterRequest(String fullName, String staffId, String email, String school,
                                   String department, String password, String facultyToken) {
        this.fullName = fullName;
        this.staffId = staffId;
        this.email = email;
        this.school = school;
        this.department = department;
        this.password = password;
        this.facultyToken = facultyToken;
    }
}