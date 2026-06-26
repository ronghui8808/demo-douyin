package com.example.douyin.repository;

import android.content.Context;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.DouyinApi;
import com.example.douyin.network.RetrofitClient;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.UserProfileDto;
import com.example.douyin.util.AppExecutors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepository {

    private static final int PROFILE_VIDEO_PAGE_SIZE = 30;

    private final DouyinApi api;

    public UserRepository(Context context) {
        api = RetrofitClient.getApi(context.getApplicationContext());
    }

    public int getProfileVideoPageSize() {
        return PROFILE_VIDEO_PAGE_SIZE;
    }

    public void getUserProfile(long userId, ApiCallback<UserProfileDto> callback) {
        api.getUserProfile(userId).enqueue(new SimpleCallback<>(callback));
    }

    public void getUserVideos(long userId, int page, ApiCallback<FeedPage> callback) {
        api.getUserVideos(userId, page, PROFILE_VIDEO_PAGE_SIZE)
                .enqueue(new SimpleCallback<>(callback));
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
