package com.example.douyin.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * 密码哈希：SHA-256 + 随机盐。
 * <p>
 * 存储格式：{@code saltHex:hashHex}，其中 hash = SHA-256(saltBytes || passwordUtf8)。
 * 仍兼容历史裸 SHA-256（64 位十六进制、无冒号），便于已 seed 的本地库继续登录。
 */
public final class PasswordHasher {

    private static final int SALT_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return bytesToHex(salt) + ":" + hashWithSalt(password, salt);
    }

    public static boolean verify(String password, String passwordHash) {
        if (passwordHash == null || passwordHash.isEmpty()) {
            return false;
        }
        int separator = passwordHash.indexOf(':');
        if (separator <= 0 || separator == passwordHash.length() - 1) {
            // 历史格式：裸 SHA-256(password)
            return sha256Hex(password.getBytes(StandardCharsets.UTF_8)).equals(passwordHash);
        }
        String saltHex = passwordHash.substring(0, separator);
        String expectedHash = passwordHash.substring(separator + 1);
        byte[] salt;
        try {
            salt = hexToBytes(saltHex);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return hashWithSalt(password, salt).equals(expectedHash);
    }

    private static String hashWithSalt(String password, byte[] salt) {
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[salt.length + passwordBytes.length];
        System.arraycopy(salt, 0, combined, 0, salt.length);
        System.arraycopy(passwordBytes, 0, combined, salt.length, passwordBytes.length);
        return sha256Hex(combined);
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("invalid hex");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int index = i * 2;
            bytes[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }
        return bytes;
    }
}
