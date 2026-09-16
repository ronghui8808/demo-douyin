package com.example.douyin.network.model;

public class PhoneRegisterRequest {

    public String phone;
    public String code;
    public String nickname;

    public PhoneRegisterRequest() {
    }

    public PhoneRegisterRequest(String phone, String code, String nickname) {
        this.phone = phone;
        this.code = code;
        this.nickname = nickname;
    }
}
