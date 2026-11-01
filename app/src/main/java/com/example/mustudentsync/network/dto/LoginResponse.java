package com.example.mustudentsync.network.dto;

/** Reply to POST /auth/login: { token, user: { role, studentId, username } }. */
public class LoginResponse {

    private String token;
    private User user;

    public String getToken() { return token; }

    /** Lowercased so the app can compare against "lecturer" / "student". */
    public String getRole() {
        return user != null && user.role != null ? user.role.toLowerCase() : null;
    }

    public String getStudentId() { return user != null ? user.studentId : null; }

    public String getUsername() { return user != null ? user.username : null; }

    private static class User {
        String role;
        String studentId;
        String username;
    }
}