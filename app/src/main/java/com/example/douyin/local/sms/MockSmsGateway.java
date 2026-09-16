package com.example.douyin.local.sms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class MockSmsGateway implements SmsGateway {
    private static final String DEBUG_CODE = "123456";

    private final long cooldownMs;
    private final long expireMs;
    private final LongSupplier nowMs;
    private final Map<String, Entry> entries = new HashMap<>();

    public MockSmsGateway() {
        this(60_000L, 300_000L, System::currentTimeMillis);
    }

    public MockSmsGateway(long cooldownMs, long expireMs, LongSupplier nowMs) {
        this.cooldownMs = cooldownMs;
        this.expireMs = expireMs;
        this.nowMs = nowMs;
    }

    @Override
    public SmsSendResult sendCode(String phone, String scene) {
        String key = key(scene, phone);
        long now = nowMs.getAsLong();
        Entry existing = entries.get(key);
        if (existing != null && now - existing.sentAt < cooldownMs) {
            return SmsSendResult.fail("请稍后再试");
        }

        int expireInSec = (int) (expireMs / 1000L);
        Entry entry = new Entry(DEBUG_CODE, now, now + expireMs);
        entries.put(key, entry);
        return SmsSendResult.ok(UUID.randomUUID().toString(), expireInSec, DEBUG_CODE);
    }

    @Override
    public boolean verifyCode(String phone, String scene, String code) {
        String key = key(scene, phone);
        Entry entry = entries.get(key);
        if (entry == null) {
            return false;
        }
        long now = nowMs.getAsLong();
        if (now > entry.expireAt || !entry.code.equals(code)) {
            return false;
        }
        entries.remove(key);
        return true;
    }

    private static String key(String scene, String phone) {
        return scene + "|" + phone;
    }

    private static final class Entry {
        final String code;
        final long sentAt;
        final long expireAt;

        Entry(String code, long sentAt, long expireAt) {
            this.code = code;
            this.sentAt = sentAt;
            this.expireAt = expireAt;
        }
    }
}
