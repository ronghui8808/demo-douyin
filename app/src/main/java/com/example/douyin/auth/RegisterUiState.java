package com.example.douyin.auth;

public final class RegisterUiState {

    public final boolean loading;
    public final String usernameError;
    public final String nicknameError;
    public final String passwordError;
    public final String passwordConfirmError;
    public final String toastMessage;

    private RegisterUiState(boolean loading,
                            String usernameError,
                            String nicknameError,
                            String passwordError,
                            String passwordConfirmError,
                            String toastMessage) {
        this.loading = loading;
        this.usernameError = usernameError;
        this.nicknameError = nicknameError;
        this.passwordError = passwordError;
        this.passwordConfirmError = passwordConfirmError;
        this.toastMessage = toastMessage;
    }

    public static RegisterUiState idle() {
        return new RegisterUiState(false, null, null, null, null, null);
    }

    public static RegisterUiState loading() {
        return new RegisterUiState(true, null, null, null, null, null);
    }

    public static RegisterUiState usernameError(String message) {
        return new RegisterUiState(false, message, null, null, null, null);
    }

    public static RegisterUiState nicknameError(String message) {
        return new RegisterUiState(false, null, message, null, null, null);
    }

    public static RegisterUiState passwordError(String message) {
        return new RegisterUiState(false, null, null, message, null, null);
    }

    public static RegisterUiState passwordConfirmError(String message) {
        return new RegisterUiState(false, null, null, null, message, null);
    }

    public static RegisterUiState toast(String message) {
        return new RegisterUiState(false, null, null, null, null, message);
    }
}
