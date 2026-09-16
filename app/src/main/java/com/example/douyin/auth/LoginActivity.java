package com.example.douyin.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.douyin.BuildConfig;
import com.example.douyin.MainActivity;
import com.example.douyin.R;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.trace.AuthTrace;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private TextInputLayout tilUsername;
    private TextInputLayout tilPassword;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressLogin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authRepository = new AuthRepository(this);

        if (authRepository.isLoggedIn()) {
            goToMain();
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_login);
        setupWindowInsets();
        bindViews();
        setupActions();
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.login_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        tilUsername = findViewById(R.id.til_username);
        tilPassword = findViewById(R.id.til_password);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        progressLogin = findViewById(R.id.progress_login);
    }

    private void setupActions() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.tv_go_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        MaterialButton skipButton = findViewById(R.id.btn_skip_login);
        if (BuildConfig.DEBUG) {
            skipButton.setVisibility(View.VISIBLE);
            skipButton.setOnClickListener(v -> loginDemoAccount());
        }
    }

    private void attemptLogin() {
        AuthTrace.begin("auth_login_click");
        try {
            tilUsername.setError(null);
            tilPassword.setError(null);

            String username = getText(etUsername);
            String password = getText(etPassword);

            if (TextUtils.isEmpty(username)) {
                tilUsername.setError(getString(R.string.error_username_empty));
                return;
            }
            if (TextUtils.isEmpty(password)) {
                tilPassword.setError(getString(R.string.error_password_empty));
                return;
            }
            if (password.length() < 6) {
                tilPassword.setError(getString(R.string.error_password_short));
                return;
            }

            setLoading(true);
            authRepository.login(username, password, new ApiCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult data) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, R.string.login_success, Toast.LENGTH_SHORT).show();
                    goToMain();
                }
                @Override
                public void onError(int code, String message) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, R.string.login_failure, Toast.LENGTH_SHORT).show();
                }
            });
        } finally {
            AuthTrace.end();
        }
    }

    private void loginDemoAccount() {
        etUsername.setText("demo");
        etPassword.setText("123456");
        attemptLogin();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "" : getString(R.string.action_login));
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String getText(TextInputEditText editText) {
        if (editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }
}
