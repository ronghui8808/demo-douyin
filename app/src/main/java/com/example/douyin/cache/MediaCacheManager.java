package com.example.douyin.cache;

import android.content.Context;
import android.text.TextUtils;

import com.example.douyin.util.AppExecutors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class MediaCacheManager {

    private static final long MAX_IMAGE_CACHE_BYTES = 64L * 1024L * 1024L;

    private static MediaCacheManager instance;

    private final File imageCacheDir;
    private final OkHttpClient httpClient;
    private final Map<String, Boolean> imageTasks = new ConcurrentHashMap<>();

    private MediaCacheManager(Context context) {
        File root = new File(context.getCacheDir(), "oss_media");
        imageCacheDir = new File(root, "image");
        imageCacheDir.mkdirs();
        httpClient = new OkHttpClient.Builder().build();
    }

    public static synchronized MediaCacheManager get(Context context) {
        if (instance == null) {
            instance = new MediaCacheManager(context.getApplicationContext());
        }
        return instance;
    }

    public void prefetchImage(String remoteUrl) {
        if (!shouldCacheRemoteUrl(remoteUrl)) {
            return;
        }
        enqueueDownload(normalizeUrl(remoteUrl));
    }

    public File getCachedImageFile(String remoteUrl) {
        if (!shouldCacheRemoteUrl(remoteUrl)) {
            return null;
        }
        return getCachedFile(normalizeUrl(remoteUrl));
    }

    private void enqueueDownload(String url) {
        if (imageTasks.putIfAbsent(url, Boolean.TRUE) != null) {
            return;
        }
        AppExecutors.get().network(() -> {
            try {
                downloadToCache(url);
            } catch (Exception ignored) {
            } finally {
                imageTasks.remove(url);
            }
        });
    }

    private File downloadToCache(String url) throws IOException {
        File cached = getCachedFile(url);
        if (cached != null) {
            return cached;
        }

        File tempFile = new File(imageCacheDir, cacheKey(url) + ".tmp");
        File targetFile = new File(imageCacheDir, cacheKey(url) + extensionForUrl(url));

        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载失败: HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("下载失败: 空响应");
            }
            try (InputStream input = body.byteStream();
                 FileOutputStream output = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
        }

        if (!tempFile.renameTo(targetFile)) {
            if (targetFile.exists()) {
                tempFile.delete();
            } else if (!tempFile.renameTo(targetFile)) {
                throw new IOException("无法写入缓存文件");
            }
        }
        trimCache();
        return targetFile;
    }

    private File getCachedFile(String url) {
        File file = new File(imageCacheDir, cacheKey(url) + extensionForUrl(url));
        if (file.exists() && file.length() > 0) {
            return file;
        }
        return null;
    }

    private void trimCache() {
        File[] files = imageCacheDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        List<File> fileList = new ArrayList<>();
        long total = 0L;
        for (File file : files) {
            if (file.isFile() && !file.getName().endsWith(".tmp")) {
                fileList.add(file);
                total += file.length();
            }
        }
        if (total <= MAX_IMAGE_CACHE_BYTES) {
            return;
        }

        fileList.sort((left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (File file : fileList) {
            if (total <= MAX_IMAGE_CACHE_BYTES) {
                break;
            }
            total -= file.length();
            file.delete();
        }
    }

    private boolean shouldCacheRemoteUrl(String url) {
        return !TextUtils.isEmpty(url) && !isLocalUrl(normalizeUrl(url));
    }

    private boolean isLocalUrl(String url) {
        return url.startsWith("file://") || (!url.startsWith("http://") && !url.startsWith("https://"));
    }

    private String normalizeUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        return url.trim();
    }

    private String cacheKey(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(url.getBytes());
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) {
                builder.append(String.format(Locale.US, "%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private String extensionForUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".png")) {
            return ".png";
        }
        if (lower.contains(".webp")) {
            return ".webp";
        }
        if (lower.contains(".jpeg")) {
            return ".jpeg";
        }
        if (lower.contains(".jpg")) {
            return ".jpg";
        }
        return ".img";
    }
}
