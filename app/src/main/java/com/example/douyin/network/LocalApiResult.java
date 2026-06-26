package com.example.douyin.network;

import com.example.douyin.network.model.ApiResponse;

public final class LocalApiResult {

    public final int httpCode;
    public final String json;

    public LocalApiResult(int httpCode, String json) {
        this.httpCode = httpCode;
        this.json = json;
    }

    public static LocalApiResult from(ApiResponse<?> response, com.google.gson.Gson gson) {
        int httpCode = response.code == 0 ? 200 : response.code;
        return new LocalApiResult(httpCode, gson.toJson(response));
    }
}
