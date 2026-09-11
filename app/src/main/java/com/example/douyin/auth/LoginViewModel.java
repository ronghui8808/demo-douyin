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

public class LoginViewModel extends ViewModel {

    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "123456";

    private final AuthRepository authRepository;
    private final Application application;
    private final MutableLiveData<LoginUiState> uiState = new MutableLiveData<>(LoginUiState.idle());
    private final SingleLiveEvent<Void> navigateMain = new SingleLiveEvent<>();

    public LoginViewModel(AuthRepository authRepository, Application application) {
        this.authRepository = authRepository;
        this.application = application;
    }

    public LiveData<LoginUiState> getUiState() {
        return uiState;
    }

    public LiveData<Void> getNavigateMain() {
        return navigateMain;
    }

    public void login(String username, String password) {
        AuthTrace.begin("auth_login_click");
        try {
            if (TextUtils.isEmpty(username)) {
                uiState.setValue(LoginUiState.usernameError(
                        application.getString(R.string.error_username_empty)));
                return;
            }
            if (TextUtils.isEmpty(password)) {
                uiState.setValue(LoginUiState.passwordError(
                        application.getString(R.string.error_password_empty)));
                return;
            }
            if (password.length() < 6) {
                uiState.setValue(LoginUiState.passwordError(
                        application.getString(R.string.error_password_short)));
                return;
            }

            uiState.setValue(LoginUiState.loading());
            authRepository.login(username, password, new ApiCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult data) {
                    uiState.setValue(LoginUiState.idle());
                    navigateMain.call();
                }

                @Override
                public void onError(int code, String message) {
                    uiState.setValue(LoginUiState.toast(message));
                }
            });
        } finally {
            AuthTrace.end();
        }
    }

    public void loginDemo() {
        login(DEMO_USERNAME, DEMO_PASSWORD);
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
            return (T) new LoginViewModel(authRepository, application);
        }
    }
}
