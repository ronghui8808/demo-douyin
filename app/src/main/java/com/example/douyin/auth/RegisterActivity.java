package com.example.douyin.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.douyin.MainActivity;
import com.example.douyin.R;
import com.example.douyin.databinding.ActivityRegisterBinding;
import com.example.douyin.repository.AuthRepository;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private RegisterViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthRepository authRepository = new AuthRepository(this);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupWindowInsets();

        viewModel = new ViewModelProvider(this,
                new RegisterViewModel.Factory(authRepository, getApplication()))
                .get(RegisterViewModel.class);

        setupActions();
        observeViewModel();
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupActions() {
        binding.btnRegister.setOnClickListener(v ->
                viewModel.register(
                        getText(binding.etUsername),
                        getText(binding.etNickname),
                        getText(binding.etPassword),
                        getText(binding.etPasswordConfirm)));

        binding.tvGoLogin.setOnClickListener(v -> finish());
    }

    private void observeViewModel() {
        viewModel.getUiState().observe(this, this::render);
        viewModel.getNavigateMain().observe(this, ignored -> {
            Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
            goToMain();
        });
    }

    private void render(RegisterUiState state) {
        if (state == null) {
            return;
        }
        binding.tilUsername.setError(state.usernameError);
        binding.tilNickname.setError(state.nicknameError);
        binding.tilPassword.setError(state.passwordError);
        binding.tilPasswordConfirm.setError(state.passwordConfirmError);
        setLoading(state.loading);
        if (state.toastMessage != null) {
            Toast.makeText(this, state.toastMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void setLoading(boolean loading) {
        binding.btnRegister.setEnabled(!loading);
        binding.btnRegister.setText(loading ? "" : getString(R.string.action_register));
        binding.progressRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
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
