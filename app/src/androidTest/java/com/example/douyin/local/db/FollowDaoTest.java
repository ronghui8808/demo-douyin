package com.example.douyin.local.db;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.douyin.local.db.entity.FollowEntity;
import com.example.douyin.local.db.entity.UserEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FollowDaoTest {

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
    public void insertFindDeleteAndCount() {
        FollowEntity e = new FollowEntity();
        e.followerId = 1;
        e.followeeId = 2;
        e.createdAt = System.currentTimeMillis();
        long id = db.followDao().insert(e);
        assertTrue(id > 0);
        assertNotNull(db.followDao().find(1, 2));
        assertEquals(1, db.followDao().countByFollower(1));

        FollowEntity dup = new FollowEntity();
        dup.followerId = 1;
        dup.followeeId = 2;
        dup.createdAt = System.currentTimeMillis();
        long ignored = db.followDao().insert(dup);
        assertTrue(ignored == -1 || ignored == id);
        assertEquals(1, db.followDao().countByFollower(1));

        assertEquals(1, db.followDao().delete(1, 2));
        assertNull(db.followDao().find(1, 2));
        assertEquals(0, db.followDao().countByFollower(1));
    }

    @Test
    public void findByPhones_returnsMatches() {
        UserEntity a = newUser("13900000001", "A");
        UserEntity b = newUser("13900000002", "B");
        db.userDao().insert(a);
        db.userDao().insert(b);

        List<UserEntity> found = db.userDao().findByPhones(
                Arrays.asList("13900000001", "13900000099"));
        assertEquals(1, found.size());
        assertEquals("13900000001", found.get(0).phone);
    }

    private static UserEntity newUser(String phone, String nick) {
        UserEntity u = new UserEntity();
        u.username = phone;
        u.passwordHash = "";
        u.nickname = nick;
        u.phone = phone;
        u.createdAt = System.currentTimeMillis();
        return u;
    }
}
