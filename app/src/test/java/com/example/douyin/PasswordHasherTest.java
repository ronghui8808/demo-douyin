package com.example.douyin;

import com.example.douyin.util.PasswordHasher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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

    @Test
    public void hash_usesRandomSalt_samePasswordDifferentDigest() {
        String left = PasswordHasher.hash("123456");
        String right = PasswordHasher.hash("123456");
        assertNotEquals(left, right);
        assertTrue(PasswordHasher.verify("123456", left));
        assertTrue(PasswordHasher.verify("123456", right));
    }

    @Test
    public void verify_acceptsLegacyUnsaltedSha256() {
        // SHA-256("123456") 历史格式，无盐、无冒号
        String legacy = "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92";
        assertTrue(PasswordHasher.verify("123456", legacy));
        assertFalse(PasswordHasher.verify("654321", legacy));
    }
}
