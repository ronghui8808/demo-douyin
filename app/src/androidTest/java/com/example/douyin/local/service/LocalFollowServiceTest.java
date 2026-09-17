package com.example.douyin.local.service;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.MatchPhonesResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LocalFollowServiceTest {

    private AppDatabase db;
    private LocalFollowService followService;
    private long userA;
    private long userB;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = AppDatabase.createInMemory(context);
        followService = new LocalFollowService(db.userDao(), db.followDao());
        userA = insertUser("13900000001", "A");
        userB = insertUser("13900000002", "B");
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void followUnfollowAndSelf() {
        assertEquals(400, followService.follow(userA, userA).code);

        assertEquals(0, followService.follow(userA, userB).code);
        assertTrue(followService.isFollowing(userA, userB));
        assertEquals(0, followService.follow(userA, userB).code); // idempotent
        assertEquals(1, followService.countFollowing(userA));

        assertEquals(0, followService.unfollow(userA, userB).code);
        assertFalse(followService.isFollowing(userA, userB));
    }

    @Test
    public void matchPhones_excludesSelf() {
        ApiResponse<MatchPhonesResult> response = followService.matchPhones(
                userA, Arrays.asList("13900000001", "13900000002"));
        assertEquals(0, response.code);
        assertEquals(1, response.data.matches.size());
        assertEquals(userB, response.data.matches.get(0).user.id);
        assertEquals("13900000002", response.data.matches.get(0).matchedPhone);
    }

    @Test
    public void matchPhones_empty() {
        ApiResponse<MatchPhonesResult> response =
                followService.matchPhones(userA, Collections.emptyList());
        assertEquals(0, response.code);
        assertTrue(response.data.matches.isEmpty());
    }

    private long insertUser(String phone, String nick) {
        UserEntity u = new UserEntity();
        u.username = phone;
        u.passwordHash = "";
        u.nickname = nick;
        u.phone = phone;
        u.createdAt = System.currentTimeMillis();
        return db.userDao().insert(u);
    }
}
