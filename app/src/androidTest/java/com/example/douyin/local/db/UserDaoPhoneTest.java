package com.example.douyin.local.db;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.douyin.local.db.entity.UserEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class UserDaoPhoneTest {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = AppDatabase.createInMemory(context);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void insertFindByPhoneAndUpdatePhone() {
        UserEntity u = new UserEntity();
        u.username = "13800138000";
        u.passwordHash = "";
        u.nickname = "测";
        u.phone = "13800138000";
        u.createdAt = System.currentTimeMillis();
        long id = db.userDao().insert(u);
        assertEquals(id, db.userDao().findByPhone("13800138000").id);
        db.userDao().updatePhone(id, "13900139000");
        assertEquals("13900139000", db.userDao().findById(id).phone);
    }
}
