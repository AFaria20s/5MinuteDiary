package com.afonso.fiveminutediary.utils;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DiaryEntry;

import java.util.Calendar;

public class DiaryUtils {

    /**
     * Returns an image resource based on the diary entry's date
     * Uses day of week to determine which image to show
     */
    public static int getImageForEntry(DiaryEntry entry) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(entry.getTimestamp());

        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

        // Map days to different drawable resources
        // You can replace these with your own images
        switch (dayOfWeek) {
            case Calendar.MONDAY:
                return R.drawable.diary_monday; // Add these drawables to res/drawable
            case Calendar.TUESDAY:
                return R.drawable.diary_tuesday;
            case Calendar.WEDNESDAY:
                return R.drawable.diary_wednesday;
            case Calendar.THURSDAY:
                return R.drawable.diary_thursday;
            case Calendar.FRIDAY:
                return R.drawable.diary_friday;
            case Calendar.SATURDAY:
                return R.drawable.diary_saturday;
            case Calendar.SUNDAY:
                return R.drawable.diary_sunday;
            default:
                return R.drawable.diary_default;
        }
    }

    /**
     * Returns a mood/emotion based on text content (simple implementation)
     * You can enhance this with sentiment analysis
     */
    public static String analyzeMood(String text) {
        String lowerText = text.toLowerCase();

        if (lowerText.contains("feliz") || lowerText.contains("ótimo") || lowerText.contains("alegre")) {
            return "😊";
        } else if (lowerText.contains("triste") || lowerText.contains("mal")) {
            return "😔";
        } else if (lowerText.contains("cansado") || lowerText.contains("sono")) {
            return "😴";
        } else {
            return "😌";
        }
    }
}