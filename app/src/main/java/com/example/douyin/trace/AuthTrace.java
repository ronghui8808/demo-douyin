package com.example.douyin.trace;

import android.os.Trace;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 登录链路 Perfetto / Systrace 埋点。
 * 同步：beginSection / endSection；异步 API：beginAsyncSection / endAsyncSection。
 */
public final class AuthTrace {

    private static final AtomicInteger ASYNC_COOKIE = new AtomicInteger();

    private AuthTrace() {
    }

    /** 同步 trace，同一线程内 begin / end 成对调用。 */
    public static void begin(String sectionName) {
        if (Trace.isEnabled()) {
            Trace.beginSection(sectionName);
        }
    }

    public static void end() {
        if (Trace.isEnabled()) {
            Trace.endSection();
        }
    }

    /** 异步 trace 开始，返回 cookie，在回调线程 endAsync。 */
    public static int beginAsync(String sectionName) {
        int cookie = ASYNC_COOKIE.incrementAndGet();
        if (Trace.isEnabled()) {
            Trace.beginAsyncSection(sectionName, cookie);
        }
        return cookie;
    }

    public static void endAsync(String sectionName, int cookie) {
        if (cookie != 0 && Trace.isEnabled()) {
            Trace.endAsyncSection(sectionName, cookie);
        }
    }
}
