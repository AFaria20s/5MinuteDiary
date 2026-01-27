package com.afonso.fiveminutediary.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface UserProfileDAO {

    @Insert
    void insert(UserProfile profile);

    @Update
    void update(UserProfile profile);

    @Query("SELECT * FROM user_profile LIMIT 1")
    UserProfile getProfile();

    @Query("SELECT COUNT(*) FROM user_profile")
    int getProfileCount();

    @Query("DELETE FROM user_profile")
    void deleteAll();
}