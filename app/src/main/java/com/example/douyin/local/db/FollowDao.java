package com.example.douyin.local.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.douyin.local.db.entity.FollowEntity;

import java.util.List;

@Dao
public interface FollowDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(FollowEntity entity);

    @Query("DELETE FROM follows WHERE follower_id = :followerId AND followee_id = :followeeId")
    int delete(long followerId, long followeeId);

    @Query("SELECT * FROM follows WHERE follower_id = :followerId AND followee_id = :followeeId LIMIT 1")
    FollowEntity find(long followerId, long followeeId);

    @Query("SELECT * FROM follows WHERE follower_id = :followerId ORDER BY created_at DESC")
    List<FollowEntity> listByFollower(long followerId);

    @Query("SELECT COUNT(*) FROM follows WHERE follower_id = :followerId")
    int countByFollower(long followerId);
}
