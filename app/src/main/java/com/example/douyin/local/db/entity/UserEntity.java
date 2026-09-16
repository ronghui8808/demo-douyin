package com.example.douyin.local.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "users",
        indices = {
                @Index(value = "username", unique = true),
                @Index(value = "phone", unique = true)
        }
)
public class UserEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "password_hash")
    public String passwordHash;

    @ColumnInfo(name = "nickname")
    public String nickname;

    @ColumnInfo(name = "avatar_path")
    public String avatarPath;

    @ColumnInfo(name = "phone")
    public String phone;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
