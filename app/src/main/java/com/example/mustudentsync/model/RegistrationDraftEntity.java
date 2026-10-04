package com.example.mustudentsync.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Holds an in-progress Register form so it survives process death, not just rotation.
 * There is only ever one draft at a time in this lab's scope, so a fixed id is used.
 */
@Entity(tableName = "registration_draft")
public class RegistrationDraftEntity {

    @PrimaryKey
    public int id = 1; // single-row table; always overwritten, never inserted twice

    public String fullName;
    public String studentNumber;
    public String program;
    public String claimCode;
    public long savedAtMillis;
}