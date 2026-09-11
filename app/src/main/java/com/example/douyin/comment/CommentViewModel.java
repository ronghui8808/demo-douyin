package com.example.douyin.comment;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.CommentDto;
import com.example.douyin.network.model.CommentPage;
import com.example.douyin.repository.CommentRepository;
import com.example.douyin.util.SingleLiveEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommentViewModel extends ViewModel {

    private static final int PAGE_SIZE = 20;

    private final long videoId;
    private final CommentRepository commentRepository;
    private final MutableLiveData<CommentUiState> uiState;
    private final SingleLiveEvent<String> loginRequired = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> toastMessage = new SingleLiveEvent<>();
    private final SingleLiveEvent<Integer> commentPosted = new SingleLiveEvent<>();

    private int currentPage;
    private boolean initialLoaded;

    public CommentViewModel(long videoId, int initialCommentCount, CommentRepository commentRepository) {
        this.videoId = videoId;
        this.commentRepository = commentRepository;
        this.uiState = new MutableLiveData<>(CommentUiState.initial(initialCommentCount));
    }

    public long getVideoId() {
        return videoId;
    }

    public LiveData<CommentUiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getLoginRequired() {
        return loginRequired;
    }

    public LiveData<String> getToastMessage() {
        return toastMessage;
    }

    public LiveData<Integer> getCommentPosted() {
        return commentPosted;
    }

    public void loadInitial() {
        CommentUiState current = requireState();
        if (current.loading || current.loadingMore) {
            return;
        }
        if (initialLoaded && (!current.comments.isEmpty() || current.error != null)) {
            return;
        }
        uiState.setValue(new CommentUiState(
                current.comments,
                true,
                false,
                current.posting,
                current.hasMore,
                null,
                current.commentCount
        ));
        commentRepository.getComments(videoId, 0, PAGE_SIZE, new ApiCallback<CommentPage>() {
            @Override
            public void onSuccess(CommentPage data) {
                List<CommentDto> list = data != null && data.list != null
                        ? new ArrayList<>(data.list)
                        : new ArrayList<>();
                currentPage = data != null ? data.page : 0;
                boolean hasMore = data != null && data.hasMore;
                initialLoaded = true;
                CommentUiState latest = requireState();
                uiState.setValue(new CommentUiState(
                        list,
                        false,
                        false,
                        latest.posting,
                        hasMore,
                        null,
                        latest.commentCount
                ));
            }

            @Override
            public void onError(int code, String message) {
                initialLoaded = true;
                CommentUiState latest = requireState();
                uiState.setValue(new CommentUiState(
                        Collections.emptyList(),
                        false,
                        false,
                        latest.posting,
                        false,
                        message != null ? message : "加载失败",
                        latest.commentCount
                ));
            }
        });
    }

    public void loadMore() {
        CommentUiState current = requireState();
        if (current.loading || current.loadingMore || !current.hasMore) {
            return;
        }
        uiState.setValue(new CommentUiState(
                current.comments,
                false,
                true,
                current.posting,
                current.hasMore,
                current.error,
                current.commentCount
        ));
        final int nextPage = currentPage + 1;
        commentRepository.getComments(videoId, nextPage, PAGE_SIZE, new ApiCallback<CommentPage>() {
            @Override
            public void onSuccess(CommentPage data) {
                CommentUiState latest = requireState();
                List<CommentDto> merged = new ArrayList<>(latest.comments);
                if (data != null && data.list != null) {
                    merged.addAll(data.list);
                }
                currentPage = data != null ? data.page : nextPage;
                boolean hasMore = data != null && data.hasMore;
                uiState.setValue(new CommentUiState(
                        merged,
                        false,
                        false,
                        latest.posting,
                        hasMore,
                        null,
                        latest.commentCount
                ));
            }

            @Override
            public void onError(int code, String message) {
                CommentUiState latest = requireState();
                uiState.setValue(new CommentUiState(
                        latest.comments,
                        false,
                        false,
                        latest.posting,
                        latest.hasMore,
                        latest.error,
                        latest.commentCount
                ));
                toastMessage.setValue(message);
            }
        });
    }

    public void send(String content, boolean loggedIn) {
        CommentUiState current = requireState();
        if (current.posting) {
            return;
        }
        if (!loggedIn) {
            loginRequired.setValue("need_login");
            return;
        }
        String trimmed = content != null ? content.trim() : "";
        if (trimmed.isEmpty()) {
            toastMessage.setValue("empty_content");
            return;
        }

        uiState.setValue(new CommentUiState(
                current.comments,
                current.loading,
                current.loadingMore,
                true,
                current.hasMore,
                current.error,
                current.commentCount
        ));
        commentRepository.postComment(videoId, trimmed, new ApiCallback<CommentDto>() {
            @Override
            public void onSuccess(CommentDto data) {
                CommentUiState latest = requireState();
                List<CommentDto> next = new ArrayList<>(latest.comments);
                if (data != null) {
                    next.add(0, data);
                }
                int newCount = latest.commentCount + 1;
                uiState.setValue(new CommentUiState(
                        next,
                        false,
                        false,
                        false,
                        latest.hasMore,
                        null,
                        newCount
                ));
                commentPosted.setValue(newCount);
            }

            @Override
            public void onError(int code, String message) {
                CommentUiState latest = requireState();
                uiState.setValue(new CommentUiState(
                        latest.comments,
                        latest.loading,
                        latest.loadingMore,
                        false,
                        latest.hasMore,
                        latest.error,
                        latest.commentCount
                ));
                if (code == 401) {
                    loginRequired.setValue("need_login");
                    return;
                }
                toastMessage.setValue(message);
            }
        });
    }

    private CommentUiState requireState() {
        CommentUiState state = uiState.getValue();
        return state != null ? state : CommentUiState.initial(0);
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final long videoId;
        private final int initialCommentCount;
        private final CommentRepository commentRepository;

        public Factory(long videoId, CommentRepository commentRepository) {
            this(videoId, 0, commentRepository);
        }

        public Factory(long videoId, int initialCommentCount, CommentRepository commentRepository) {
            this.videoId = videoId;
            this.initialCommentCount = initialCommentCount;
            this.commentRepository = commentRepository;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new CommentViewModel(videoId, initialCommentCount, commentRepository);
        }
    }
}
