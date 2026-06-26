package com.example.douyin.local.service;

import android.content.Context;
import android.text.TextUtils;

import com.example.douyin.local.db.UserDao;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.local.EntityMapper;
import com.example.douyin.network.TokenStore;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.network.model.RegisterRequest;
import com.example.douyin.network.model.UserDto;
import com.example.douyin.util.PasswordHasher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LocalAuthService {

    private final UserDao userDao;
    private final TokenStore tokenStore;
    private final Map<String, Long> sessions = new HashMap<>();

    public LocalAuthService(Context context, UserDao userDao) {
        this.userDao = userDao;
        this.tokenStore = TokenStore.get(context);
        restoreSessionFromStore();
    }

    public ApiResponse<LoginResult> register(RegisterRequest request) {
        if (request == null || TextUtils.isEmpty(request.username)
                || TextUtils.isEmpty(request.password)) {
            return ApiResponse.error(400, "用户名和密码不能为空");
        }
        if (request.password.length() < 6) {
            return ApiResponse.error(400, "密码至少 6 位");
        }
        if (userDao.findByUsername(request.username) != null) {
            return ApiResponse.error(400, "用户名已存在");
        }

        UserEntity user = new UserEntity();
        user.username = request.username.trim();
        user.passwordHash = PasswordHasher.hash(request.password);
        user.nickname = TextUtils.isEmpty(request.nickname)
                ? request.username.trim()
                : request.nickname.trim();
        user.createdAt = System.currentTimeMillis();
        user.id = userDao.insert(user);

        return ApiResponse.ok(createLoginResult(user));
    }

    public ApiResponse<LoginResult> login(String username, String password) {
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            return ApiResponse.error(400, "用户名和密码不能为空");
        }

        UserEntity user = userDao.findByUsername(username.trim());
        if (user == null || !PasswordHasher.verify(password, user.passwordHash)) {
            return ApiResponse.error(400, "用户名或密码错误");
        }

        return ApiResponse.ok(createLoginResult(user));
    }

    public ApiResponse<UserDto> getMe(long userId) {
        UserEntity user = userDao.findById(userId);
        if (user == null) {
            return ApiResponse.error(401, "用户不存在");
        }
        return ApiResponse.ok(EntityMapper.toUserDto(user));
    }

    public Long resolveUserId(String token) {
        if (TextUtils.isEmpty(token)) {
            return null;
        }

        Long userId = sessions.get(token);
        if (userId != null) {
            return userId;
        }

        String storedToken = tokenStore.getToken();
        if (token.equals(storedToken)) {
            long storedUserId = tokenStore.getUserId();
            if (storedUserId > 0) {
                sessions.put(token, storedUserId);
                return storedUserId;
            }
        }
        return null;
    }

    private LoginResult createLoginResult(UserEntity user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user.id);

        LoginResult result = new LoginResult();
        result.token = token;
        result.user = EntityMapper.toUserDto(user);
        return result;
    }

    private void restoreSessionFromStore() {
        String token = tokenStore.getToken();
        long userId = tokenStore.getUserId();
        if (!TextUtils.isEmpty(token) && userId > 0) {
            sessions.put(token, userId);
        }
    }
}
