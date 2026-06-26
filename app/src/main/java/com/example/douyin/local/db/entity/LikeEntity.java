package com.example.douyin.local.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "likes",
        primaryKeys = {"user_id", "video_id"},
        foreignKeys = {
                @ForeignKey(
                        entity = UserEntity.class,
                        parentColumns = "id",
                        childColumns = "user_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = VideoEntity.class,
                        parentColumns = "id",
                        childColumns = "video_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {@Index("video_id")}
)
public class LikeEntity {

    @ColumnInfo(name = "user_id")
    public long userId;

    @ColumnInfo(name = "video_id")
    public long videoId;
}
