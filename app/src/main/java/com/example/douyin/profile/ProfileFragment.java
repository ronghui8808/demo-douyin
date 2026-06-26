package com.example.douyin.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.douyin.R;
import com.example.douyin.auth.LoginActivity;
import com.example.douyin.repository.AuthRepository;

public class ProfileFragment extends Fragment {

    private AuthRepository authRepository;
    private UserProfileController profileController;

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

        if (!authRepository.isLoggedIn()) {
            navigateToLogin();
            return;
        }

        profileController = new UserProfileController(
                view,
                authRepository.getUserId(),
                true,
                false,
                this::logout
        );
        profileController.load();
    }

    public void refreshProfile() {
        if (profileController != null) {
            profileController.refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (authRepository == null) {
            authRepository = new AuthRepository(requireContext());
        }
        if (!authRepository.isLoggedIn()) {
            navigateToLogin();
        }
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
