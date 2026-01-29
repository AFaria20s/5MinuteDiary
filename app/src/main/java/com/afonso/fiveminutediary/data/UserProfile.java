package com.afonso.fiveminutediary.data;

import java.util.HashMap;
import java.util.Map;

public class UserProfile {
    private String id; // Firebase document ID (same as userId)
    private String userName;
    private long firstUseTimestamp;
    private long lastOpenedTimestamp;
    private int totalWordsWritten;
    private String favoriteWritingTime;

    public UserProfile() {
        this.firstUseTimestamp = System.currentTimeMillis();
        this.lastOpenedTimestamp = System.currentTimeMillis();
        this.totalWordsWritten = 0;
        this.favoriteWritingTime = "evening";
    }

    public UserProfile(String id) {
        this();
        this.id = id;
    }

    // Convert to Map for Firebase
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("userName", userName);
        result.put("firstUseTimestamp", firstUseTimestamp);
        result.put("lastOpenedTimestamp", lastOpenedTimestamp);
        result.put("totalWordsWritten", totalWordsWritten);
        result.put("favoriteWritingTime", favoriteWritingTime);
        return result;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
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