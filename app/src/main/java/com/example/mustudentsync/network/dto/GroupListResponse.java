package com.example.mustudentsync.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Response of GET /api/groups: { "groups": [ { group_code, capacity, active_students, ... } ] } */
public class GroupListResponse {

    @SerializedName("groups")
    public List<Group> groups;

    public static class Group {
        @SerializedName("group_code")
        public String groupCode;

        @SerializedName("capacity")
        public int capacity;

        @SerializedName("active_students")
        public int activeStudents;
    }
}