package com.example.douyin.network;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    private static volatile DouyinApi api;

    private RetrofitClient() {
    }

    public static DouyinApi getApi(Context context) {
        if (api == null) {
            synchronized (RetrofitClient.class) {
                if (api == null) {
                    api = buildApi(context.getApplicationContext());
                }
            }
        }
        return api;
    }

    public static void resetForTests() {
        api = null;
    }

    private static DouyinApi buildApi(Context appContext) {
        Gson gson = new GsonBuilder().create();
        TokenStore tokenStore = TokenStore.get(appContext);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthHeaderInterceptor(tokenStore))
                .addInterceptor(new LocalApiInterceptor(appContext, gson))
                .build();

        return new Retrofit.Builder()
                .baseUrl("https://app.local/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(DouyinApi.class);
    }
}
