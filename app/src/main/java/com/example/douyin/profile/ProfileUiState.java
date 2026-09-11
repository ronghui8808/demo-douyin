package com.example.douyin.profile;

public final class ProfileUiState {

    public final boolean needLogin;
    public final long userId;

    private ProfileUiState(boolean needLogin, long userId) {
        this.needLogin = needLogin;
        this.userId = userId;
    }

    public static ProfileUiState needLogin() {
        return new ProfileUiState(true, 0L);
    }

    public static ProfileUiState ready(long userId) {
        return new ProfileUiState(false, userId);
    }
}
