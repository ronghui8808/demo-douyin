package com.example.douyin.network;

import androidx.annotation.Nullable;

final class TokenMemoryCache {
    @Nullable String token;
    long userId = -1L;

    synchronized void set(@Nullable String token, long userId) {
        this.token = token;
        this.userId = userId;
    }

    synchronized void clear() {
        token = null;
        userId = -1L;
    }

    @Nullable
    synchronized String getToken() {
        return token;
    }

    synchronized long getUserId() {
        return userId;
    }

    synchronized boolean isLoggedIn() {
        return token != null && !token.isEmpty();
    }
}
