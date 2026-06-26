package com.example.douyin.local;

import android.content.Context;

import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.local.service.LocalAuthService;
import com.example.douyin.local.service.LocalCommentService;
import com.example.douyin.local.service.LocalVideoService;

public final class LocalServices {

    private static volatile boolean initialized;
    private static LocalAuthService authService;
    private static LocalVideoService videoService;
    private static LocalCommentService commentService;

    private LocalServices() {
    }

    public static synchronized void init(Context context) {
        if (initialized) {
            return;
        }
        Context appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.get(appContext);
        authService = new LocalAuthService(appContext, database.userDao());
        videoService = new LocalVideoService(
                appContext,
                database.userDao(),
                database.videoDao(),
                database.likeDao()
        );
        commentService = new LocalCommentService(
                database.userDao(),
                database.videoDao(),
                database.commentDao()
        );
        initialized = true;
    }

    public static LocalAuthService auth() {
        checkInitialized();
        return authService;
    }

    public static LocalVideoService video() {
        checkInitialized();
        return videoService;
    }

    public static LocalCommentService comment() {
        checkInitialized();
        return commentService;
    }

    public static void resetForTests() {
        initialized = false;
        authService = null;
        videoService = null;
        commentService = null;
        AppDatabase.resetInstance();
    }

    private static void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("LocalServices not initialized");
        }
    }
}
