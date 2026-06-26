package com.example.douyin.oss;

import android.content.Context;
import android.text.TextUtils;

import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;

import java.io.File;
import java.util.Locale;
import java.util.UUID;

public final class OssUploadService {

    private static OssUploadService instance;

    private final Context appContext;

    private OssUploadService(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized OssUploadService get(Context context) {
        if (instance == null) {
            instance = new OssUploadService(context);
        }
        return instance;
    }

    public String uploadVideo(File file, long userId) throws Exception {
        return uploadFile(file, userId, "videos", ".mp4");
    }

    public String uploadImage(File file, long userId) throws Exception {
        String extension = resolveImageExtension(file.getName());
        return uploadFile(file, userId, "images", extension);
    }

    private String uploadFile(File file, long userId, String folder, String extension) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件无效");
        }
        OssConfig config = OssConfig.get();
        if (!config.isConfigured()) {
            throw new IllegalStateException("OSS 未配置");
        }

        String objectKey = String.format(
                Locale.US,
                "%s/%d/%s%s",
                folder,
                userId,
                UUID.randomUUID().toString(),
                extension
        );
        OSS client = OssClientHolder.getClient(appContext);
        PutObjectRequest request = new PutObjectRequest(
                config.bucket,
                objectKey,
                file.getAbsolutePath()
        );
        client.putObject(request);
        return config.buildPublicUrl(objectKey);
    }

    private String resolveImageExtension(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return ".jpg";
        }
        String lower = fileName.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) {
            return ".png";
        }
        if (lower.endsWith(".webp")) {
            return ".webp";
        }
        if (lower.endsWith(".jpeg")) {
            return ".jpeg";
        }
        return ".jpg";
    }
}
