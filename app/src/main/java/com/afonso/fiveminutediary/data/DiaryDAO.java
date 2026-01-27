package com.afonso.fiveminutediary.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DiaryDAO {

    @Insert
    void insert(DiaryEntry entry);

    @Query("SELECT * FROM diary_entries ORDER BY timestamp DESC")
    List<DiaryEntry> getAllEntries();

    @Update
    void update(DiaryEntry entry);

    @Delete
    void delete(DiaryEntry entry);

    @Query("SELECT * FROM diary_entries WHERE strftime('%Y-%m-%d', datetime(timestamp/1000, 'unixepoch')) = :day LIMIT 1")
    DiaryEntry getEntryForDay(String day);

    @Query("SELECT COUNT(*) FROM diary_entries")
    int getEntryCount();
}