package com.example.douyin;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.douyin.local.LocalServices;
import com.example.douyin.local.SeedDataInitializer;
import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.network.DouyinApi;
import com.example.douyin.network.RetrofitClient;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LoginResult;
import com.example.douyin.network.model.PhoneLoginRequest;
import com.example.douyin.network.model.SmsSendRequest;
import com.example.douyin.network.model.SmsSendResultDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AuthApiTest {

    private static final String DEMO_PHONE = "13800138000";
    private static final String DEMO_CODE = "123456";

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        LocalServices.resetForTests();
        RetrofitClient.resetForTests();
        AppDatabase.resetInstance();
        context.deleteDatabase("douyin.db");
        SeedDataInitializer.resetSeedFlag(context);
        AppDatabase.get(context);
        LocalServices.init(context);
        SeedDataInitializer.initBlocking(context);
    }

    @Test
    public void loginDemoUser_returnsToken() throws InterruptedException {
        DouyinApi api = RetrofitClient.getApi(context);
        CountDownLatch latch = new CountDownLatch(1);
        final ApiResponse<LoginResult>[] holder = new ApiResponse[1];
        final int[] httpCode = new int[1];

        api.sendSms(new SmsSendRequest(DEMO_PHONE, "login"))
                .enqueue(new Callback<ApiResponse<SmsSendResultDto>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<SmsSendResultDto>> call,
                                           Response<ApiResponse<SmsSendResultDto>> response) {
                        if (response.code() != 200 || response.body() == null
                                || response.body().code != 0) {
                            latch.countDown();
                            return;
                        }
                        api.loginByPhone(new PhoneLoginRequest(DEMO_PHONE, DEMO_CODE))
                                .enqueue(new Callback<ApiResponse<LoginResult>>() {
                                    @Override
                                    public void onResponse(Call<ApiResponse<LoginResult>> call,
                                                           Response<ApiResponse<LoginResult>> response) {
                                        httpCode[0] = response.code();
                                        holder[0] = response.body();
                                        latch.countDown();
                                    }

                                    @Override
                                    public void onFailure(Call<ApiResponse<LoginResult>> call,
                                                          Throwable t) {
                                        latch.countDown();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<SmsSendResultDto>> call, Throwable t) {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(200, httpCode[0]);
        assertNotNull(holder[0]);
        assertEquals(0, holder[0].code);
        assertNotNull(holder[0].data);
        assertNotNull(holder[0].data.token);
        assertNotNull(holder[0].data.user);
        // Seed: username "demo", phone 13800138000 (either may identify the demo user)
        assertTrue(
                "demo".equals(holder[0].data.user.username)
                        || DEMO_PHONE.equals(holder[0].data.user.phone)
                        || DEMO_PHONE.equals(holder[0].data.user.username));
    }

    @Test
    public void getFeed_returnsSeedVideos() throws InterruptedException {
        DouyinApi api = RetrofitClient.getApi(context);
        CountDownLatch latch = new CountDownLatch(1);
        final ApiResponse<FeedPage>[] holder = new ApiResponse[1];

        api.getFeed(0, 10).enqueue(new Callback<ApiResponse<FeedPage>>() {
            @Override
            public void onResponse(Call<ApiResponse<FeedPage>> call,
                                   Response<ApiResponse<FeedPage>> response) {
                holder[0] = response.body();
                latch.countDown();
            }

            @Override
            public void onFailure(Call<ApiResponse<FeedPage>> call, Throwable t) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNotNull(holder[0]);
        assertEquals(0, holder[0].code);
        assertNotNull(holder[0].data.list);
        assertTrue(holder[0].data.list.size() >= 3);
    }
}
