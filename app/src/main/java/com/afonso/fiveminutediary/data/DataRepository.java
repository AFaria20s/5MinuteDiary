package com.afonso.fiveminutediary.data;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DataRepository {

    private static DataRepository instance;
    private AppDatabase db;

    private DataRepository(Context context) {
        db = AppDatabase.getInstance(context);
    }

    public static DataRepository getInstance(Context context) {
        if (instance == null) {
            instance = new DataRepository(context);
        }
        return instance;
    }

    // ========== DiaryEntry Methods ==========

    public void addEntry(DiaryEntry entry) {
        db.diaryDao().insert(entry);
    }

    public List<DiaryEntry> getEntries() {
        return db.diaryDao().getAllEntries();
    }

    public void addOrUpdateEntry(long timestamp, String text) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));
        DiaryEntry existing = db.diaryDao().getEntryForDay(today);

        if (existing != null) {
            existing.setText(text);
            existing.setTimestamp(timestamp);
            db.diaryDao().update(existing);
        } else {
            db.diaryDao().insert(new DiaryEntry(timestamp, text));
        }
    }

    public DiaryEntry getEntryForDay(String day) {
        return db.diaryDao().getEntryForDay(day);
    }

    public void deleteEntry(DiaryEntry entry) {
        db.diaryDao().delete(entry);
    }

    public int getEntryCount() {
        return db.diaryDao().getEntryCount();
    }

    // ========== UserProfile Methods ==========

    public UserProfile getUserProfile() {
        return db.userProfileDao().getProfile();
    }

    public void insertUserProfile(UserProfile profile) {
        db.userProfileDao().insert(profile);
    }

    public void updateUserProfile(UserProfile profile) {
        db.userProfileDao().update(profile);
    }

    public UserProfile getOrCreateUserProfile() {
        UserProfile profile = db.userProfileDao().getProfile();
        if (profile == null) {
            profile = new UserProfile();
            db.userProfileDao().insert(profile);
            profile = db.userProfileDao().getProfile(); // Get it back with ID
        }
        return profile;
    }
}