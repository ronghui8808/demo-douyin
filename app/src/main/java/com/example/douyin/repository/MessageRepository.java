package com.example.douyin.repository;

import android.content.Context;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.DouyinApi;
import com.example.douyin.network.RetrofitClient;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.ConversationDto;
import com.example.douyin.network.model.MessageDto;
import com.example.douyin.network.model.SendMessageRequest;
import com.example.douyin.util.AppExecutors;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageRepository {

    private final DouyinApi api;
    private final Gson gson = new Gson();

    public MessageRepository(Context context) {
        api = RetrofitClient.getApi(context.getApplicationContext());
    }

    public void listConversations(ApiCallback<List<ConversationDto>> callback) {
        api.getConversations().enqueue(new MessageCallback<>(callback));
    }

    public void listMessages(long peerUserId, ApiCallback<List<MessageDto>> callback) {
        api.getMessages(peerUserId).enqueue(new MessageCallback<>(callback));
    }

    public void send(long toUserId, String content, ApiCallback<MessageDto> callback) {
        SendMessageRequest request = new SendMessageRequest();
        request.toUserId = toUserId;
        request.content = content;
        api.sendMessage(request).enqueue(new MessageCallback<>(callback));
    }

    private class MessageCallback<T> implements Callback<ApiResponse<T>> {

        private final ApiCallback<T> delegate;

        MessageCallback(ApiCallback<T> delegate) {
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
