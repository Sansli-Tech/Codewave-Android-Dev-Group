package com.example.mustudentsync.network.dto;

import java.util.List;

public class SyncResponse {
    public boolean success;
    public List<Change> changes;
    public Info sync;

    public static class Change {
        public long changeId;
        public String studentId;
        public int recordVersion;
        public String changeType;   // "UPSERT" or "DELETE"
        public Student student;     // null for DELETE
    }
    public static class Student {
        public String studentId, studentNumber, studentName, programmeCode, groupCode;
        public int recordVersion;
    }
    public static class Info {
        public long since, nextSince;
        public int limit;
        public boolean hasMore;
    }
}