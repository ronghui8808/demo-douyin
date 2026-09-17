package com.example.douyin.local.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "follows",
        indices = {
                @Index(value = {"follower_id", "followee_id"}, unique = true),
                @Index("followee_id")
        }
)
public class FollowEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "follower_id")
    public long followerId;

    @ColumnInfo(name = "followee_id")
    public long followeeId;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
