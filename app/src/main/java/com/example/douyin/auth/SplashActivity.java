package com.example.douyin.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.example.douyin.MainActivity;
import com.example.douyin.R;
import com.example.douyin.network.TokenStore;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_splash);
        // 延迟到下一帧再跳转，避免与系统 Splash 动画冲突（MIUI TransitionChain 警告）
        findViewById(R.id.tv_splash).post(this::routeNextScreen);
    }

    private void routeNextScreen() {
        Intent intent;
        if (TokenStore.get(this).isLoggedIn()) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }
}
