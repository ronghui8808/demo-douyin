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
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class MediaCacheManager {

    private static final long MAX_VIDEO_CACHE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_IMAGE_CACHE_BYTES = 64L * 1024L * 1024L;

    public interface CacheCallback {
        void onReady(String playableUrl);

        void onError(String message);
    }

    private static MediaCacheManager instance;

    private final File videoCacheDir;
    private final File imageCacheDir;
    private final OkHttpClient httpClient;
    private final Map<String, DownloadTask> videoTasks = new ConcurrentHashMap<>();
    private final Map<String, DownloadTask> imageTasks = new ConcurrentHashMap<>();

    private MediaCacheManager(Context context) {
        File root = new File(context.getCacheDir(), "oss_media");
        videoCacheDir = new File(root, "video");
        imageCacheDir = new File(root, "image");
        videoCacheDir.mkdirs();
        imageCacheDir.mkdirs();
        httpClient = new OkHttpClient.Builder().build();
    }

    public static synchronized MediaCacheManager get(Context context) {
        if (instance == null) {
            instance = new MediaCacheManager(context.getApplicationContext());
        }
        return instance;
    }

    public void prefetchVideo(String remoteUrl) {
        if (!shouldCacheRemoteUrl(remoteUrl)) {
            return;
        }
        enqueueDownload(CacheType.VIDEO, remoteUrl, null);
    }

    public void prefetchImage(String remoteUrl) {
        if (!shouldCacheRemoteUrl(remoteUrl)) {
            return;
        }
        enqueueDownload(CacheType.IMAGE, remoteUrl, null);
    }

    public void resolveVideoForPlayback(String remoteUrl, CacheCallback callback) {
        if (callback == null) {
            return;
        }
        String normalized = normalizeUrl(remoteUrl);
        if (TextUtils.isEmpty(normalized)) {
            callback.onError("视频地址无效");
            return;
        }
        if (isLocalUrl(normalized)) {
            callback.onReady(normalized);
            return;
        }
        File cached = getCachedFile(CacheType.VIDEO, normalized);
        if (cached != null) {
            callback.onReady(toFileUrl(cached));
            return;
        }
        enqueueDownload(CacheType.VIDEO, normalized, callback);
    }

    public File getCachedImageFile(String remoteUrl) {
        if (!shouldCacheRemoteUrl(remoteUrl)) {
            return null;
        }
        return getCachedFile(CacheType.IMAGE, normalizeUrl(remoteUrl));
    }

    private void enqueueDownload(CacheType type, String url, CacheCallback callback) {
        Map<String, DownloadTask> tasks = type == CacheType.VIDEO ? videoTasks : imageTasks;
        DownloadTask existing = tasks.get(url);
        if (existing != null) {
            if (callback != null) {
                existing.addCallback(callback);
            }
            return;
        }

        DownloadTask task = new DownloadTask(type, url);
        if (callback != null) {
            task.addCallback(callback);
        }
        tasks.put(url, task);
        AppExecutors.get().network(() -> {
            try {
                File file = downloadToCache(type, url);
                task.notifySuccess(toFileUrl(file));
            } catch (Exception e) {
                task.notifyError(e.getMessage() != null ? e.getMessage() : "缓存失败");
            } finally {
                tasks.remove(url);
            }
        });
    }

    private File downloadToCache(CacheType type, String url) throws IOException {
        File cached = getCachedFile(type, url);
        if (cached != null) {
            return cached;
        }

        File cacheDir = type == CacheType.VIDEO ? videoCacheDir : imageCacheDir;
        // 先写 .tmp 再 rename：下载中断不会留下半截正式文件被 getCachedFile 当成有效命中
        File tempFile = new File(cacheDir, cacheKey(url) + ".tmp");
        File targetFile = new File(cacheDir, cacheKey(url) + extensionForUrl(url, type));

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
        trimCache(type);
        return targetFile;
    }

    private File getCachedFile(CacheType type, String url) {
        File cacheDir = type == CacheType.VIDEO ? videoCacheDir : imageCacheDir;
        File file = new File(cacheDir, cacheKey(url) + extensionForUrl(url, type));
        if (file.exists() && file.length() > 0) {
            return file;
        }
        return null;
    }

    private void trimCache(CacheType type) {
        File cacheDir = type == CacheType.VIDEO ? videoCacheDir : imageCacheDir;
        long maxBytes = type == CacheType.VIDEO ? MAX_VIDEO_CACHE_BYTES : MAX_IMAGE_CACHE_BYTES;
        File[] files = cacheDir.listFiles();
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
        if (total <= maxBytes) {
            return;
        }

        fileList.sort((left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (File file : fileList) {
            if (total <= maxBytes) {
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

    private String toFileUrl(File file) {
        return "file://" + file.getAbsolutePath();
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

    private String extensionForUrl(String url, CacheType type) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains(".mp4")) {
            return ".mp4";
        }
        if (lower.contains(".webm")) {
            return ".webm";
        }
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
        return type == CacheType.VIDEO ? ".mp4" : ".img";
    }

    private enum CacheType {
        VIDEO,
        IMAGE
    }

    private static final class DownloadTask {
        private final CopyOnWriteArrayList<CacheCallback> callbacks = new CopyOnWriteArrayList<>();

        DownloadTask(CacheType type, String url) {
        }

        void addCallback(CacheCallback callback) {
            if (callback != null) {
                callbacks.add(callback);
            }
        }

        void notifySuccess(String playableUrl) {
            List<CacheCallback> pending = new ArrayList<>(callbacks);
            AppExecutors.get().mainThread(() -> {
                for (CacheCallback callback : pending) {
                    callback.onReady(playableUrl);
                }
            });
        }

        void notifyError(String message) {
            List<CacheCallback> pending = new ArrayList<>(callbacks);
            AppExecutors.get().mainThread(() -> {
                for (CacheCallback callback : pending) {
                    callback.onError(message);
                }
            });
        }
    }
}
