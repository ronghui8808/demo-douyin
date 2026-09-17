package com.example.douyin.local.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "conversations",
        indices = {@Index(value = {"user_low_id", "user_high_id"}, unique = true)}
)
public class ConversationEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "user_low_id")
    public long userLowId;

    @ColumnInfo(name = "user_high_id")
    public long userHighId;

    @ColumnInfo(name = "last_message_preview")
    public String lastMessagePreview;

    @ColumnInfo(name = "last_message_at")
    public long lastMessageAt;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
