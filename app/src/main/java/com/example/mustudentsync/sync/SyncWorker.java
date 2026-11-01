package com.example.mustudentsync.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.mustudentsync.data.SyncManager;

/**
 * Background sync run by WorkManager (Activity E). It only starts when the phone has a
 * network connection, survives the app being closed or the phone restarting, and on a
 * network/server failure asks WorkManager to retry with exponential backoff. Every retry
 * re-sends the SAME operation IDs, so nothing is ever applied twice.
 */
public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SyncManager.Report report = new SyncManager(getApplicationContext()).runBlocking();
        switch (report.outcome) {
            case OK:
                return Result.success();
            case RETRY_LATER:
                return Result.retry();
            case SIGN_IN_REQUIRED:
            default:
                // Session expired: stop (sync is paused) until the person signs in again,
                // which schedules a new run. The queued changes stay in Room.
                return Result.failure();
        }
    }
}
