package com.example.douyin.player;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MediaUrlHelperTest {

    @Test
    public void remoteHttpUrls_arePrefetchable() {
        assertTrue(MediaUrlHelper.shouldPrefetch("https://cdn.example.com/a.mp4"));
        assertTrue(MediaUrlHelper.shouldPrefetch("http://cdn.example.com/a.mp4"));
        assertTrue(MediaUrlHelper.shouldPrefetch("  https://cdn.example.com/a.mp4  "));
    }

    @Test
    public void localUrls_areNotPrefetchable() {
        assertFalse(MediaUrlHelper.shouldPrefetch("file:///data/local/tmp/a.mp4"));
        assertFalse(MediaUrlHelper.shouldPrefetch("/data/user/0/com.example/files/a.mp4"));
        assertFalse(MediaUrlHelper.shouldPrefetch(""));
        assertFalse(MediaUrlHelper.shouldPrefetch(null));
    }

    @Test
    public void normalize_trimsWhitespace() {
        assertEquals("https://cdn.example.com/a.mp4",
                MediaUrlHelper.normalize("  https://cdn.example.com/a.mp4  "));
        assertEquals("", MediaUrlHelper.normalize(null));
    }

    @Test
    public void prefetchBytes_isTwoMegabytes() {
        assertEquals(2L * 1024L * 1024L, MediaUrlHelper.PREFETCH_BYTES);
    }
}
