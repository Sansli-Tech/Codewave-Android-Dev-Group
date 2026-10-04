package com.example.mustudentsync.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Durable local record of a student, mirrored from / to the server.
 * studentId is an immutable internal identifier — separate from studentNumber,
 * which a lecturer is allowed to correct without breaking ownership/history.
 */
@Entity(tableName = "students")
public class StudentEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "student_id")
    public String studentId; // immutable, generated once (e.g. UUID), never reused

    @ColumnInfo(name = "student_number")
    public String studentNumber; // 9-digit string, unique, editable only by lecturer

    @ColumnInfo(name = "full_name")
    public String fullName;

    @ColumnInfo(name = "program")
    public String program; // full program name from the grouped dropdown

    @ColumnInfo(name = "lab_group")
    public String labGroup; // G01–G04 or "Unassigned"

    @ColumnInfo(name = "is_active")
    public boolean isActive = true; // false once soft-deleted

    @ColumnInfo(name = "record_version")
    public int recordVersion = 1; // used later for conflict detection on sync

    public StudentEntity(@NonNull String studentId, String studentNumber, String fullName,
                         String program, String labGroup) {
        this.studentId = studentId;
        this.studentNumber = studentNumber;
        this.fullName = fullName;
        this.program = program;
        this.labGroup = labGroup;
    }
}