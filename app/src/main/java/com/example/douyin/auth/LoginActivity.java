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

import com.example.douyin.BuildConfig;
import com.example.douyin.MainActivity;
import com.example.douyin.R;
import com.example.douyin.databinding.ActivityLoginBinding;
import com.example.douyin.repository.AuthRepository;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthRepository authRepository = new AuthRepository(this);

        if (authRepository.isLoggedIn()) {
            goToMain();
            return;
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupWindowInsets();

        viewModel = new ViewModelProvider(this,
                new LoginViewModel.Factory(authRepository, getApplication()))
                .get(LoginViewModel.class);

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
        binding.btnLogin.setOnClickListener(v ->
                viewModel.login(getText(binding.etUsername), getText(binding.etPassword)));

        binding.tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        if (BuildConfig.DEBUG) {
            binding.btnSkipLogin.setVisibility(View.VISIBLE);
            binding.btnSkipLogin.setOnClickListener(v -> {
                binding.etUsername.setText("demo");
                binding.etPassword.setText("123456");
                viewModel.loginDemo();
            });
        }
    }

    private void observeViewModel() {
        viewModel.getUiState().observe(this, this::render);
        viewModel.getNavigateMain().observe(this, ignored -> {
            Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
            goToMain();
        });
    }

    private void render(LoginUiState state) {
        if (state == null) {
            return;
        }
        binding.tilUsername.setError(state.usernameError);
        binding.tilPassword.setError(state.passwordError);
        setLoading(state.loading);
        if (state.toastMessage != null) {
            Toast.makeText(this, state.toastMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnLogin.setText(loading ? "" : getString(R.string.action_login));
        binding.progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
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
