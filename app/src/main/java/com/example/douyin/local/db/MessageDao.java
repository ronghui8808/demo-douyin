package com.example.douyin.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.douyin.local.db.entity.MessageEntity;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert
    long insert(MessageEntity e);

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    List<MessageEntity> listByConversation(long conversationId);

    @Query("UPDATE messages SET read_at = :readAt WHERE conversation_id = :conversationId AND sender_id != :viewerId AND read_at IS NULL")
    int markRead(long conversationId, long viewerId, long readAt);

    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId AND sender_id != :viewerId AND read_at IS NULL")
    int countUnread(long conversationId, long viewerId);
}
