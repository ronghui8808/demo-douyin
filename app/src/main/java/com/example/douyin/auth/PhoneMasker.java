package com.example.douyin.auth;

public final class PhoneMasker {
    private PhoneMasker() {}

    public static String mask(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
