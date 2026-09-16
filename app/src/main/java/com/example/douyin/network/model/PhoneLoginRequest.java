package com.example.douyin.network.model;

public class PhoneLoginRequest {

    public String phone;
    public String code;

    public PhoneLoginRequest() {
    }

    public PhoneLoginRequest(String phone, String code) {
        this.phone = phone;
        this.code = code;
    }
}
