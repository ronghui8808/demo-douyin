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

public class RegisterActivity extends AppCompatActivity {

    private static final int SMS_COUNTDOWN_SECONDS = 60;

    private AuthRepository authRepository;
    private TextInputLayout tilPhone;
    private TextInputLayout tilCode;
    private TextInputLayout tilNickname;
    private TextInputEditText etPhone;
    private TextInputEditText etCode;
    private TextInputEditText etNickname;
    private MaterialButton btnSendCode;
    private MaterialButton btnRegister;
    private ProgressBar progressRegister;
    private SmsCountdownHelper smsCountdownHelper;

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

    @Override
    protected void onDestroy() {
        if (smsCountdownHelper != null) {
            smsCountdownHelper.cancel();
        }
        super.onDestroy();
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
        tilPhone = findViewById(R.id.til_phone);
        tilCode = findViewById(R.id.til_code);
        tilNickname = findViewById(R.id.til_nickname);
        etPhone = findViewById(R.id.et_phone);
        etCode = findViewById(R.id.et_code);
        etNickname = findViewById(R.id.et_nickname);
        btnSendCode = findViewById(R.id.btn_send_code);
        btnRegister = findViewById(R.id.btn_register);
        progressRegister = findViewById(R.id.progress_register);
        smsCountdownHelper = new SmsCountdownHelper(btnSendCode, SMS_COUNTDOWN_SECONDS);
    }

    private void setupActions() {
        btnSendCode.setOnClickListener(v -> attemptSendCode());
        btnRegister.setOnClickListener(v -> attemptRegister());
        findViewById(R.id.tv_go_login).setOnClickListener(v -> finish());
    }

    private void attemptSendCode() {
        tilPhone.setError(null);
        String phone = getText(etPhone);
        if (!PhoneValidator.isValidPhone(phone)) {
            tilPhone.setError(getString(R.string.error_phone_invalid));
            return;
        }

        btnSendCode.setEnabled(false);
        authRepository.sendSms(phone, "register", new ApiCallback<SmsSendResultDto>() {
            @Override
            public void onSuccess(SmsSendResultDto data) {
                smsCountdownHelper.start();
                maybeToastDebugCode(data);
            }

            @Override
            public void onError(int code, String message) {
                btnSendCode.setEnabled(true);
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attemptRegister() {
        AuthTrace.begin("auth_register_click");
        try {
            clearErrors();

            String phone = getText(etPhone);
            String code = getText(etCode);
            String nickname = getText(etNickname);

            if (!PhoneValidator.isValidPhone(phone)) {
                tilPhone.setError(getString(R.string.error_phone_invalid));
                return;
            }
            if (!PhoneValidator.isValidCode(code)) {
                tilCode.setError(getString(R.string.error_code_invalid));
                return;
            }
            if (TextUtils.isEmpty(nickname)) {
                tilNickname.setError(getString(R.string.error_nickname_empty));
                return;
            }

            setLoading(true);
            authRepository.registerByPhone(phone, code, nickname, new ApiCallback<LoginResult>() {
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

    private void maybeToastDebugCode(SmsSendResultDto data) {
        if (BuildConfig.DEBUG && data != null && !TextUtils.isEmpty(data.debugCode)) {
            Toast.makeText(this, getString(R.string.sms_debug_code, data.debugCode), Toast.LENGTH_SHORT).show();
        }
    }

    private void clearErrors() {
        tilPhone.setError(null);
        tilCode.setError(null);
        tilNickname.setError(null);
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
