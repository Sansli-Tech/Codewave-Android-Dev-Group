package com.example.mustudentsync.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.mustudentsync.model.PendingOperationEntity;
import com.example.mustudentsync.model.RegistrationDraftEntity;
import com.example.mustudentsync.model.StudentEntity;

@Database(
        entities = {StudentEntity.class, RegistrationDraftEntity.class, PendingOperationEntity.class},
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract StudentDao studentDao();
    public abstract DraftDao draftDao();
    public abstract PendingOperationDao pendingOperationDao();

    /**
     * Version 1 -> 2 (Activity E). Adds the offline-change columns to students and the
     * pending_operations queue. Existing students and drafts are kept: nothing is dropped,
     * and every existing student starts as SYNCED.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `students` ADD COLUMN `sync_state` TEXT NOT NULL DEFAULT 'SYNCED'");
            db.execSQL("ALTER TABLE `students` ADD COLUMN `pending_json` TEXT");
            db.execSQL("ALTER TABLE `students` ADD COLUMN `sync_message` TEXT");
            db.execSQL("CREATE TABLE IF NOT EXISTS `pending_operations` ("
                    + "`op_id` TEXT NOT NULL, "
                    + "`account_id` TEXT NOT NULL, "
                    + "`student_id` TEXT NOT NULL, "
                    + "`type` TEXT NOT NULL, "
                    + "`payload_json` TEXT, "
                    + "`base_version` INTEGER NOT NULL, "
                    + "`status` TEXT NOT NULL, "
                    + "`created_at` INTEGER NOT NULL, "
                    + "`attempts` INTEGER NOT NULL, "
                    + "`last_error` TEXT, "
                    + "`server_result` TEXT, "
                    + "PRIMARY KEY(`op_id`))");
            db.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_pending_operations_account_id_status_created_at` "
                    + "ON `pending_operations` (`account_id`, `status`, `created_at`)");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "mustudentsync.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Sign-out / session timeout: removes the cached roster and drafts so the next person
     * on this phone never sees them. The queue of unsent changes is KEPT: it is scoped by
     * account, is only ever sent for the account that created it, and resumes when that
     * person signs in again. Must run off the main thread.
     */
    public static void clearCachedData(Context context) {
        AppDatabase db = getInstance(context);
        db.runInTransaction(() -> {
            db.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM students");
            db.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM registration_draft");
        });
    }
}
