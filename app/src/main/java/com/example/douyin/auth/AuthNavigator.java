package com.example.douyin.auth;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.douyin.MainActivity;
import com.example.douyin.network.model.UserDto;

public final class AuthNavigator {

    private AuthNavigator() {}

    public static void openByDestination(Activity activity, AuthDestination destination) {
        if (activity == null || destination == null) {
            return;
        }
        switch (destination) {
            case LOGIN:
                goClearTask(activity, LoginActivity.class);
                break;
            case BIND_PHONE:
                goClearTask(activity, BindPhoneActivity.class);
                break;
            case MAIN:
                goClearTask(activity, MainActivity.class);
                break;
            default:
                goClearTask(activity, LoginActivity.class);
                break;
        }
    }

    public static void openAfterAuth(Activity activity, @Nullable UserDto user) {
        boolean hasPhone = user != null && !TextUtils.isEmpty(user.phone);
        openByDestination(activity, AuthDestination.resolve(true, hasPhone));
    }

    public static void goClearTask(Activity activity, Class<?> cls) {
        Intent intent = new Intent(activity, cls);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
