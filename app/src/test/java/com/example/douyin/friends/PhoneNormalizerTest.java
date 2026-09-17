package com.example.douyin.friends;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PhoneNormalizerTest {

    @Test
    public void normalize_stripsFormattingAndCountryCode() {
        assertEquals("13800138000", PhoneNormalizer.normalize("138-0013-8000"));
        assertEquals("13800138000", PhoneNormalizer.normalize("+86 13800138000"));
        assertEquals("13800138000", PhoneNormalizer.normalize("008613800138000"));
        assertEquals("13800138000", PhoneNormalizer.normalize("8613800138000"));
    }

    @Test
    public void normalize_rejectsInvalid() {
        assertNull(PhoneNormalizer.normalize("12345"));
        assertNull(PhoneNormalizer.normalize(null));
        assertNull(PhoneNormalizer.normalize("12345678901"));
    }
}
