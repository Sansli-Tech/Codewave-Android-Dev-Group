package com.example.mustudentsync.share;

public class GroupCount {
    public final String groupCode;
    public final int active;
    public final int capacity;

    public GroupCount(String groupCode, int active, int capacity) {
        this.groupCode = groupCode;
        this.active = active;
        this.capacity = capacity;
    }
}