package com.example.douyin.auth;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.douyin.R;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.trace.AuthTrace;
import com.example.douyin.util.SingleLiveEvent;

public class RegisterViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final Application application;
    private final MutableLiveData<RegisterUiState> uiState =
            new MutableLiveData<>(RegisterUiState.idle());
    private final SingleLiveEvent<Void> navigateMain = new SingleLiveEvent<>();

    public RegisterViewModel(AuthRepository authRepository, Application application) {
        this.authRepository = authRepository;
        this.application = application;
    }

    public LiveData<RegisterUiState> getUiState() {
        return uiState;
    }

    public LiveData<Void> getNavigateMain() {
        return navigateMain;
    }

    public void register(String username, String nickname, String password, String passwordConfirm) {
        AuthTrace.begin("auth_register_click");
        try {
            if (TextUtils.isEmpty(username)) {
                uiState.setValue(RegisterUiState.usernameError(
                        application.getString(R.string.error_username_empty)));
                return;
            }
            if (TextUtils.isEmpty(nickname)) {
                uiState.setValue(RegisterUiState.nicknameError(
                        application.getString(R.string.error_nickname_empty)));
                return;
            }
            if (TextUtils.isEmpty(password)) {
                uiState.setValue(RegisterUiState.passwordError(
                        application.getString(R.string.error_password_empty)));
                return;
            }
            if (password.length() < 6) {
                uiState.setValue(RegisterUiState.passwordError(
                        application.getString(R.string.error_password_short)));
                return;
            }
            if (!password.equals(passwordConfirm)) {
                uiState.setValue(RegisterUiState.passwordConfirmError(
                        application.getString(R.string.error_password_mismatch)));
                return;
            }

            uiState.setValue(RegisterUiState.loading());
            authRepository.register(username, password, nickname, new ApiCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult data) {
                    uiState.setValue(RegisterUiState.idle());
                    navigateMain.call();
                }

                @Override
                public void onError(int code, String message) {
                    uiState.setValue(RegisterUiState.toast(message));
                }
            });
        } finally {
            AuthTrace.end();
        }
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final AuthRepository authRepository;
        private final Application application;

        public Factory(AuthRepository authRepository, Application application) {
            this.authRepository = authRepository;
            this.application = application;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new RegisterViewModel(authRepository, application);
        }
    }
}
