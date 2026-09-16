package com.example.douyin.network.model;

public class SmsSendRequest {

    public String phone;
    public String scene;

    public SmsSendRequest() {
    }

    public SmsSendRequest(String phone, String scene) {
        this.phone = phone;
        this.scene = scene;
    }
}
