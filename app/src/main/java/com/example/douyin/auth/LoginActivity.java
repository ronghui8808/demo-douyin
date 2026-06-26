package com.example.douyin.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.douyin.BuildConfig;
import com.example.douyin.MainActivity;
import com.example.douyin.R;
import com.example.douyin.network.TokenStore;
import com.google.android.material.button.MaterialButton;

public class LoginActivity extends AppCompatActivity {

    private static final String DEBUG_TOKEN = "debug-token";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_login);

        View root = findViewById(R.id.tv_login_title).getRootView();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView titleView = findViewById(R.id.tv_login_title);
        titleView.setText(R.string.login_title);

        MaterialButton skipButton = findViewById(R.id.btn_skip_login);
        if (BuildConfig.DEBUG) {
            skipButton.setVisibility(View.VISIBLE);
            skipButton.setOnClickListener(v -> enterMainForDebug());
        }
    }

    private void enterMainForDebug() {
        TokenStore.get(this).saveToken(DEBUG_TOKEN, 1L);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
