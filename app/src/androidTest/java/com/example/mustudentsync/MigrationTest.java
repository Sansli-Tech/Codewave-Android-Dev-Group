package com.example.mustudentsync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.mustudentsync.data.local.AppDatabase;
import com.example.mustudentsync.model.PendingOperationEntity;
import com.example.mustudentsync.model.RegistrationDraftEntity;
import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.model.SyncState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * Activity E evidence: "Test one Room migration without deleting existing or pending data."
 *
 * Builds a database exactly as version 1 of the app created it (before offline sync), with a
 * synced student and an unfinished registration draft. Then opens it with the version 2
 * AppDatabase and MIGRATION_1_2. Room checks that every migrated table matches the new entities
 * exactly (otherwise opening throws), and the test checks no row or value was lost.
 *
 * Run: right-click this file in Android Studio -> Run 'MigrationTest' (needs a phone or emulator).
 */
@RunWith(AndroidJUnit4.class)
public class MigrationTest {

    private static final String DB_NAME = "migration-test.db";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DB_NAME);
    }

    @After
    public void tearDown() {
        context.deleteDatabase(DB_NAME);
    }

    @Test
    public void migrate1To2_keepsStudentsAndDrafts_andAddsTheQueue() {
        // ---- 1. A version 1 database, with the tables Room generated for version 1 ----
        File file = context.getDatabasePath(DB_NAME);
        file.getParentFile().mkdirs();
        SQLiteDatabase v1 = SQLiteDatabase.openOrCreateDatabase(file, null);
        v1.execSQL("CREATE TABLE IF NOT EXISTS `students` (`student_id` TEXT NOT NULL, "
                + "`student_number` TEXT, `full_name` TEXT, `program` TEXT, `lab_group` TEXT, "
                + "`is_active` INTEGER NOT NULL, `record_version` INTEGER NOT NULL, "
                + "PRIMARY KEY(`student_id`))");
        v1.execSQL("CREATE TABLE IF NOT EXISTS `registration_draft` (`id` INTEGER NOT NULL, "
                + "`fullName` TEXT, `studentNumber` TEXT, `program` TEXT, `claimCode` TEXT, "
                + "`savedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        // Fictitious test data only.
        v1.execSQL("INSERT INTO students VALUES ('11111111-1111-4111-8111-111111111111', "
                + "'012345678', 'Test Student Migration', 'Computer Science', 'G02', 1, 3)");
        v1.execSQL("INSERT INTO registration_draft VALUES (1, 'Draft Person', '098765432', "
                + "'Information Technology', 'ABCDEF0123456789', 1700000000000)");
        v1.setVersion(1);
        v1.close();

        // ---- 2. Open it with the current app database (version 2) and the migration ----
        AppDatabase db = Room.databaseBuilder(context, AppDatabase.class, DB_NAME)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .build();
        try {
            // Existing student kept, values unchanged, now marked SYNCED with nothing pending.
            StudentEntity s = db.studentDao().findByIdNow("11111111-1111-4111-8111-111111111111");
            assertNotNull("student was deleted by the migration", s);
            assertEquals("012345678", s.studentNumber);   // leading zero kept
            assertEquals("Test Student Migration", s.fullName);
            assertEquals("Computer Science", s.program);
            assertEquals("G02", s.labGroup);
            assertEquals(3, s.recordVersion);
            assertEquals(SyncState.SYNCED, s.syncState);
            assertNull(s.pendingJson);

            // The unfinished registration draft (pending local work) survived.
            RegistrationDraftEntity draft = db.draftDao().getDraft();
            assertNotNull("draft was deleted by the migration", draft);
            assertEquals("098765432", draft.studentNumber);
            assertEquals("ABCDEF0123456789", draft.claimCode);

            // The new queue table works.
            PendingOperationEntity op = new PendingOperationEntity();
            op.opId = "22222222-2222-4222-8222-222222222222";
            op.accountId = "test-account";
            op.studentId = s.studentId;
            op.type = PendingOperationEntity.TYPE_UPDATE;
            op.payloadJson = "{\"student_name\":\"Edited Offline\"}";
            op.baseVersion = 3;
            op.createdAt = 1;
            db.pendingOperationDao().insert(op);
            assertEquals(1, db.pendingOperationDao().toSend("test-account").size());
            assertEquals(0, db.pendingOperationDao().toSend("another-account").size()); // scoped by account
        } finally {
            db.close();
        }
    }
}
