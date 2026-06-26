package com.example.douyin.network.model;

public class RegisterRequest {

    public String username;
    public String password;
    public String nickname;

    public RegisterRequest(String username, String password, String nickname) {
        this.username = username;
        this.password = password;
        this.nickname = nickname;
    }
}
