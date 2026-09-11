package com.example.douyin.network;

import android.content.Context;

import com.example.douyin.local.LocalServices;
import com.google.gson.Gson;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 拦截 {@code https://app.local/}，在进程内路由到 Local*Service，并构造 HTTP 语义响应。
 * <p>
 * 边界：能验证 path/method/状态码/JSON 契约与 Repository 调用链，但没有真实网络的超时、重试、弱网抖动；
 * 切真后端后这些仍需单独测。
 */
public class LocalApiInterceptor implements Interceptor {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Context appContext;
    private final Gson gson;

    public LocalApiInterceptor(Context context, Gson gson) {
        this.appContext = context.getApplicationContext();
        this.gson = gson;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        okhttp3.Request request = chain.request();
        if (!"app.local".equals(request.url().host())) {
            return chain.proceed(request);
        }

        LocalApiResult result = LocalApiDispatcher.dispatch(
                appContext,
                request,
                LocalServices.auth(),
                LocalServices.video(),
                LocalServices.comment(),
                gson
        );

        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(result.httpCode)
                .message(result.httpCode == 200 ? "OK" : "Error")
                .body(ResponseBody.create(result.json, JSON))
                .build();
    }
}
