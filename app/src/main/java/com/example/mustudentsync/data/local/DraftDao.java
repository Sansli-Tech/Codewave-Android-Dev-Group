package com.example.mustudentsync.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.mustudentsync.model.RegistrationDraftEntity;

@Dao
public interface DraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveDraft(RegistrationDraftEntity draft);

    @Query("SELECT * FROM registration_draft WHERE id = 1 LIMIT 1")
    RegistrationDraftEntity getDraft(); // synchronous — called once on screen open, off main thread

    @Delete
    void clearDraft(RegistrationDraftEntity draft);

    @Query("DELETE FROM registration_draft")
    void clearAllDrafts();
}