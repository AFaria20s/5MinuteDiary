package com.afonso.fiveminutediary.data;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

@Database(entities = {DiaryEntry.class, UserProfile.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase instance;

    public abstract DiaryDAO diaryDao();
    public abstract UserProfileDAO userProfileDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "diary_db")
                    .fallbackToDestructiveMigration() // Simplifica migração para MVP
                    .allowMainThreadQueries() // Only for MVP, then move to background
                    .build();
        }
        return instance;
    }
}