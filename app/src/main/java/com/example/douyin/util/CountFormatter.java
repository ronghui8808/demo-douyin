package com.example.douyin.util;

public final class CountFormatter {

    private CountFormatter() {
    }

    public static String format(int count) {
        if (count < 10000) {
            return String.valueOf(count);
        }
        if (count < 100000000) {
            float value = count / 10000f;
            if (value >= 10) {
                return ((int) value) + "万";
            }
            return String.format("%.1f万", value).replace(".0万", "万");
        }
        float value = count / 100000000f;
        return String.format("%.1f亿", value).replace(".0亿", "亿");
    }
}
