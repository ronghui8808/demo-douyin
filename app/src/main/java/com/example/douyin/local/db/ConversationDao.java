package com.example.douyin.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.douyin.local.db.entity.ConversationEntity;

import java.util.List;

@Dao
public interface ConversationDao {

    @Insert
    long insert(ConversationEntity e);

    @Update
    int update(ConversationEntity e);

    @Query("SELECT * FROM conversations WHERE user_low_id = :low AND user_high_id = :high LIMIT 1")
    ConversationEntity findByPair(long low, long high);

    @Query("SELECT * FROM conversations WHERE user_low_id = :userId OR user_high_id = :userId ORDER BY last_message_at DESC")
    List<ConversationEntity> listForUser(long userId);
}
