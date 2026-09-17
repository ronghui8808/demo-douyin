package com.example.douyin.local.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "messages",
        indices = {@Index("conversation_id"), @Index("sender_id")}
)
public class MessageEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "conversation_id")
    public long conversationId;

    @ColumnInfo(name = "sender_id")
    public long senderId;

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "read_at")
    public Long readAt;
}
