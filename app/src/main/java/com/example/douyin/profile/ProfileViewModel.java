package com.example.douyin.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.douyin.repository.AuthRepository;

public class ProfileViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<ProfileUiState> uiState = new MutableLiveData<>();

    public ProfileViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<ProfileUiState> getUiState() {
        return uiState;
    }

    public boolean isLoggedIn() {
        return authRepository.isLoggedIn();
    }

    public long getUserId() {
        return authRepository.getUserId();
    }

    public void refreshSession() {
        if (!authRepository.isLoggedIn()) {
            uiState.setValue(ProfileUiState.needLogin());
        } else {
            uiState.setValue(ProfileUiState.ready(authRepository.getUserId()));
        }
    }

    public void logout() {
        authRepository.logout();
        uiState.setValue(ProfileUiState.needLogin());
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final AuthRepository authRepository;

        public Factory(AuthRepository authRepository) {
            this.authRepository = authRepository;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new ProfileViewModel(authRepository);
        }
    }
}
