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
import androidx.lifecycle.ViewModelProvider;

import com.example.douyin.R;
import com.example.douyin.auth.LoginActivity;
import com.example.douyin.databinding.FragmentProfileBinding;
import com.example.douyin.repository.AuthRepository;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;
    private UserProfileController profileController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AuthRepository authRepository = new AuthRepository(requireContext());
        viewModel = new ViewModelProvider(this, new ProfileViewModel.Factory(authRepository))
                .get(ProfileViewModel.class);

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.refreshSession();
    }

    public void refreshProfile() {
        if (profileController != null) {
            profileController.refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.refreshSession();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        profileController = null;
        binding = null;
    }

    private void render(ProfileUiState state) {
        if (state.needLogin) {
            navigateToLogin();
            return;
        }
        ensureController(state.userId);
    }

    private void ensureController(long userId) {
        if (binding == null || userId <= 0) {
            return;
        }
        if (profileController == null) {
            profileController = new UserProfileController(
                    binding.getRoot(),
                    userId,
                    true,
                    false,
                    this::onLogoutRequested
            );
            profileController.load();
        }
    }

    private void onLogoutRequested() {
        viewModel.logout();
        Toast.makeText(requireContext(), R.string.logout_success, Toast.LENGTH_SHORT).show();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}
