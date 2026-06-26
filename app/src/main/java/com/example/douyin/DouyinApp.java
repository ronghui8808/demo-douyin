package com.example.douyin;

import android.app.Application;

import com.example.douyin.local.LocalServices;
import com.example.douyin.local.SeedDataInitializer;
import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.util.AppExecutors;

public class DouyinApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppDatabase.get(this);
        LocalServices.init(this);
        AppExecutors.get().diskIo(() -> SeedDataInitializer.init(this));
    }
}
