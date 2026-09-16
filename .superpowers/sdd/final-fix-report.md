# Final Fix Report: Profile phone leak + getMe 401 gate

## Status

**DONE**

## Fixes

### Fix 1 — Profile username hides phone

`UserProfileController.bindProfile`: when `username` equals `phone` or `PhoneValidator.isValidPhone(username)`, hide `tvUsername` (GONE). Otherwise show `@username`. `tv_phone` still uses `PhoneMasker.mask`.

### Fix 2 — getMe failure / HTTP 401 gate

| Area | Change |
|------|--------|
| `AuthRepository.AuthCallback` | HTTP 401 or parsed `code==401` on unsuccessful / null-body responses now calls `clearSession()` (aligned with body.code==401 path) |
| `SplashActivity` | Any getMe error → `logout()` + Login; never BindPhone |
| `LoginActivity.routeIfAlreadyLoggedIn` | Any getMe error → `logout()` (if still logged in) + show login form; BindPhone only via onSuccess empty phone through `AuthNavigator` |

## Verification

```text
.\gradlew.bat :app:compileDebugJavaWithJavac :app:assembleDebug
BUILD SUCCESSFUL in 7s

.\gradlew.bat :app:testDebugUnitTest
BUILD SUCCESSFUL in 5s
```

## Commit

`fix(auth): hide phone usernames on profile and harden getMe gate`
