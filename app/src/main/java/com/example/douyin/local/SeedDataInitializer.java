package com.example.douyin.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.local.db.UserDao;
import com.example.douyin.local.db.VideoDao;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.local.db.entity.VideoEntity;
import com.example.douyin.oss.OssConfig;
import com.example.douyin.util.PasswordHasher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class SeedDataInitializer {

    private static final String PREFS = "douyin_seed";
    private static final String KEY_DONE = "seed_done";
    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "123456";

    private SeedDataInitializer() {
    }

    public static void init(Context context) {
        if (isSeedDone(context)) {
            return;
        }
        initBlocking(context);
    }

    public static synchronized void initBlocking(Context context) {
        Context appContext = context.getApplicationContext();
        if (isSeedDone(appContext)) {
            return;
        }

        AppDatabase database = AppDatabase.get(appContext);
        UserDao userDao = database.userDao();
        VideoDao videoDao = database.videoDao();

        long demoUserId = ensureDemoUser(userDao);
        seedVideos(appContext, userDao, videoDao, demoUserId);
        markSeedDone(appContext);
    }

    private static long ensureDemoUser(UserDao userDao) {
        UserEntity existing = userDao.findByUsername(DEMO_USERNAME);
        if (existing != null) {
            return existing.id;
        }

        UserEntity user = new UserEntity();
        user.username = DEMO_USERNAME;
        user.passwordHash = PasswordHasher.hash(DEMO_PASSWORD);
        user.nickname = "演示用户";
        user.createdAt = System.currentTimeMillis();
        return userDao.insert(user);
    }

    private static void seedVideos(Context context, UserDao userDao, VideoDao videoDao, long demoUserId) {
        if (videoDao.countAll() > 0) {
            return;
        }

        try {
            JSONArray array = readJsonArray(context, "seed/videos.json");
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String authorUsername = item.optString("authorUsername", DEMO_USERNAME);
                UserEntity author = userDao.findByUsername(authorUsername);
                long userId = author != null ? author.id : demoUserId;

                String filePath = resolveVideoPath(context, item);
                if (TextUtils.isEmpty(filePath)) {
                    continue;
                }

                VideoEntity video = new VideoEntity();
                video.userId = userId;
                video.filePath = filePath;
                video.coverPath = resolveCoverPath(item);
                video.description = item.optString("description", "");
                video.likeCount = item.optInt("likeCount", 0);
                video.commentCount = item.optInt("commentCount", 0);
                video.createdAt = System.currentTimeMillis() - (array.length() - i) * 60_000L;
                videoDao.insert(video);
            }
        } catch (Exception ignored) {
            // 种子数据失败不阻塞启动
        }
    }

    private static String resolveVideoPath(Context context, JSONObject item) throws IOException {
        String ossObjectKey = item.optString("ossObjectKey", "");
        if (!TextUtils.isEmpty(ossObjectKey) && OssConfig.get().isConfigured()) {
            return OssConfig.get().buildPublicUrl(ossObjectKey);
        }

        String videoUrl = item.optString("videoUrl", "");
        if (!TextUtils.isEmpty(videoUrl)) {
            return videoUrl;
        }

        String assetPath = item.optString("videoFile", "");
        if (TextUtils.isEmpty(assetPath)) {
            return "";
        }

        InputStream inputStream = context.getAssets().open(assetPath);
        File videosDir = new File(context.getFilesDir(), "videos");
        if (!videosDir.exists()) {
            videosDir.mkdirs();
        }
        File target = new File(videosDir, "seed_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = inputStream; FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return target.getAbsolutePath();
    }

    private static String resolveCoverPath(JSONObject item) {
        String ossCoverKey = item.optString("ossCoverKey", "");
        if (!TextUtils.isEmpty(ossCoverKey) && OssConfig.get().isConfigured()) {
            return OssConfig.get().buildPublicUrl(ossCoverKey);
        }
        return item.optString("coverUrl", "");
    }

    private static JSONArray readJsonArray(Context context, String assetPath) throws Exception {
        InputStream inputStream = context.getAssets().open(assetPath);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        );
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return new JSONArray(builder.toString());
    }

    private static boolean isSeedDone(Context context) {
        return getPrefs(context).getBoolean(KEY_DONE, false);
    }

    private static void markSeedDone(Context context) {
        getPrefs(context).edit().putBoolean(KEY_DONE, true).apply();
    }

    public static void resetSeedFlag(Context context) {
        getPrefs(context).edit().remove(KEY_DONE).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
