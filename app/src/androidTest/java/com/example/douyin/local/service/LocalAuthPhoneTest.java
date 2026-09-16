package com.example.douyin.local.service;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.local.db.UserDao;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.local.sms.MockSmsGateway;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.network.model.RegisterRequest;
import com.example.douyin.network.model.SmsSendResultDto;
import com.example.douyin.network.model.UserDto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LocalAuthPhoneTest {

    private static final String PHONE_A = "13800138000";
    private static final String PHONE_B = "13900139000";
    private static final String CODE = "123456";

    private AppDatabase database;
    private UserDao userDao;
    private LocalAuthService authService;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = AppDatabase.createInMemory(context);
        userDao = database.userDao();
        authService = new LocalAuthService(context, userDao, new MockSmsGateway());
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void registerByPhone_success() {
        ApiResponse<SmsSendResultDto> sms = authService.sendSms(PHONE_A, "register", null);
        assertEquals(0, sms.code);
        assertNotNull(sms.data);
        assertEquals(CODE, sms.data.debugCode);

        ApiResponse<LoginResult> reg = authService.registerByPhone(PHONE_A, CODE, "小明");
        assertEquals(0, reg.code);
        assertNotNull(reg.data);
        assertNotNull(reg.data.token);
        assertEquals(PHONE_A, reg.data.user.phone);
        assertEquals(PHONE_A, reg.data.user.username);
        assertEquals("小明", reg.data.user.nickname);
    }

    @Test
    public void registerByPhone_duplicate_fails() {
        assertEquals(0, authService.sendSms(PHONE_A, "register", null).code);
        assertEquals(0, authService.registerByPhone(PHONE_A, CODE, "用户A").code);

        ApiResponse<SmsSendResultDto> sms = authService.sendSms(PHONE_A, "register", null);
        assertEquals(400, sms.code);
        assertEquals("该手机号已注册，请直接登录", sms.message);
    }

    @Test
    public void loginByPhone_success() {
        assertEquals(0, authService.sendSms(PHONE_A, "register", null).code);
        assertEquals(0, authService.registerByPhone(PHONE_A, CODE, "用户A").code);

        ApiResponse<SmsSendResultDto> sms = authService.sendSms(PHONE_A, "login", null);
        assertEquals(0, sms.code);

        ApiResponse<LoginResult> login = authService.loginByPhone(PHONE_A, CODE);
        assertEquals(0, login.code);
        assertNotNull(login.data.token);
        assertEquals(PHONE_A, login.data.user.phone);
    }

    @Test
    public void loginByPhone_unregistered_fails() {
        assertEquals(0, authService.sendSms(PHONE_A, "login", null).code);

        ApiResponse<LoginResult> login = authService.loginByPhone(PHONE_A, CODE);
        assertEquals(400, login.code);
        assertEquals("该手机号未注册，请先注册", login.message);
    }

    @Test
    public void bindPhone_success() {
        long userId = insertUserWithoutPhone("legacy_user");

        ApiResponse<SmsSendResultDto> sms = authService.sendSms(PHONE_B, "bind", userId);
        assertEquals(0, sms.code);

        ApiResponse<UserDto> bind = authService.bindPhone(userId, PHONE_B, CODE);
        assertEquals(0, bind.code);
        assertEquals(PHONE_B, bind.data.phone);
    }

    @Test
    public void bindPhone_occupied_fails() {
        assertEquals(0, authService.sendSms(PHONE_A, "register", null).code);
        assertEquals(0, authService.registerByPhone(PHONE_A, CODE, "占号用户").code);

        long userId = insertUserWithoutPhone("other_user");
        assertEquals(0, authService.sendSms(PHONE_A, "bind", userId).code);

        ApiResponse<UserDto> bind = authService.bindPhone(userId, PHONE_A, CODE);
        assertEquals(400, bind.code);
        assertEquals("该手机号已绑定其他账号", bind.message);
    }

    @Test
    public void oldRegisterAndLogin_return410() {
        ApiResponse<LoginResult> register = authService.register(
                new RegisterRequest("old_user", "123456", "旧用户")
        );
        assertEquals(410, register.code);
        assertEquals("请使用手机号登录/注册", register.message);

        ApiResponse<LoginResult> login = authService.login("old_user", "123456");
        assertEquals(410, login.code);
        assertEquals("请使用手机号登录/注册", login.message);
    }

    private long insertUserWithoutPhone(String username) {
        UserEntity user = new UserEntity();
        user.username = username;
        user.passwordHash = "";
        user.nickname = username;
        user.phone = null;
        user.createdAt = System.currentTimeMillis();
        long id = userDao.insert(user);
        assertTrue(id > 0);
        return id;
    }
}
