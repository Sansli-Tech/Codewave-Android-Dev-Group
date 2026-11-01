package com.example.mustudentsync.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mustudentsync.model.PendingOperationEntity;

import java.util.List;

@Dao
public interface PendingOperationDao {

    @Insert
    void insert(PendingOperationEntity op);

    @Update
    void update(PendingOperationEntity op);

    /** Operations still to send for this account, oldest first (order is preserved). */
    @Query("SELECT * FROM pending_operations WHERE account_id = :accountId " +
            "AND status IN ('PENDING', 'SYNCING') ORDER BY created_at ASC, rowid ASC")
    List<PendingOperationEntity> toSend(String accountId);

    /** Unfinished operations (to send or needing review) for one student. */
    @Query("SELECT * FROM pending_operations WHERE account_id = :accountId AND student_id = :studentId " +
            "AND status IN ('PENDING', 'SYNCING', 'ACTION_REQUIRED') ORDER BY created_at ASC, rowid ASC")
    List<PendingOperationEntity> openFor(String accountId, String studentId);

    @Query("SELECT COUNT(*) FROM pending_operations WHERE account_id = :accountId " +
            "AND student_id = :studentId AND status IN ('PENDING', 'SYNCING')")
    int countToSendFor(String accountId, String studentId);

    @Query("SELECT COUNT(*) FROM pending_operations WHERE account_id = :accountId " +
            "AND status IN ('PENDING', 'SYNCING')")
    LiveData<Integer> countWaiting(String accountId);

    @Query("SELECT COUNT(*) FROM pending_operations WHERE account_id = :accountId " +
            "AND status = 'ACTION_REQUIRED'")
    LiveData<Integer> countActionRequired(String accountId);

    /** Whole queue for this account, newest first: the "queue inspection" evidence. */
    @Query("SELECT * FROM pending_operations WHERE account_id = :accountId ORDER BY created_at DESC, rowid DESC")
    List<PendingOperationEntity> allFor(String accountId);

    @Query("DELETE FROM pending_operations WHERE op_id = :opId")
    void deleteById(String opId);
}
