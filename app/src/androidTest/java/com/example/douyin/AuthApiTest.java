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
import com.example.douyin.network.model.LoginRequest;
import com.example.douyin.network.model.LoginResult;

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

        api.login(new LoginRequest("demo", "123456")).enqueue(new Callback<ApiResponse<LoginResult>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResult>> call,
                                   Response<ApiResponse<LoginResult>> response) {
                httpCode[0] = response.code();
                holder[0] = response.body();
                latch.countDown();
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginResult>> call, Throwable t) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(200, httpCode[0]);
        assertNotNull(holder[0]);
        assertEquals(0, holder[0].code);
        assertNotNull(holder[0].data);
        assertNotNull(holder[0].data.token);
        assertEquals("demo", holder[0].data.user.username);
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
