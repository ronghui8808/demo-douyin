package com.example.douyin.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
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
import com.example.douyin.network.model.SmsSendResultDto;
import com.example.douyin.network.model.UserDto;
import com.example.douyin.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class BindPhoneActivity extends AppCompatActivity {

    private static final int SMS_COUNTDOWN_SECONDS = 60;

    private AuthRepository authRepository;
    private TextInputLayout tilPhone;
    private TextInputLayout tilCode;
    private TextInputEditText etPhone;
    private TextInputEditText etCode;
    private MaterialButton btnSendCode;
    private MaterialButton btnBindPhone;
    private ProgressBar progressBindPhone;
    private SmsCountdownHelper smsCountdownHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authRepository = new AuthRepository(this);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_bind_phone);
        setupWindowInsets();
        bindViews();
        setupActions();
        blockBackToMain();
    }

    @Override
    protected void onDestroy() {
        if (smsCountdownHelper != null) {
            smsCountdownHelper.cancel();
        }
        super.onDestroy();
    }

    private void setupWindowInsets() {
        View root = findViewById(R.id.bind_phone_root);
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
        btnBindPhone = findViewById(R.id.btn_bind_phone);
        progressBindPhone = findViewById(R.id.progress_bind_phone);
        smsCountdownHelper = new SmsCountdownHelper(btnSendCode, SMS_COUNTDOWN_SECONDS);
    }

    private void setupActions() {
        btnSendCode.setOnClickListener(v -> attemptSendCode());
        btnBindPhone.setOnClickListener(v -> attemptBindPhone());
    }

    private void blockBackToMain() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                moveTaskToBack(true);
            }
        });
    }

    private void attemptSendCode() {
        tilPhone.setError(null);
        String phone = getText(etPhone);
        if (!PhoneValidator.isValidPhone(phone)) {
            tilPhone.setError(getString(R.string.error_phone_invalid));
            return;
        }

        btnSendCode.setEnabled(false);
        authRepository.sendSms(phone, "bind", new ApiCallback<SmsSendResultDto>() {
            @Override
            public void onSuccess(SmsSendResultDto data) {
                smsCountdownHelper.start();
                maybeToastDebugCode(data);
            }

            @Override
            public void onError(int code, String message) {
                btnSendCode.setEnabled(true);
                Toast.makeText(BindPhoneActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attemptBindPhone() {
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
        authRepository.bindPhone(phone, code, new ApiCallback<UserDto>() {
            @Override
            public void onSuccess(UserDto data) {
                setLoading(false);
                Toast.makeText(BindPhoneActivity.this, R.string.bind_phone_success, Toast.LENGTH_SHORT)
                        .show();
                AuthNavigator.goClearTask(BindPhoneActivity.this, MainActivity.class);
            }

            @Override
            public void onError(int code, String message) {
                setLoading(false);
                Toast.makeText(BindPhoneActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void maybeToastDebugCode(SmsSendResultDto data) {
        if (BuildConfig.DEBUG && data != null && !TextUtils.isEmpty(data.debugCode)) {
            Toast.makeText(this, getString(R.string.sms_debug_code, data.debugCode), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private void setLoading(boolean loading) {
        btnBindPhone.setEnabled(!loading);
        btnBindPhone.setText(loading ? "" : getString(R.string.action_bind_phone));
        progressBindPhone.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private static String getText(TextInputEditText editText) {
        if (editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }
}
