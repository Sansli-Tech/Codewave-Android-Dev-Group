package com.example.mustudentsync.sync;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Schedules SyncWorker: once after every saved change, and every 15 minutes while signed in. */
public final class SyncScheduler {

    private static final String ONE_TIME = "mustudentsync-sync-now";
    private static final String PERIODIC = "mustudentsync-sync-periodic";

    private SyncScheduler() { }

    private static Constraints online() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    /** Sync as soon as there is a network connection (called after each saved change). */
    public static void requestSync(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(online())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        // APPEND_OR_REPLACE: a change saved while a sync is running gets its own run afterwards.
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    /** Call after sign-in and at app start when signed in: periodic sync plus one run now. */
    public static void start(Context context) {
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(online())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic);
        requestSync(context);
    }

    /** Sign-out: stop background sync. The queue itself is kept for when this person returns. */
    public static void stop(Context context) {
        WorkManager wm = WorkManager.getInstance(context.getApplicationContext());
        wm.cancelUniqueWork(ONE_TIME);
        wm.cancelUniqueWork(PERIODIC);
    }
}
