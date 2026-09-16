package com.example.douyin.network.model;

public class BindPhoneRequest {

    public String phone;
    public String code;

    public BindPhoneRequest() {
    }

    public BindPhoneRequest(String phone, String code) {
        this.phone = phone;
        this.code = code;
    }
}
