package com.example.douyin.local.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.douyin.local.db.entity.CommentEntity;
import com.example.douyin.local.db.entity.LikeEntity;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.local.db.entity.VideoEntity;

@Database(
        entities = {
                UserEntity.class,
                VideoEntity.class,
                CommentEntity.class,
                LikeEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract UserDao userDao();

    public abstract VideoDao videoDao();

    public abstract CommentDao commentDao();

    public abstract LikeDao likeDao();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "douyin.db"
                            )
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return instance;
    }

    public static AppDatabase createInMemory(Context context) {
        return Room.inMemoryDatabaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class
                )
                .allowMainThreadQueries()
                .build();
    }

    public static void resetInstance() {
        instance = null;
    }
}
