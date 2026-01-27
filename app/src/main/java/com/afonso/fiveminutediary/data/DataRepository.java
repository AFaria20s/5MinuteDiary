package com.afonso.fiveminutediary.data;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Calendar;
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

    public void updateEntry(DiaryEntry entry) {
        db.diaryDao().update(entry);
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

    public int calculateStreak() {
        List<DiaryEntry> allEntries = getEntries();
        if (allEntries.isEmpty()) {
            return 0;
        }

        // Sort by date (already sorted DESC from DB)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long todayStart = calendar.getTimeInMillis();

        // Check if we have an entry for today
        boolean hasToday = false;
        for (DiaryEntry entry : allEntries) {
            if (entry.getTimestamp() >= todayStart) {
                hasToday = true;
                break;
            }
        }

        // Start counting streak from today or yesterday
        if (!hasToday) {
            // If no entry today, start from yesterday
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }

        int streak = 0;
        long currentDayStart = calendar.getTimeInMillis();

        // Go backwards checking consecutive days
        for (int i = 0; i < 365; i++) { // Max 1 year streak
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long dayStart = calendar.getTimeInMillis();
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            long dayEnd = calendar.getTimeInMillis();
            calendar.add(Calendar.DAY_OF_YEAR, -1);

            // Check if we have an entry for this day
            boolean foundEntryForDay = false;
            for (DiaryEntry entry : allEntries) {
                if (entry.getTimestamp() >= dayStart && entry.getTimestamp() < dayEnd) {
                    foundEntryForDay = true;
                    break;
                }
            }

            if (foundEntryForDay) {
                streak++;
                calendar.add(Calendar.DAY_OF_YEAR, -1); // Move to previous day
            } else {
                break; // Streak broken
            }
        }

        return streak;
    }
}