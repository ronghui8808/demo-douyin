package com.example.douyin.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TokenStoreMemoryCacheTest {
    @Test
    public void set_thenIsLoggedIn() {
        TokenMemoryCache cache = new TokenMemoryCache();
        cache.set("abc", 42L);
        assertTrue(cache.isLoggedIn());
        assertEquals("abc", cache.getToken());
        assertEquals(42L, cache.getUserId());
    }

    @Test
    public void clear_resetsState() {
        TokenMemoryCache cache = new TokenMemoryCache();
        cache.set("abc", 1L);
        cache.clear();
        assertFalse(cache.isLoggedIn());
        assertNull(cache.getToken());
        assertEquals(-1L, cache.getUserId());
    }

    @Test
    public void shouldApplyHydratedToken_neverWipeNonEmptyCacheWithEmptyDs() {
        assertFalse(TokenStore.shouldApplyHydratedToken("legacy-token", null));
        assertFalse(TokenStore.shouldApplyHydratedToken("legacy-token", ""));
        assertFalse(TokenStore.shouldApplyHydratedToken("saved-token", null));
    }

    @Test
    public void shouldApplyHydratedToken_appliesWhenDsHasTokenOrCacheEmpty() {
        assertTrue(TokenStore.shouldApplyHydratedToken(null, "from-ds"));
        assertTrue(TokenStore.shouldApplyHydratedToken("", "from-ds"));
        assertTrue(TokenStore.shouldApplyHydratedToken(null, null));
        assertTrue(TokenStore.shouldApplyHydratedToken("", ""));
        assertTrue(TokenStore.shouldApplyHydratedToken("old", "from-ds"));
    }
}
