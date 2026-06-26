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

import com.example.douyin.MainActivity;
import com.example.douyin.R;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.trace.AuthTrace;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    private AuthRepository authRepository;
    private TextInputLayout tilUsername;
    private TextInputLayout tilNickname;
    private TextInputLayout tilPassword;
    private TextInputLayout tilPasswordConfirm;
    private TextInputEditText etUsername;
    private TextInputEditText etNickname;
    private TextInputEditText etPassword;
    private TextInputEditText etPasswordConfirm;
    private MaterialButton btnRegister;
    private ProgressBar progressRegister;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authRepository = new AuthRepository(this);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_register);
        setupWindowInsets();
        bindViews();
        setupActions();
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.register_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void bindViews() {
        tilUsername = findViewById(R.id.til_username);
        tilNickname = findViewById(R.id.til_nickname);
        tilPassword = findViewById(R.id.til_password);
        tilPasswordConfirm = findViewById(R.id.til_password_confirm);
        etUsername = findViewById(R.id.et_username);
        etNickname = findViewById(R.id.et_nickname);
        etPassword = findViewById(R.id.et_password);
        etPasswordConfirm = findViewById(R.id.et_password_confirm);
        btnRegister = findViewById(R.id.btn_register);
        progressRegister = findViewById(R.id.progress_register);
    }

    private void setupActions() {
        btnRegister.setOnClickListener(v -> attemptRegister());
        findViewById(R.id.tv_go_login).setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        AuthTrace.begin("auth_register_click");
        try {
            clearErrors();

            String username = getText(etUsername);
            String nickname = getText(etNickname);
            String password = getText(etPassword);
            String passwordConfirm = getText(etPasswordConfirm);

            if (TextUtils.isEmpty(username)) {
                tilUsername.setError(getString(R.string.error_username_empty));
                return;
            }
            if (TextUtils.isEmpty(nickname)) {
                tilNickname.setError(getString(R.string.error_nickname_empty));
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
            if (!password.equals(passwordConfirm)) {
                tilPasswordConfirm.setError(getString(R.string.error_password_mismatch));
                return;
            }

            setLoading(true);
            authRepository.register(username, password, nickname, new ApiCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult data) {
                    setLoading(false);
                    Toast.makeText(RegisterActivity.this, R.string.register_success, Toast.LENGTH_SHORT).show();
                    goToMain();
                }

                @Override
                public void onError(int code, String message) {
                    setLoading(false);
                    Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } finally {
            AuthTrace.end();
        }
    }

    private void clearErrors() {
        tilUsername.setError(null);
        tilNickname.setError(null);
        tilPassword.setError(null);
        tilPasswordConfirm.setError(null);
    }

    private void setLoading(boolean loading) {
        btnRegister.setEnabled(!loading);
        btnRegister.setText(loading ? "" : getString(R.string.action_register));
        progressRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
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
