# Task 9 Report: Update androidTest + self-check

## Status

**DONE**

## Summary

Updated instrumented `AuthApiTest.loginDemoUser_returnsToken` to phone OTP (`sendSms` login → `loginByPhone` with `13800138000` / `123456`). Deleted orphan unit test `PlaybackUiPolicyTest.java` (no `PlaybackUiPolicy` source). Unit tests and debug assemble both green.

## Changes

| File | Action |
|------|--------|
| `app/src/androidTest/java/com/example/douyin/AuthApiTest.java` | Phone OTP flow; assert HTTP 200, token; accept demo username or phone `13800138000` |
| `app/src/test/java/com/example/douyin/feed/PlaybackUiPolicyTest.java` | **Deleted** (untracked orphan; blocked unit test compile) |

`LocalAuthServiceTest` / `LocalAuthPhoneTest` left unchanged (already match 410 / phone flows).

## Verification

```text
.\gradlew.bat :app:testDebugUnitTest
BUILD SUCCESSFUL in 5s

.\gradlew.bat :app:assembleDebug
BUILD SUCCESSFUL in 2s
```

`connectedAndroidTest` not run (no device in this session).

## Commit

`test(auth): update instrumented tests for phone OTP auth`
