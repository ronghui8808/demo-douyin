package com.example.douyin.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.douyin.local.db.entity.VideoEntity;

import java.util.List;

@Dao
public interface VideoDao {

    @Insert
    long insert(VideoEntity video);

    @Query("SELECT * FROM videos ORDER BY created_at DESC LIMIT :size OFFSET :offset")
    List<VideoEntity> getFeed(int offset, int size);

    @Query("SELECT * FROM videos WHERE user_id = :userId ORDER BY created_at DESC LIMIT :size OFFSET :offset")
    List<VideoEntity> getByUserId(long userId, int offset, int size);

    @Query("SELECT COUNT(*) FROM videos")
    int countAll();

    @Query("SELECT COUNT(*) FROM videos WHERE user_id = :userId")
    int countByUserId(long userId);

    @Query("SELECT COALESCE(SUM(like_count), 0) FROM videos WHERE user_id = :userId")
    int sumLikeCountByUserId(long userId);

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    VideoEntity findById(long id);

    @Query("UPDATE videos SET like_count = :count WHERE id = :id")
    void updateLikeCount(long id, int count);

    @Query("UPDATE videos SET comment_count = :count WHERE id = :id")
    void updateCommentCount(long id, int count);
}
