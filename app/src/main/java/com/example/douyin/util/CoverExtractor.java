package com.example.douyin.util;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class CoverExtractor {

    private CoverExtractor() {
    }

    public static File extractFirstFrame(File videoFile, File outputDir) throws IOException {
        if (videoFile == null || !videoFile.exists()) {
            throw new IOException("视频文件无效");
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("无法创建封面目录");
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoFile.getAbsolutePath());
            Bitmap frame = retriever.getFrameAtTime(0);
            if (frame == null) {
                throw new IOException("无法提取封面");
            }
            File coverFile = new File(outputDir, "cover_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream output = new FileOutputStream(coverFile)) {
                frame.compress(Bitmap.CompressFormat.JPEG, 85, output);
            }
            frame.recycle();
            return coverFile;
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
            }
        }
    }
}
