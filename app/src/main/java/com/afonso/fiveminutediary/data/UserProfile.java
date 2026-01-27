package com.afonso.fiveminutediary.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_profile")
public class UserProfile {
    @PrimaryKey(autoGenerate = true)
    private int id;

    private String userName;
    private long firstUseTimestamp;
    private long lastOpenedTimestamp;
    private int totalWordsWritten;
    private String favoriteWritingTime; // "morning", "afternoon", "evening", "night"

    public UserProfile() {
        this.firstUseTimestamp = System.currentTimeMillis();
        this.lastOpenedTimestamp = System.currentTimeMillis();
        this.totalWordsWritten = 0;
        this.favoriteWritingTime = "evening";
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public long getFirstUseTimestamp() {
        return firstUseTimestamp;
    }

    public void setFirstUseTimestamp(long firstUseTimestamp) {
        this.firstUseTimestamp = firstUseTimestamp;
    }

    public long getLastOpenedTimestamp() {
        return lastOpenedTimestamp;
    }

    public void setLastOpenedTimestamp(long lastOpenedTimestamp) {
        this.lastOpenedTimestamp = lastOpenedTimestamp;
    }

    public int getTotalWordsWritten() {
        return totalWordsWritten;
    }

    public void setTotalWordsWritten(int totalWordsWritten) {
        this.totalWordsWritten = totalWordsWritten;
    }

    public String getFavoriteWritingTime() {
        return favoriteWritingTime;
    }

    public void setFavoriteWritingTime(String favoriteWritingTime) {
        this.favoriteWritingTime = favoriteWritingTime;
    }
}