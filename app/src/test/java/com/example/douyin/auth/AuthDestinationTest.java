package com.example.douyin.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AuthDestinationTest {

    @Test
    public void resolvesAuthDestinationFromTokenAndPhoneState() {
        assertEquals(AuthDestination.LOGIN, AuthDestination.resolve(false, false));
        assertEquals(AuthDestination.LOGIN, AuthDestination.resolve(false, true));
        assertEquals(AuthDestination.BIND_PHONE, AuthDestination.resolve(true, false));
        assertEquals(AuthDestination.MAIN, AuthDestination.resolve(true, true));
    }
}
