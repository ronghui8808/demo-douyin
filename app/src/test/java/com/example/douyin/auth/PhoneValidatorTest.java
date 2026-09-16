package com.example.douyin.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhoneValidatorTest {

    @Test
    public void validatesPhoneNumbers() {
        assertTrue(PhoneValidator.isValidPhone("13800138000"));
        assertFalse(PhoneValidator.isValidPhone("12345678901"));
        assertFalse(PhoneValidator.isValidPhone("1380013800"));
    }

    @Test
    public void validatesVerificationCodes() {
        assertTrue(PhoneValidator.isValidCode("123456"));
        assertFalse(PhoneValidator.isValidCode("12345"));
    }
}
