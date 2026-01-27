package com.afonso.fiveminutediary.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;
@Entity(tableName = "diary_entries")
public class DiaryEntry implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private long timestamp;
    private String text;

    // Novo campo
    private String imagePath;

    public DiaryEntry(long timestamp, String text) {
        this.timestamp = timestamp;
        this.text = text;
    }

    // Getters e setters
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    // Novo
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}

