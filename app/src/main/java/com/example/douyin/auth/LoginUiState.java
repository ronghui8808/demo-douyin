package com.example.douyin.auth;

public final class LoginUiState {

    public final boolean loading;
    public final String usernameError;
    public final String passwordError;
    public final String toastMessage;

    private LoginUiState(boolean loading,
                         String usernameError,
                         String passwordError,
                         String toastMessage) {
        this.loading = loading;
        this.usernameError = usernameError;
        this.passwordError = passwordError;
        this.toastMessage = toastMessage;
    }

    public static LoginUiState idle() {
        return new LoginUiState(false, null, null, null);
    }

    public static LoginUiState loading() {
        return new LoginUiState(true, null, null, null);
    }

    public static LoginUiState usernameError(String message) {
        return new LoginUiState(false, message, null, null);
    }

    public static LoginUiState passwordError(String message) {
        return new LoginUiState(false, null, message, null);
    }

    public static LoginUiState toast(String message) {
        return new LoginUiState(false, null, null, message);
    }
}
