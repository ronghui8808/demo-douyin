package com.example.douyin.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.douyin.R;
import com.example.douyin.auth.LoginActivity;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.UserDto;
import com.example.douyin.repository.AuthRepository;
import com.google.android.material.button.MaterialButton;

public class ProfileFragment extends Fragment {

    private AuthRepository authRepository;
    private TextView tvNickname;
    private TextView tvUsername;
    private ProgressBar progressProfile;
    private MaterialButton btnLogout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        authRepository = new AuthRepository(requireContext());

        tvNickname = view.findViewById(R.id.tv_nickname);
        tvUsername = view.findViewById(R.id.tv_username);
        progressProfile = view.findViewById(R.id.progress_profile);
        btnLogout = view.findViewById(R.id.btn_logout);

        btnLogout.setOnClickListener(v -> logout());
        loadUserInfo();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (authRepository.isLoggedIn()) {
            loadUserInfo();
        }
    }

    private void loadUserInfo() {
        if (!authRepository.isLoggedIn()) {
            tvNickname.setText(R.string.profile_load_failed);
            tvUsername.setText("");
            return;
        }

        progressProfile.setVisibility(View.VISIBLE);
        authRepository.getMe(new ApiCallback<UserDto>() {
            @Override
            public void onSuccess(UserDto data) {
                if (!isAdded()) {
                    return;
                }
                progressProfile.setVisibility(View.GONE);
                tvNickname.setText(data.nickname);
                tvUsername.setText(getString(R.string.hint_username) + ": " + data.username);
            }

            @Override
            public void onError(int code, String message) {
                if (!isAdded()) {
                    return;
                }
                progressProfile.setVisibility(View.GONE);
                tvNickname.setText(R.string.profile_load_failed);
                tvUsername.setText(message);
                if (code == 401) {
                    navigateToLogin();
                }
            }
        });
    }

    private void logout() {
        authRepository.logout();
        Toast.makeText(requireContext(), R.string.logout_success, Toast.LENGTH_SHORT).show();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
