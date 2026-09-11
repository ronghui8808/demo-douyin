package com.example.douyin;

import android.app.Application;

import com.example.douyin.local.LocalServices;
import com.example.douyin.local.SeedDataInitializer;
import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.cache.MediaCacheManager;
import com.example.douyin.network.TokenStore;
import com.example.douyin.util.AppExecutors;

public class DouyinApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppDatabase.get(this);
        LocalServices.init(this);
        MediaCacheManager.get(this);
        TokenStore.get(this).hydrate(null);
        AppExecutors.get().diskIo(() -> SeedDataInitializer.init(this));
    }
}
