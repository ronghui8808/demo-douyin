package com.example.douyin.local.sms;

public final class SmsSendResult {
    public final boolean ok;
    public final String errorMessage;
    public final String requestId;
    public final int expireInSec;
    public final String debugCode;

    private SmsSendResult(
            boolean ok,
            String errorMessage,
            String requestId,
            int expireInSec,
            String debugCode) {
        this.ok = ok;
        this.errorMessage = errorMessage;
        this.requestId = requestId;
        this.expireInSec = expireInSec;
        this.debugCode = debugCode;
    }

    public static SmsSendResult ok(String requestId, int expireInSec, String debugCode) {
        return new SmsSendResult(true, null, requestId, expireInSec, debugCode);
    }

    public static SmsSendResult fail(String message) {
        return new SmsSendResult(false, message, null, 0, null);
    }
}
