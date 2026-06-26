package com.example.douyin.util;

public final class TimeFormatter {

    private TimeFormatter() {
    }

    public static String formatRelative(long timestampMs) {
        long diff = Math.max(0, System.currentTimeMillis() - timestampMs);
        if (diff < 60_000L) {
            return "刚刚";
        }
        if (diff < 3_600_000L) {
            return (diff / 60_000L) + "分钟前";
        }
        if (diff < 86_400_000L) {
            return (diff / 3_600_000L) + "小时前";
        }
        if (diff < 2_592_000_000L) {
            return (diff / 86_400_000L) + "天前";
        }
        return "30天前";
    }
}
