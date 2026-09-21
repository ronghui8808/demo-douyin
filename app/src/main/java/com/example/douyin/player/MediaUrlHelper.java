package com.example.douyin.player;

public final class MediaUrlHelper {

    public static final long PREFETCH_BYTES = 2L * 1024L * 1024L;

    private MediaUrlHelper() {
    }

    public static String normalize(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.trim();
    }

    public static boolean isRemoteHttpUrl(String url) {
        String normalized = normalize(url);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    public static boolean shouldPrefetch(String url) {
        return isRemoteHttpUrl(url);
    }
}
