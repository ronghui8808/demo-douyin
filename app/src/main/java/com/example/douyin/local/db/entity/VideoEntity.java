package com.example.douyin.local.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "videos",
        foreignKeys = @ForeignKey(
                entity = UserEntity.class,
                parentColumns = "id",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("user_id"), @Index("created_at")}
)
public class VideoEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "user_id")
    public long userId;

    @ColumnInfo(name = "file_path")
    public String filePath;

    @ColumnInfo(name = "cover_path")
    public String coverPath;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "like_count")
    public int likeCount;

    @ColumnInfo(name = "comment_count")
    public int commentCount;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
