package com.example.douyin.auth;

import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.douyin.R;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.TokenStore;
import com.example.douyin.network.model.UserDto;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.trace.AuthTrace;

public class SplashActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private boolean routed;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_splash);
        authRepository = new AuthRepository(this);
        findViewById(R.id.tv_splash).post(this::routeNextScreen);
    }

    private void routeNextScreen() {
        if (routed || isFinishing()) {
            return;
        }
        AuthTrace.begin("auth_splash_route");
        try {
            boolean loggedIn = TokenStore.get(this).isLoggedIn();
            if (!loggedIn) {
                openDestination(AuthDestination.LOGIN);
                return;
            }

            authRepository.getMe(new ApiCallback<UserDto>() {
                @Override
                public void onSuccess(UserDto data) {
                    if (routed || isFinishing()) {
                        return;
                    }
                    boolean hasPhone = data != null && !TextUtils.isEmpty(data.phone);
                    openDestination(AuthDestination.resolve(true, hasPhone));
                }

                @Override
                public void onError(int code, String message) {
                    if (routed || isFinishing()) {
                        return;
                    }
                    // Prefer clearSession + Login on any getMe failure (401 or network);
                    // never route to BindPhone from Splash on error.
                    authRepository.logout();
                    openDestination(AuthDestination.LOGIN);
                }
            });
        } finally {
            AuthTrace.end();
        }
    }

    private void openDestination(AuthDestination destination) {
        if (routed || isFinishing()) {
            return;
        }
        routed = true;
        AuthNavigator.openByDestination(this, destination);
        overridePendingTransition(0, 0);
    }
}
