package com.example.douyin.cache;

import android.content.Context;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import com.example.douyin.player.MediaUrlHelper;
import com.example.douyin.util.AppExecutors;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ExoMediaCache {

    private static final long MAX_CACHE_BYTES = 512L * 1024L * 1024L;

    private static ExoMediaCache instance;

    private final SimpleCache simpleCache;
    private final CacheDataSource.Factory cacheDataSourceFactory;
    private final Map<String, Boolean> inflight = new ConcurrentHashMap<>();

    private ExoMediaCache(Context context) {
        File cacheDir = new File(context.getCacheDir(), "exo_video");
        StandaloneDatabaseProvider databaseProvider = new StandaloneDatabaseProvider(context);
        simpleCache = new SimpleCache(
                cacheDir,
                new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                databaseProvider
        );
        DefaultHttpDataSource.Factory upstreamFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);
        cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    public static synchronized ExoMediaCache get(Context context) {
        if (instance == null) {
            instance = new ExoMediaCache(context.getApplicationContext());
        }
        return instance;
    }

    public CacheDataSource.Factory getCacheDataSourceFactory() {
        return cacheDataSourceFactory;
    }

    public void prefetch(String remoteUrl) {
        if (!MediaUrlHelper.shouldPrefetch(remoteUrl)) {
            return;
        }
        String url = MediaUrlHelper.normalize(remoteUrl);
        if (inflight.putIfAbsent(url, Boolean.TRUE) != null) {
            return;
        }
        AppExecutors.get().network(() -> {
            try {
                DataSpec dataSpec = new DataSpec.Builder()
                        .setUri(android.net.Uri.parse(url))
                        .setLength(MediaUrlHelper.PREFETCH_BYTES)
                        .build();
                CacheDataSource dataSource = cacheDataSourceFactory.createDataSource();
                new CacheWriter(dataSource, dataSpec, /* temporaryBuffer= */ null, /* progressListener= */ null)
                        .cache();
            } catch (Exception ignored) {
                // 预取失败不阻断播放；播放时由 ExoPlayer 自行拉流
            } finally {
                inflight.remove(url);
            }
        });
    }
}
