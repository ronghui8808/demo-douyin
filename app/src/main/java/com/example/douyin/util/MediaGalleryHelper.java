package com.example.douyin.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.Nullable;

public final class MediaGalleryHelper {

    private MediaGalleryHelper() {
    }

    public static void loadLatestThumbnail(Context context, ImageView target, int sizePx) {
        AppExecutors.get().diskIo(() -> {
            Uri latestUri = queryLatestMediaUri(context);
            Bitmap bitmap = latestUri != null ? loadThumbnail(context, latestUri, sizePx) : null;
            AppExecutors.get().mainThread(() -> {
                if (bitmap != null) {
                    target.setImageBitmap(bitmap);
                    target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                } else {
                    target.setImageDrawable(null);
                    target.setScaleType(ImageView.ScaleType.CENTER);
                }
            });
        });
    }

    @Nullable
    private static Uri queryLatestMediaUri(Context context) {
        Uri videoUri = queryLatestFromStore(
                context,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED
        );
        Uri imageUri = queryLatestFromStore(
                context,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
        );
        if (videoUri == null) {
            return imageUri;
        }
        if (imageUri == null) {
            return videoUri;
        }
        long videoDate = queryDateAdded(context, videoUri);
        long imageDate = queryDateAdded(context, imageUri);
        return videoDate >= imageDate ? videoUri : imageUri;
    }

    @Nullable
    private static Uri queryLatestFromStore(Context context, Uri collection, String idColumn, String dateColumn) {
        String[] projection = {idColumn, dateColumn};
        try (Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                null,
                null,
                dateColumn + " DESC"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(idColumn));
                return ContentUris.withAppendedId(collection, id);
            }
        } catch (Exception ignored) {
            // 无存储权限或设备无媒体时显示占位
        }
        return null;
    }

    private static long queryDateAdded(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{MediaStore.MediaColumns.DATE_ADDED},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED));
            }
        } catch (Exception ignored) {
            // ignore
        }
        return 0L;
    }

    @Nullable
    private static Bitmap loadThumbnail(Context context, Uri uri, int sizePx) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return context.getContentResolver().loadThumbnail(uri, new Size(sizePx, sizePx), null);
            }
            long id = ContentUris.parseId(uri);
            if (uri.toString().contains("video")) {
                return MediaStore.Video.Thumbnails.getThumbnail(
                        context.getContentResolver(),
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                );
            }
            return MediaStore.Images.Thumbnails.getThumbnail(
                    context.getContentResolver(),
                    id,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null
            );
        } catch (Exception ignored) {
            return null;
        }
    }
}
