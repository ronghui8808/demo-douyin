package com.example.douyin.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.douyin.MainActivity;
import com.example.douyin.R;
import com.example.douyin.network.TokenStore;
import com.example.douyin.trace.AuthTrace;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_splash);
        findViewById(R.id.tv_splash).post(this::routeNextScreen);
    }

    private void routeNextScreen() {
        TokenStore store = TokenStore.get(this);
        if (store.isHydrated()) {
            navigate(store.isLoggedIn());
            return;
        }
        store.hydrate(() -> runOnUiThread(() -> navigate(store.isLoggedIn())));
    }

    private void navigate(boolean loggedIn) {
        AuthTrace.begin("auth_splash_route");
        try {
            Intent intent = loggedIn
                    ? new Intent(this, MainActivity.class)
                    : new Intent(this, LoginActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
            overridePendingTransition(0, 0);
        } finally {
            AuthTrace.end();
        }
    }
}
