package com.example.douyin.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.widget.ImageView;

import com.example.douyin.cache.MediaCacheManager;

import java.io.File;

public final class CoverImageLoader {

    private CoverImageLoader() {
    }

    public static void load(ImageView imageView, String url) {
        if (imageView == null || TextUtils.isEmpty(url)) {
            return;
        }
        String normalized = url.trim();
        AppExecutors.get().diskIo(() -> {
            Bitmap bitmap = decode(normalized, imageView.getContext());
            if (bitmap == null) {
                return;
            }
            AppExecutors.get().mainThread(() -> {
                Object tag = imageView.getTag();
                if (normalized.equals(tag)) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
        imageView.setTag(normalized);
    }

    private static Bitmap decode(String url, android.content.Context context) {
        try {
            if (url.startsWith("file://")) {
                return BitmapFactory.decodeFile(url.substring(7));
            }
            if (url.startsWith("http://") || url.startsWith("https://")) {
                File cached = MediaCacheManager.get(context).getCachedImageFile(url);
                if (cached != null) {
                    return BitmapFactory.decodeFile(cached.getAbsolutePath());
                }
                MediaCacheManager.get(context).prefetchImage(url);
                return null;
            }
            return BitmapFactory.decodeFile(url);
        } catch (Exception ignored) {
            return null;
        }
    }
}
