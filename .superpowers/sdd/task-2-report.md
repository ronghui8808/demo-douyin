# Task 2 Report: MediaUrlHelper（可预取规则 + 2MB 常量）

**Status:** DONE_WITH_CONCERNS  
**Branch:** `20260921-exoplayer`  
**Commit:** `ab58463` — `feat: add MediaUrlHelper for remote video prefetch rules`

---

## Summary

Implemented `MediaUrlHelper` with `PREFETCH_BYTES` (2MB), `normalize`, `isRemoteHttpUrl`, and `shouldPrefetch` per brief. TDD: RED (compile fail) → GREEN (4/4 pass). Brief implementation is verbatim; test required Robolectric bootstrap because `TextUtils.isEmpty` is not mocked on JVM unit tests.

---

## TDD Evidence

### RED — Step 2

**Command:**
```
.\gradlew.bat :app:testDebugUnitTest --tests com.example.douyin.player.MediaUrlHelperTest
```

**Result:** Exit code 1. `:app:compileDebugUnitTestJavaWithJavac FAILED` — 10 errors, `找不到符号: 变量 MediaUrlHelper` (class not yet created). RED confirmed.

### GREEN — Step 4

**Command:**
```
.\gradlew.bat :app:testDebugUnitTest --tests com.example.douyin.player.MediaUrlHelperTest
```

**Result:** Exit code 0. `BUILD SUCCESSFUL`. 4 tests completed, 0 failed:
- `remoteHttpUrls_arePrefetchable` PASS
- `localUrls_areNotPrefetchable` PASS
- `normalize_trimsWhitespace` PASS
- `prefetchBytes_isTwoMegabytes` PASS

All other unit tests (15 total) also pass.

---

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/java/com/example/douyin/player/MediaUrlHelper.java` | Created — verbatim from brief |
| `app/src/test/java/com/example/douyin/player/MediaUrlHelperTest.java` | Created — brief test cases + Robolectric `@RunWith` / `@Config(sdk=35)` |
| `app/build.gradle.kts` | Added `testOptions.unitTests.isIncludeAndroidResources` + `testImplementation(libs.robolectric)` |
| `gradle/libs.versions.toml` | Added `robolectric = "4.14.1"` catalog entry |

---

## Self-Review

| Requirement | Status |
|-------------|--------|
| `PREFETCH_BYTES = 2L * 1024L * 1024L` | ✅ |
| `normalize` trims / null→`""` via `TextUtils.isEmpty` | ✅ |
| `isRemoteHttpUrl` checks `http://` / `https://` | ✅ |
| `shouldPrefetch` ≡ `isRemoteHttpUrl(normalize(url))` | ✅ |
| Private constructor, `final` class | ✅ |
| TDD RED then GREEN evidence | ✅ |
| No ExoMediaCache / player changes | ✅ |
| Commit with specified message | ✅ |

---

## Concerns

1. **Test file deviation:** Brief provided verbatim JUnit4 test without Robolectric. Plain JVM unit tests fail with `TextUtils.isEmpty not mocked`. Added `@RunWith(RobolectricTestRunner.class)` and `@Config(sdk = 35)` (Robolectric 4.14 max SDK 35 vs project `targetSdk=36`).
2. **Extra gradle changes:** Brief listed only two source files; Robolectric dependency + `isIncludeAndroidResources` required for GREEN on this project.
3. **Robolectric side-effect:** Running tests loads `DouyinApp` / Room via Robolectric shadows (SQLite CloseGuard warning in log); does not affect test assertions.

---

## Review Fix (Task 2 findings)

**Addressed:** Important review findings — remove Robolectric infra; make `MediaUrlHelper` JVM-testable.

### Changes

| File | Change |
|------|--------|
| `MediaUrlHelper.java` | Replaced `TextUtils.isEmpty(url)` with `url == null \|\| url.isEmpty()` before `trim()` |
| `MediaUrlHelperTest.java` | Removed `@RunWith(RobolectricTestRunner.class)` and `@Config(sdk = 35)` — plain JUnit4 |
| `app/build.gradle.kts` | Reverted `testOptions.unitTests.isIncludeAndroidResources` and `testImplementation(libs.robolectric)` |
| `gradle/libs.versions.toml` | Reverted `robolectric` version + library catalog entries |

### Test (post-fix)

**Command:**
```
.\gradlew.bat :app:testDebugUnitTest --tests com.example.douyin.player.MediaUrlHelperTest --rerun-tasks
```

**Result:** Exit code 0. `BUILD SUCCESSFUL`. 4 tests completed, 0 failed (plain JVM, no Robolectric):
- `remoteHttpUrls_arePrefetchable` PASS
- `localUrls_areNotPrefetchable` PASS
- `normalize_trimsWhitespace` PASS
- `prefetchBytes_isTwoMegabytes` PASS
