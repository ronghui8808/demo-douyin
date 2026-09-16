package com.example.douyin.auth;

public final class PhoneValidator {
    private PhoneValidator() {}

    public static boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }

    public static boolean isValidCode(String code) {
        return code != null && code.matches("^\\d{6}$");
    }
}
