package com.example.douyin.util;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class VideoImportHelper {

    private VideoImportHelper() {
    }

    public static File copyToCache(Context context, Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("视频地址无效");
        }
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("无法读取视频");
        }

        File dir = new File(context.getCacheDir(), "imports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建缓存目录");
        }
        File target = new File(dir, "import_" + System.currentTimeMillis() + ".mp4");
        try (InputStream input = inputStream; OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return target;
    }
}
