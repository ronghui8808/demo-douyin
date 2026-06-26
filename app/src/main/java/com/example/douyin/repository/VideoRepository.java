package com.example.douyin.repository;

import android.content.Context;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.DouyinApi;
import com.example.douyin.network.RetrofitClient;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.util.AppExecutors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideoRepository {

    private final DouyinApi api;

    public VideoRepository(Context context) {
        api = RetrofitClient.getApi(context.getApplicationContext());
    }

    public void getFeed(int page, int size, ApiCallback<FeedPage> callback) {
        api.getFeed(page, size).enqueue(new SimpleCallback<>(callback));
    }

    public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
        api.toggleLike(videoId).enqueue(new SimpleCallback<>(callback));
    }

    private static class SimpleCallback<T> implements Callback<ApiResponse<T>> {

        private final ApiCallback<T> delegate;

        SimpleCallback(ApiCallback<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onResponse(Call<ApiResponse<T>> call, Response<ApiResponse<T>> response) {
            if (!response.isSuccessful() || response.body() == null) {
                notifyError(response.code() > 0 ? response.code() : -1, "请求失败");
                return;
            }
            ApiResponse<T> body = response.body();
            if (body.code != 0) {
                notifyError(body.code, body.message != null ? body.message : "操作失败");
                return;
            }
            if (body.data == null) {
                notifyError(-1, "响应数据为空");
                return;
            }
            notifySuccess(body.data);
        }

        @Override
        public void onFailure(Call<ApiResponse<T>> call, Throwable t) {
            notifyError(-1, t.getMessage() != null ? t.getMessage() : "网络错误");
        }

        private void notifySuccess(T data) {
            AppExecutors.get().mainThread(() -> delegate.onSuccess(data));
        }

        private void notifyError(int code, String message) {
            AppExecutors.get().mainThread(() -> delegate.onError(code, message));
        }
    }
}
