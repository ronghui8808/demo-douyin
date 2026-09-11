package com.example.douyin.feed;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.VideoDto;

import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class FeedViewModelTest {

    @Rule
    public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    @Test
    public void loadFeed_success_emitsVideos() {
        FeedDataSource source = new FeedDataSource() {
            @Override
            public void getFeed(int page, int size, ApiCallback<FeedPage> callback) {
                FeedPage pageData = new FeedPage();
                VideoDto v = new VideoDto();
                v.id = 1L;
                pageData.list = Collections.singletonList(v);
                callback.onSuccess(pageData);
            }

            @Override
            public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
            }
        };
        FeedViewModel vm = new FeedViewModel(source, 20);
        vm.loadFeed();
        FeedUiState state = vm.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.loading);
        assertEquals(1, state.videos.size());
    }

    @Test
    public void loadFeed_error_emitsMessage() {
        FeedDataSource source = new FeedDataSource() {
            @Override
            public void getFeed(int page, int size, ApiCallback<FeedPage> callback) {
                callback.onError(500, "boom");
            }

            @Override
            public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
            }
        };
        FeedViewModel vm = new FeedViewModel(source, 20);
        vm.loadFeed();
        assertEquals("boom", vm.getUiState().getValue().errorMessage);
    }
}
