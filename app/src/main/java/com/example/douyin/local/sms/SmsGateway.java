package com.example.douyin.local.sms;

public interface SmsGateway {
    SmsSendResult sendCode(String phone, String scene);

    boolean verifyCode(String phone, String scene, String code);
}
