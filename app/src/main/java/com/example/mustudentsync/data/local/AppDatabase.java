package com.example.mustudentsync.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.mustudentsync.model.RegistrationDraftEntity;
import com.example.mustudentsync.model.StudentEntity;

@Database(
        entities = {StudentEntity.class, RegistrationDraftEntity.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract StudentDao studentDao();
    public abstract DraftDao draftDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "mustudentsync.db"
                    ).build();
                    // NOTE: when you add a real Room migration in Activity E's
                    // "test one Room migration" requirement, replace .build() with
                    // .addMigrations(MIGRATION_1_2) rather than wiping data.
                }
            }
        }
        return INSTANCE;
    }
}