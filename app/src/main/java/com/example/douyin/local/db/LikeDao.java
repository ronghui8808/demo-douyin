package com.example.douyin.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.douyin.local.db.entity.LikeEntity;

@Dao
public interface LikeDao {

    @Insert
    void insert(LikeEntity like);

    @Query("DELETE FROM likes WHERE user_id = :userId AND video_id = :videoId")
    void delete(long userId, long videoId);

    @Query("SELECT * FROM likes WHERE user_id = :userId AND video_id = :videoId LIMIT 1")
    LikeEntity find(long userId, long videoId);
}
