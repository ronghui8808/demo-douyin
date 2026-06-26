package com.example.douyin.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;

public final class TokenStore {

    private static final String PREFS_NAME = "douyin_auth";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";

    private static volatile TokenStore instance;

    private final SharedPreferences prefs;

    private TokenStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static TokenStore get(Context context) {
        if (instance == null) {
            synchronized (TokenStore.class) {
                if (instance == null) {
                    instance = new TokenStore(context);
                }
            }
        }
        return instance;
    }

    public void saveToken(String token, long userId) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_USER_ID, userId)
                .apply();
    }

    @Nullable
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1L);
    }

    public boolean isLoggedIn() {
        return !TextUtils.isEmpty(getToken());
    }

    public void clear() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .apply();
    }
}
