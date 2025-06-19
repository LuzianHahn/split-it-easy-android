package com.nishantboro.splititeasy;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {MemberEntity.class,BillEntity.class,GroupEntity.class}, version = 2,exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static final String db_name = "SplitItEasy";
    private static AppDatabase instance;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Füge die neue Date-Spalte zur BillEntity-Tabelle hinzu
            // NOT NULL Constraint hinzugefügt und Default-Wert gesetzt
            database.execSQL("ALTER TABLE BillEntity ADD COLUMN Date INTEGER NOT NULL DEFAULT " + System.currentTimeMillis());
        }
    };

    // if an instance of the database was already created return it else generate a new instance
    static synchronized AppDatabase getInstance(Context context) {
        if(instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    AppDatabase.class, db_name)
                    .addMigrations(MIGRATION_1_2)
                    .build();
        }
        return instance;
    }
    public abstract MemberDao memberDao();
    public abstract BillDao billDao();
    public abstract GroupDao groupDao();

    // Methode zum Zurücksetzen der Instanz für Import/Hot-Reload
    public static synchronized void clearInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
