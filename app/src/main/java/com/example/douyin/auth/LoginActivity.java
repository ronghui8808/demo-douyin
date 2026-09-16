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
import com.example.douyin.network.model.SmsSendResultDto;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.trace.AuthTrace;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends AppCompatActivity {

    private static final String DEMO_PHONE = "13800138000";
    private static final String DEMO_CODE = "123456";
    private static final int SMS_COUNTDOWN_SECONDS = 60;

    private AuthRepository authRepository;
    private TextInputLayout tilPhone;
    private TextInputLayout tilCode;
    private TextInputEditText etPhone;
    private TextInputEditText etCode;
    private MaterialButton btnSendCode;
    private MaterialButton btnLogin;
    private ProgressBar progressLogin;
    private SmsCountdownHelper smsCountdownHelper;

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

    @Override
    protected void onDestroy() {
        if (smsCountdownHelper != null) {
            smsCountdownHelper.cancel();
        }
        super.onDestroy();
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
        tilPhone = findViewById(R.id.til_phone);
        tilCode = findViewById(R.id.til_code);
        etPhone = findViewById(R.id.et_phone);
        etCode = findViewById(R.id.et_code);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnLogin = findViewById(R.id.btn_login);
        progressLogin = findViewById(R.id.progress_login);
        smsCountdownHelper = new SmsCountdownHelper(btnSendCode, SMS_COUNTDOWN_SECONDS);
    }

    private void setupActions() {
        btnSendCode.setOnClickListener(v -> attemptSendCode());
        btnLogin.setOnClickListener(v -> attemptLogin());
        findViewById(R.id.tv_go_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        MaterialButton skipButton = findViewById(R.id.btn_skip_login);
        if (BuildConfig.DEBUG) {
            skipButton.setVisibility(View.VISIBLE);
            skipButton.setOnClickListener(v -> loginDemoAccount());
        }
    }

    private void attemptSendCode() {
        tilPhone.setError(null);
        String phone = getText(etPhone);
        if (!PhoneValidator.isValidPhone(phone)) {
            tilPhone.setError(getString(R.string.error_phone_invalid));
            return;
        }

        btnSendCode.setEnabled(false);
        authRepository.sendSms(phone, "login", new ApiCallback<SmsSendResultDto>() {
            @Override
            public void onSuccess(SmsSendResultDto data) {
                smsCountdownHelper.start();
                maybeToastDebugCode(data);
            }

            @Override
            public void onError(int code, String message) {
                btnSendCode.setEnabled(true);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attemptLogin() {
        AuthTrace.begin("auth_login_click");
        try {
            tilPhone.setError(null);
            tilCode.setError(null);

            String phone = getText(etPhone);
            String code = getText(etCode);

            if (!PhoneValidator.isValidPhone(phone)) {
                tilPhone.setError(getString(R.string.error_phone_invalid));
                return;
            }
            if (!PhoneValidator.isValidCode(code)) {
                tilCode.setError(getString(R.string.error_code_invalid));
                return;
            }

            setLoading(true);
            authRepository.loginByPhone(phone, code, new ApiCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult data) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, R.string.login_success, Toast.LENGTH_SHORT).show();
                    goToMain();
                }

                @Override
                public void onError(int code, String message) {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        } finally {
            AuthTrace.end();
        }
    }

    private void loginDemoAccount() {
        etPhone.setText(DEMO_PHONE);
        etCode.setText(DEMO_CODE);
        setLoading(true);
        authRepository.sendSms(DEMO_PHONE, "login", new ApiCallback<SmsSendResultDto>() {
            @Override
            public void onSuccess(SmsSendResultDto data) {
                maybeToastDebugCode(data);
                authRepository.loginByPhone(DEMO_PHONE, DEMO_CODE, new ApiCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this, R.string.login_success, Toast.LENGTH_SHORT).show();
                        goToMain();
                    }

                    @Override
                    public void onError(int code, String message) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void maybeToastDebugCode(SmsSendResultDto data) {
        if (BuildConfig.DEBUG && data != null && !TextUtils.isEmpty(data.debugCode)) {
            Toast.makeText(this, getString(R.string.sms_debug_code, data.debugCode), Toast.LENGTH_SHORT).show();
        }
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
