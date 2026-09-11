package com.example.douyin.feed;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LikeResult;

public interface FeedDataSource {
    void getFeed(int page, int size, ApiCallback<FeedPage> callback);

    void toggleLike(long videoId, ApiCallback<LikeResult> callback);
}
