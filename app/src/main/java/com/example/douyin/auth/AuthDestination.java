package com.example.douyin.auth;

public enum AuthDestination {
    LOGIN, BIND_PHONE, MAIN;

    public static AuthDestination resolve(boolean hasToken, boolean hasPhone) {
        if (!hasToken) {
            return LOGIN;
        }
        return hasPhone ? MAIN : BIND_PHONE;
    }
}
