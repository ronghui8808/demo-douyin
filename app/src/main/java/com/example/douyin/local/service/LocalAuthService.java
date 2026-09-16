package com.example.douyin.local.service;

import android.content.Context;
import android.text.TextUtils;

import com.example.douyin.auth.PhoneValidator;
import com.example.douyin.local.EntityMapper;
import com.example.douyin.local.db.UserDao;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.local.sms.MockSmsGateway;
import com.example.douyin.local.sms.SmsGateway;
import com.example.douyin.local.sms.SmsSendResult;
import com.example.douyin.network.TokenStore;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.network.model.RegisterRequest;
import com.example.douyin.network.model.SmsSendResultDto;
import com.example.douyin.network.model.UserDto;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LocalAuthService {

    private static final String MSG_USE_PHONE = "请使用手机号登录/注册";
    private static final String MSG_INVALID_PHONE = "手机号格式不正确";
    private static final String MSG_INVALID_CODE = "验证码格式不正确";
    private static final String MSG_INVALID_SCENE = "无效的场景";
    private static final String MSG_CODE_BAD = "验证码错误或已过期";
    private static final String MSG_ALREADY_REGISTERED = "该手机号已注册，请直接登录";
    private static final String MSG_NOT_REGISTERED = "该手机号未注册，请先注册";
    private static final String MSG_ALREADY_BOUND = "已绑定手机号";
    private static final String MSG_PHONE_TAKEN = "该手机号已绑定其他账号";
    private static final String MSG_NOT_LOGGED_IN = "未登录";
    private static final String MSG_USER_MISSING = "用户不存在";

    private final UserDao userDao;
    private final TokenStore tokenStore;
    private final SmsGateway smsGateway;
    private final Map<String, Long> sessions = new HashMap<>();

    public LocalAuthService(Context context, UserDao userDao) {
        this(context, userDao, new MockSmsGateway());
    }

    public LocalAuthService(Context context, UserDao userDao, SmsGateway smsGateway) {
        this.userDao = userDao;
        this.tokenStore = TokenStore.get(context);
        this.smsGateway = smsGateway;
        restoreSessionFromStore();
    }

    public ApiResponse<LoginResult> register(RegisterRequest request) {
        return ApiResponse.error(410, MSG_USE_PHONE);
    }

    public ApiResponse<LoginResult> login(String username, String password) {
        return ApiResponse.error(410, MSG_USE_PHONE);
    }

    public ApiResponse<SmsSendResultDto> sendSms(String phone, String scene, Long userIdOrNull) {
        if (!PhoneValidator.isValidPhone(phone)) {
            return ApiResponse.error(400, MSG_INVALID_PHONE);
        }
        if (!isAllowedScene(scene)) {
            return ApiResponse.error(400, MSG_INVALID_SCENE);
        }

        if ("bind".equals(scene)) {
            if (userIdOrNull == null) {
                return ApiResponse.error(401, MSG_NOT_LOGGED_IN);
            }
            UserEntity user = userDao.findById(userIdOrNull);
            if (user == null) {
                return ApiResponse.error(401, MSG_USER_MISSING);
            }
            if (!TextUtils.isEmpty(user.phone)) {
                return ApiResponse.error(400, MSG_ALREADY_BOUND);
            }
        } else if ("register".equals(scene)) {
            if (userDao.findByPhone(phone) != null) {
                return ApiResponse.error(400, MSG_ALREADY_REGISTERED);
            }
        }

        SmsSendResult result = smsGateway.sendCode(phone, scene);
        if (!result.ok) {
            return ApiResponse.error(400, result.errorMessage);
        }

        SmsSendResultDto dto = new SmsSendResultDto();
        dto.requestId = result.requestId;
        dto.expireInSec = result.expireInSec;
        dto.debugCode = result.debugCode;
        return ApiResponse.ok(dto);
    }

    public ApiResponse<LoginResult> registerByPhone(String phone, String code, String nickname) {
        if (!PhoneValidator.isValidPhone(phone)) {
            return ApiResponse.error(400, MSG_INVALID_PHONE);
        }
        if (!PhoneValidator.isValidCode(code)) {
            return ApiResponse.error(400, MSG_INVALID_CODE);
        }
        if (!smsGateway.verifyCode(phone, "register", code)) {
            return ApiResponse.error(400, MSG_CODE_BAD);
        }
        if (userDao.findByPhone(phone) != null) {
            return ApiResponse.error(400, MSG_ALREADY_REGISTERED);
        }

        UserEntity user = new UserEntity();
        user.username = phone;
        user.passwordHash = "";
        user.phone = phone;
        user.nickname = resolveNickname(nickname, phone);
        user.createdAt = System.currentTimeMillis();
        user.id = userDao.insert(user);

        return ApiResponse.ok(createLoginResult(user));
    }

    public ApiResponse<LoginResult> loginByPhone(String phone, String code) {
        if (!PhoneValidator.isValidPhone(phone)) {
            return ApiResponse.error(400, MSG_INVALID_PHONE);
        }
        if (!PhoneValidator.isValidCode(code)) {
            return ApiResponse.error(400, MSG_INVALID_CODE);
        }
        if (!smsGateway.verifyCode(phone, "login", code)) {
            return ApiResponse.error(400, MSG_CODE_BAD);
        }

        UserEntity user = userDao.findByPhone(phone);
        if (user == null) {
            return ApiResponse.error(400, MSG_NOT_REGISTERED);
        }

        return ApiResponse.ok(createLoginResult(user));
    }

    public ApiResponse<UserDto> bindPhone(long userId, String phone, String code) {
        UserEntity user = userDao.findById(userId);
        if (user == null) {
            return ApiResponse.error(401, MSG_USER_MISSING);
        }
        if (!TextUtils.isEmpty(user.phone)) {
            return ApiResponse.error(400, MSG_ALREADY_BOUND);
        }
        if (!PhoneValidator.isValidPhone(phone)) {
            return ApiResponse.error(400, MSG_INVALID_PHONE);
        }
        if (!PhoneValidator.isValidCode(code)) {
            return ApiResponse.error(400, MSG_INVALID_CODE);
        }
        if (!smsGateway.verifyCode(phone, "bind", code)) {
            return ApiResponse.error(400, MSG_CODE_BAD);
        }

        UserEntity occupied = userDao.findByPhone(phone);
        if (occupied != null && occupied.id != userId) {
            return ApiResponse.error(400, MSG_PHONE_TAKEN);
        }

        userDao.updatePhone(userId, phone);
        return getMe(userId);
    }

    public ApiResponse<UserDto> getMe(long userId) {
        UserEntity user = userDao.findById(userId);
        if (user == null) {
            return ApiResponse.error(401, MSG_USER_MISSING);
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

    private static boolean isAllowedScene(String scene) {
        return "register".equals(scene) || "login".equals(scene) || "bind".equals(scene);
    }

    private static String resolveNickname(String nickname, String phone) {
        if (TextUtils.isEmpty(nickname) || TextUtils.isEmpty(nickname.trim())) {
            return "用户" + phone.substring(phone.length() - 4);
        }
        return nickname.trim();
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
