package com.example.douyin.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppExecutors {

    private static final AppExecutors INSTANCE = new AppExecutors();

    private final ExecutorService diskIo = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppExecutors() {
    }

    public static AppExecutors get() {
        return INSTANCE;
    }

    public void diskIo(Runnable runnable) {
        diskIo.execute(runnable);
    }

    public void mainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }
}
