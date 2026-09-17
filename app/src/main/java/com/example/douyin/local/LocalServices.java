package com.example.douyin.local;

import android.content.Context;

import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.local.service.LocalAuthService;
import com.example.douyin.local.service.LocalCommentService;
import com.example.douyin.local.service.LocalFollowService;
import com.example.douyin.local.service.LocalMessageService;
import com.example.douyin.local.service.LocalVideoService;
import com.example.douyin.local.sms.MockSmsGateway;
import com.example.douyin.local.sms.SmsGateway;

public final class LocalServices {

    private static volatile boolean initialized;
    private static LocalAuthService authService;
    private static LocalVideoService videoService;
    private static LocalCommentService commentService;
    private static LocalFollowService followService;
    private static LocalMessageService messageService;
    private static SmsGateway smsGateway;

    private LocalServices() {
    }

    public static synchronized void init(Context context) {
        if (initialized) {
            return;
        }
        Context appContext = context.getApplicationContext();
        AppDatabase database = AppDatabase.get(appContext);
        smsGateway = new MockSmsGateway();
        authService = new LocalAuthService(
                appContext, database.userDao(), smsGateway, database.followDao());
        videoService = new LocalVideoService(
                appContext,
                database.userDao(),
                database.videoDao(),
                database.likeDao(),
                database.followDao()
        );
        commentService = new LocalCommentService(
                database.userDao(),
                database.videoDao(),
                database.commentDao()
        );
        followService = new LocalFollowService(database.userDao(), database.followDao());
        messageService = new LocalMessageService(
                database.userDao(),
                database.followDao(),
                database.conversationDao(),
                database.messageDao()
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

    public static LocalFollowService follow() {
        checkInitialized();
        return followService;
    }

    public static LocalMessageService messages() {
        checkInitialized();
        return messageService;
    }

    public static void resetForTests() {
        initialized = false;
        authService = null;
        videoService = null;
        commentService = null;
        followService = null;
        messageService = null;
        smsGateway = null;
        AppDatabase.resetInstance();
    }

    private static void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("LocalServices not initialized");
        }
    }
}
