package com.example.douyin.local.sms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class MockSmsGatewayTest {

    @Test
    public void sendCode_cooldown_verify_scenes_and_expiry() {
        AtomicLong now = new AtomicLong(1_000_000L);
        MockSmsGateway gw = new MockSmsGateway(60_000L, 300_000L, now::get);

        SmsSendResult r1 = gw.sendCode("13800138000", "login");
        assertTrue(r1.ok);
        assertEquals("123456", r1.debugCode);

        SmsSendResult r2 = gw.sendCode("13800138000", "login");
        assertFalse(r2.ok);

        assertTrue(gw.verifyCode("13800138000", "login", "123456"));
        assertFalse(gw.verifyCode("13800138000", "login", "000000"));
        assertFalse(gw.verifyCode("13800138000", "register", "123456"));

        gw.sendCode("13900139000", "register");
        assertFalse(gw.verifyCode("13900139000", "login", "123456"));
        assertTrue(gw.verifyCode("13900139000", "register", "123456"));

        now.addAndGet(301_000L);
        assertFalse(gw.verifyCode("13800138000", "login", "123456"));
    }
}
