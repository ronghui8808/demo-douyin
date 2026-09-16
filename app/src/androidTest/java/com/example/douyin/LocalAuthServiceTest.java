package com.example.douyin;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.local.service.LocalAuthService;
import com.example.douyin.local.sms.MockSmsGateway;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.network.model.RegisterRequest;
import com.example.douyin.network.model.UserDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LocalAuthServiceTest {

    private static final String PHONE = "13800138001";
    private static final String CODE = "123456";

    private LocalAuthService authService;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase database = AppDatabase.createInMemory(context);
        authService = new LocalAuthService(context, database.userDao(), new MockSmsGateway());
    }

    @Test
    public void registerAndLogin_passwordApis_return410() {
        ApiResponse<LoginResult> registerResponse = authService.register(
                new RegisterRequest("test_user", "123456", "测试用户")
        );
        assertEquals(410, registerResponse.code);
        assertEquals("请使用手机号登录/注册", registerResponse.message);

        ApiResponse<LoginResult> loginResponse = authService.login("test_user", "123456");
        assertEquals(410, loginResponse.code);
        assertEquals("请使用手机号登录/注册", loginResponse.message);
    }

    @Test
    public void register_duplicateUsername_passwordApi_returns410() {
        ApiResponse<LoginResult> response = authService.register(
                new RegisterRequest("dup_user", "654321", "用户2")
        );
        assertEquals(410, response.code);
    }

    @Test
    public void getMe_returnsUserDto() {
        assertEquals(0, authService.sendSms(PHONE, "register", null).code);
        ApiResponse<LoginResult> registerResponse =
                authService.registerByPhone(PHONE, CODE, "Me");
        assertEquals(0, registerResponse.code);
        long userId = registerResponse.data.user.id;

        ApiResponse<UserDto> meResponse = authService.getMe(userId);
        assertEquals(0, meResponse.code);
        assertEquals(PHONE, meResponse.data.username);
        assertEquals(PHONE, meResponse.data.phone);
        assertTrue(meResponse.data.id > 0);
        assertNotNull(meResponse.data.nickname);
    }
}
