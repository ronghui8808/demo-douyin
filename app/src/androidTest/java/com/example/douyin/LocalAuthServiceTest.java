package com.example.douyin;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.local.service.LocalAuthService;
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

    private LocalAuthService authService;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        AppDatabase database = AppDatabase.createInMemory(context);
        authService = new LocalAuthService(context, database.userDao());
    }

    @Test
    public void registerAndLogin_success() {
        ApiResponse<LoginResult> registerResponse = authService.register(
                new RegisterRequest("test_user", "123456", "测试用户")
        );
        assertEquals(0, registerResponse.code);
        assertNotNull(registerResponse.data);
        assertNotNull(registerResponse.data.token);

        ApiResponse<LoginResult> loginResponse = authService.login("test_user", "123456");
        assertEquals(0, loginResponse.code);
        assertNotNull(loginResponse.data.token);
    }

    @Test
    public void register_duplicateUsername_returnsError() {
        authService.register(new RegisterRequest("dup_user", "123456", "用户1"));
        ApiResponse<LoginResult> response = authService.register(
                new RegisterRequest("dup_user", "654321", "用户2")
        );
        assertEquals(400, response.code);
    }

    @Test
    public void getMe_returnsUserDto() {
        ApiResponse<LoginResult> registerResponse = authService.register(
                new RegisterRequest("me_user", "123456", "Me")
        );
        long userId = registerResponse.data.user.id;

        ApiResponse<UserDto> meResponse = authService.getMe(userId);
        assertEquals(0, meResponse.code);
        assertEquals("me_user", meResponse.data.username);
        assertTrue(meResponse.data.id > 0);
    }
}
