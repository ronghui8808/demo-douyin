package com.example.douyin.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PhoneMaskerTest {

    @Test
    public void masksPhoneNumbers() {
        assertEquals("138****8000", PhoneMasker.mask("13800138000"));
        assertEquals("", PhoneMasker.mask(null));
    }
}
