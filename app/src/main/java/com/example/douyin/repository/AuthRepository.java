package com.example.douyin.repository;

import android.content.Context;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.DouyinApi;
import com.example.douyin.network.RetrofitClient;
import com.example.douyin.network.TokenStore;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.LoginRequest;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.network.model.RegisterRequest;
import com.example.douyin.network.model.UserDto;
import com.example.douyin.trace.AuthTrace;
import com.example.douyin.util.AppExecutors;
import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {

    private static final String TRACE_LOGIN = "auth_login";
    private static final String TRACE_REGISTER = "auth_register";

    private final DouyinApi api;
    private final TokenStore tokenStore;

    public AuthRepository(Context context) {
        Context appContext = context.getApplicationContext();
        api = RetrofitClient.getApi(appContext);
        tokenStore = TokenStore.get(appContext);
    }

    public boolean isLoggedIn() {
        return tokenStore.isLoggedIn();
    }

    public long getUserId() {
        return tokenStore.getUserId();
    }

    public void login(String username, String password, ApiCallback<LoginResult> callback) {
        int cookie = AuthTrace.beginAsync(TRACE_LOGIN);
        api.login(new LoginRequest(username, password))
                .enqueue(new AuthCallback<>(callback, TRACE_LOGIN, cookie) {
                    @Override
                    protected void onSuccessData(LoginResult data) {
                        tokenStore.saveToken(data.token, data.user.id);
                    }

                    @Override
                    protected void onErrorData(int code, String message) {
                    }
                });
    }

    public void register(String username, String password, String nickname,
                         ApiCallback<LoginResult> callback) {
        int cookie = AuthTrace.beginAsync(TRACE_REGISTER);
        api.register(new RegisterRequest(username, password, nickname))
                .enqueue(new AuthCallback<>(callback, TRACE_REGISTER, cookie) {
                    @Override
                    protected void onSuccessData(LoginResult data) {
                        tokenStore.saveToken(data.token, data.user.id);
                    }

                    @Override
                    protected void onErrorData(int code, String message) {
                    }
                });
    }

    public void getMe(ApiCallback<UserDto> callback) {
        api.getMe().enqueue(new AuthCallback<>(callback, null, 0) {
            @Override
            protected void onSuccessData(UserDto data) {
            }

            @Override
            protected void onErrorData(int code, String message) {
            }
        });
    }

    public void logout() {
        AuthTrace.begin("auth_logout");
        try {
            clearSession();
        } finally {
            AuthTrace.end();
        }
    }

    private void clearSession() {
        tokenStore.clear();
    }

    private abstract class AuthCallback<T> implements Callback<ApiResponse<T>> {

        private final ApiCallback<T> delegate;
        private final String asyncSection;
        private int asyncCookie;

        AuthCallback(ApiCallback<T> delegate, String asyncSection, int asyncCookie) {
            this.delegate = delegate;
            this.asyncSection = asyncSection;
            this.asyncCookie = asyncCookie;
        }

        protected abstract void onSuccessData(T data);

        protected abstract void onErrorData(int code, String message);

        @Override
        public void onResponse(Call<ApiResponse<T>> call, Response<ApiResponse<T>> response) {
            if (!response.isSuccessful()) {
                ApiResponse<?> parsed = parseErrorApiResponse(response);
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
            if (response.body() == null) {
                notifyError(response.code() > 0 ? response.code() : -1, "请求失败");
                return;
            }
            deliverResponse(response.body());
        }

        @Override
        public void onFailure(Call<ApiResponse<T>> call, Throwable t) {
            notifyError(-1, t.getMessage() != null ? t.getMessage() : "网络错误");
        }

        @SuppressWarnings("unchecked")
        private ApiResponse<?> parseErrorApiResponse(Response<ApiResponse<T>> response) {
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
                return new Gson().fromJson(json, ApiResponse.class);
            } catch (IOException | RuntimeException ignored) {
                return null;
            }
        }

        private void deliverResponse(ApiResponse<T> body) {
            if (body.code == 401) {
                clearSession();
                notifyError(401, body.message != null ? body.message : "未登录");
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
            onSuccessData(body.data);
            notifySuccess(body.data);
        }

        private void notifySuccess(T data) {
            finishAsyncTrace();
            AppExecutors.get().mainThread(() -> delegate.onSuccess(data));
        }

        private void notifyError(int code, String message) {
            onErrorData(code, message);
            finishAsyncTrace();
            AppExecutors.get().mainThread(() -> delegate.onError(code, message));
        }

        private void finishAsyncTrace() {
            if (asyncCookie != 0 && asyncSection != null) {
                AuthTrace.endAsync(asyncSection, asyncCookie);
                asyncCookie = 0;
            }
        }
    }
}
