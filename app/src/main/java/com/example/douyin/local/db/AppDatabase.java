package com.example.douyin.local.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.douyin.local.db.entity.CommentEntity;
import com.example.douyin.local.db.entity.FollowEntity;
import com.example.douyin.local.db.entity.LikeEntity;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.local.db.entity.VideoEntity;

@Database(
        entities = {
                UserEntity.class,
                VideoEntity.class,
                CommentEntity.class,
                LikeEntity.class,
                FollowEntity.class
        },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE users ADD COLUMN phone TEXT");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_phone ON users(phone)");
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS follows ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "follower_id INTEGER NOT NULL, "
                    + "followee_id INTEGER NOT NULL, "
                    + "created_at INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "index_follows_follower_id_followee_id ON follows(follower_id, followee_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_follows_followee_id ON follows(followee_id)");
        }
    };

    public abstract UserDao userDao();

    public abstract VideoDao videoDao();

    public abstract CommentDao commentDao();

    public abstract LikeDao likeDao();

    public abstract FollowDao followDao();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "douyin.db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
