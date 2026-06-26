package com.example.douyin.network;

import android.text.TextUtils;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthHeaderInterceptor implements Interceptor {

    private final TokenStore tokenStore;

    public AuthHeaderInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!"app.local".equals(request.url().host())) {
            return chain.proceed(request);
        }

        String token = tokenStore.getToken();
        if (!TextUtils.isEmpty(token) && request.header("Authorization") == null) {
            request = request.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
        }
        return chain.proceed(request);
    }
}
