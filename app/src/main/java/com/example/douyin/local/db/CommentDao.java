package com.example.douyin.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.douyin.local.db.entity.CommentEntity;

import java.util.List;

@Dao
public interface CommentDao {

    @Insert
    long insert(CommentEntity comment);

    @Query("SELECT * FROM comments WHERE video_id = :videoId ORDER BY created_at DESC LIMIT :size OFFSET :offset")
    List<CommentEntity> getByVideoId(long videoId, int offset, int size);

    @Query("SELECT COUNT(*) FROM comments WHERE video_id = :videoId")
    int countByVideoId(long videoId);
}
