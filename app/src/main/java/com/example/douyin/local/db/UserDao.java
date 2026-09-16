package com.example.douyin.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.douyin.local.db.entity.UserEntity;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(UserEntity user);

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    UserEntity findByUsername(String username);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    UserEntity findById(long id);

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    UserEntity findByPhone(String phone);

    @Query("UPDATE users SET phone = :phone WHERE id = :id")
    void updatePhone(long id, String phone);

    @Query("SELECT COUNT(*) FROM users")
    int count();
}
