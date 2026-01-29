package com.afonso.fiveminutediary.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DiaryEntry implements Serializable {
    private String id;           // Firebase document ID
    private String userId;       // Owner of the entry
    private long timestamp;
    private String text;
    private String imagePath;
    private String formatting;   // JSON string com formatação rica

    // Empty constructor required for Firebase
    public DiaryEntry() {
    }

    public DiaryEntry(long timestamp, String text) {
        this.timestamp = timestamp;
        this.text = text;
    }

    public DiaryEntry(String id, String userId, long timestamp, String text, String imagePath) {
        this.id = id;
        this.userId = userId;
        this.timestamp = timestamp;
        this.text = text;
        this.imagePath = imagePath;
    }

    public DiaryEntry(String id, String userId, long timestamp, String text, String imagePath, String formatting) {
        this.id = id;
        this.userId = userId;
        this.timestamp = timestamp;
        this.text = text;
        this.imagePath = imagePath;
        this.formatting = formatting;
    }

    // Convert to Map for Firebase
    public Map<String, Object> toMap() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("timestamp", timestamp);
        result.put("text", text);
        result.put("imagePath", imagePath);
        result.put("formatting", formatting);
        return result;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getFormatting() {
        return formatting;
    }

    public void setFormatting(String formatting) {
        this.formatting = formatting;
    }
}