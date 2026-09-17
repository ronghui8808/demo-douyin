package com.example.douyin.repository;

import android.content.Context;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.DouyinApi;
import com.example.douyin.network.RetrofitClient;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.MatchPhonesRequest;
import com.example.douyin.network.model.MatchPhonesResult;
import com.example.douyin.network.model.UserDto;
import com.example.douyin.util.AppExecutors;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FriendRepository {

    private final DouyinApi api;
    private final Gson gson = new Gson();

    public FriendRepository(Context context) {
        api = RetrofitClient.getApi(context.getApplicationContext());
    }

    public void matchPhones(List<String> phones, ApiCallback<MatchPhonesResult> callback) {
        MatchPhonesRequest request = new MatchPhonesRequest();
        request.phones = phones != null ? phones : new ArrayList<>();
        api.matchPhones(request).enqueue(new FriendCallback<>(callback));
    }

    public void follow(long userId, ApiCallback<Boolean> callback) {
        api.follow(userId).enqueue(new FriendCallback<>(callback));
    }

    public void unfollow(long userId, ApiCallback<Boolean> callback) {
        api.unfollow(userId).enqueue(new FriendCallback<>(callback));
    }

    public void getMyFollowing(ApiCallback<List<UserDto>> callback) {
        api.getMyFollowing().enqueue(new FriendCallback<>(callback));
    }

    private class FriendCallback<T> implements Callback<ApiResponse<T>> {

        private final ApiCallback<T> delegate;

        FriendCallback(ApiCallback<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onResponse(Call<ApiResponse<T>> call, Response<ApiResponse<T>> response) {
            if (!response.isSuccessful()) {
                ApiResponse<?> parsed = parseError(response);
                int code = response.code() > 0 ? response.code() : -1;
                String message = "请求失败";
                if (parsed != null) {
                    if (parsed.code != 0) {
                        code = parsed.code;
                    }
                    if (parsed.message != null && !parsed.message.isEmpty()) {
                        message = parsed.message;
                    }
                }
                notifyError(code, message);
                return;
            }
            ApiResponse<T> body = response.body();
            if (body == null) {
                notifyError(-1, "请求失败");
                return;
            }
            if (body.code != 0) {
                notifyError(body.code, body.message != null ? body.message : "操作失败");
                return;
            }
            if (body.data == null) {
                notifyError(-1, "响应数据为空");
                return;
            }
            AppExecutors.get().mainThread(() -> delegate.onSuccess(body.data));
        }

        @Override
        public void onFailure(Call<ApiResponse<T>> call, Throwable t) {
            notifyError(-1, t.getMessage() != null ? t.getMessage() : "网络错误");
        }

        private ApiResponse<?> parseError(Response<ApiResponse<T>> response) {
            try {
                if (response.body() != null) {
                    return response.body();
                }
                ResponseBody errorBody = response.errorBody();
                if (errorBody == null) {
                    return null;
                }
                String json = errorBody.string();
                if (json == null || json.isEmpty()) {
                    return null;
                }
                return gson.fromJson(json, ApiResponse.class);
            } catch (IOException | RuntimeException ignored) {
                return null;
            }
        }

        private void notifyError(int code, String message) {
            AppExecutors.get().mainThread(() -> delegate.onError(code, message));
        }
    }
}
