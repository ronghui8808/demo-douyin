package com.example.douyin.network;

public interface ApiCallback<T> {

    void onSuccess(T data);

    void onError(int code, String message);
}
