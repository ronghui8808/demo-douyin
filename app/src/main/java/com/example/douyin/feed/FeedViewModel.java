package com.example.douyin.feed;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.VideoDto;
import com.example.douyin.repository.VideoRepository;
import com.example.douyin.util.SingleLiveEvent;

import java.util.ArrayList;
import java.util.List;

public class FeedViewModel extends ViewModel {

    private final FeedDataSource dataSource;
    private final int pageSize;
    private final MutableLiveData<FeedUiState> uiState = new MutableLiveData<>(FeedUiState.loading());
    private final SingleLiveEvent<String> loginRequired = new SingleLiveEvent<>();

    public FeedViewModel(FeedDataSource dataSource, int pageSize) {
        this.dataSource = dataSource;
        this.pageSize = pageSize;
    }

    public LiveData<FeedUiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getLoginRequired() {
        return loginRequired;
    }

    public void loadFeed() {
        uiState.setValue(FeedUiState.loading());
        dataSource.getFeed(0, pageSize, new ApiCallback<FeedPage>() {
            @Override
            public void onSuccess(FeedPage data) {
                List<VideoDto> list = data != null && data.list != null ? data.list : new ArrayList<>();
                uiState.postValue(FeedUiState.success(list));
            }

            @Override
            public void onError(int code, String message) {
                uiState.postValue(FeedUiState.error(message != null ? message : "加载失败"));
            }
        });
    }

    public void toggleLike(long videoId, boolean loggedIn) {
        if (!loggedIn) {
            loginRequired.setValue("need_login");
            return;
        }
        dataSource.toggleLike(videoId, new ApiCallback<LikeResult>() {
            @Override
            public void onSuccess(LikeResult data) {
                if (data != null) {
                    updateLike(videoId, data.isLiked, data.likeCount);
                }
            }

            @Override
            public void onError(int code, String message) {
                // 保持现行为：错误由页面 Toast；此处可扩展 error event
            }
        });
    }

    public void updateLike(long videoId, boolean isLiked, int likeCount) {
        FeedUiState current = uiState.getValue();
        if (current == null || current.videos.isEmpty()) {
            return;
        }
        List<VideoDto> copy = new ArrayList<>(current.videos);
        for (VideoDto v : copy) {
            if (v.id == videoId) {
                v.isLiked = isLiked;
                v.likeCount = likeCount;
                break;
            }
        }
        uiState.setValue(FeedUiState.success(copy));
    }

    public void updateCommentCount(long videoId, int commentCount) {
        FeedUiState current = uiState.getValue();
        if (current == null || current.videos.isEmpty()) {
            return;
        }
        List<VideoDto> copy = new ArrayList<>(current.videos);
        for (VideoDto v : copy) {
            if (v.id == videoId) {
                v.commentCount = commentCount;
                break;
            }
        }
        uiState.setValue(FeedUiState.success(copy));
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final VideoRepository repository;
        private final int pageSize;

        public Factory(VideoRepository repository, int pageSize) {
            this.repository = repository;
            this.pageSize = pageSize;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            FeedDataSource source = new FeedDataSource() {
                @Override
                public void getFeed(int page, int size, ApiCallback<FeedPage> callback) {
                    repository.getFeed(page, size, callback);
                }

                @Override
                public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
                    repository.toggleLike(videoId, callback);
                }
            };
            return (T) new FeedViewModel(source, pageSize);
        }
    }
}
