package com.example.douyin.feed;

import com.example.douyin.network.model.VideoDto;

import java.util.Collections;
import java.util.List;

public final class FeedUiState {
    public final boolean loading;
    public final String errorMessage;
    public final List<VideoDto> videos;

    private FeedUiState(boolean loading, String errorMessage, List<VideoDto> videos) {
        this.loading = loading;
        this.errorMessage = errorMessage;
        this.videos = videos;
    }

    public static FeedUiState loading() {
        return new FeedUiState(true, null, Collections.emptyList());
    }

    public static FeedUiState success(List<VideoDto> videos) {
        return new FeedUiState(false, null, videos != null ? videos : Collections.emptyList());
    }

    public static FeedUiState error(String message) {
        return new FeedUiState(false, message, Collections.emptyList());
    }

    public boolean isEmpty() {
        return !loading && errorMessage == null && videos.isEmpty();
    }
}
