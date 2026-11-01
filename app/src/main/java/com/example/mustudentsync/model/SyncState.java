package com.example.mustudentsync.model;

/**
 * Sync status shown next to a student (Activity E):
 *  SAVED_LOCALLY   - saved on this phone, not sent yet
 *  PENDING         - waiting to be sent (e.g. offline; WorkManager will retry)
 *  SYNCING         - being sent right now
 *  SYNCED          - the server has accepted everything
 *  ACTION_REQUIRED - the server refused the change (conflict, group full, deleted...);
 *                    the lecturer must review it, re-apply or discard it
 */
public final class SyncState {
    public static final String SAVED_LOCALLY = "SAVED_LOCALLY";
    public static final String PENDING = "PENDING";
    public static final String SYNCING = "SYNCING";
    public static final String SYNCED = "SYNCED";
    public static final String ACTION_REQUIRED = "ACTION_REQUIRED";

    private SyncState() { }
}
