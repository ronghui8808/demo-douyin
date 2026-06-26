package com.example.douyin;

import com.example.douyin.util.PasswordHasher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PasswordHasherTest {

    @Test
    public void verify_returnsTrueForCorrectPassword() {
        String hash = PasswordHasher.hash("123456");
        assertTrue(PasswordHasher.verify("123456", hash));
    }

    @Test
    public void verify_returnsFalseForWrongPassword() {
        String hash = PasswordHasher.hash("123456");
        assertFalse(PasswordHasher.verify("654321", hash));
    }
}
