# 手机号验证码注册/登录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将账号体系改为「仅手机号 + 验证码」注册与登录；存量无手机号用户强制绑号；短信走可替换的 `MockSmsGateway`。

**Architecture:** UI → `AuthRepository` → `DouyinApi` → `LocalApiDispatcher` → `LocalAuthService` + `SmsGateway`；Room `users.phone` 唯一索引；Splash/`AuthRouter` 按 token + phone 分流 Login / BindPhone / Main。

**Tech Stack:** Java 11、AppCompat、Retrofit/OkHttp 本地拦截、Room、JUnit4 单测（`app/src/test`）

**Spec:** `docs/superpowers/specs/2026-09-16-phone-bind-design.md`

## Global Constraints

- 语言：Java（不引入 Kotlin）
- 无独立后端；继续 `https://app.local/` + LocalApi
- Mock 验证码固定 `123456`；场景 `register` / `login` / `bind`
- 本轮不做：改绑/解绑、真实短信、自动开户登录、朋友页
- 旧密码登录/注册 API：Local 返回错误「请使用手机号登录/注册」，UI 不再调用
- 业务错误文案必须可达 UI（修复 AuthCallback 吞掉 errorBody 的问题）

---

## File Structure

| 路径 | 职责 |
|------|------|
| `auth/AuthDestination.java` | 路由枚举 + `resolve(hasToken, hasPhone)` |
| `auth/PhoneValidator.java` | 手机号/验证码格式校验 |
| `auth/PhoneMasker.java` | 脱敏 `138****8000` |
| `auth/SmsCountdownHelper.java` | 获取验证码按钮 60s 倒计时 |
| `local/sms/SmsGateway.java` | 发码/验码接口 |
| `local/sms/MockSmsGateway.java` | 内存 Mock 实现 |
| `local/sms/SmsSendResult.java` | `requestId/expireInSec/debugCode` |
| `network/model/SmsSendRequest.java` 等 | 请求/响应 DTO |
| `auth/BindPhoneActivity.java` + layout | 存量绑号 |
| 修改：`UserEntity`/`UserDao`/`AppDatabase`/`LocalAuthService`/`LocalApiDispatcher`/`DouyinApi`/`AuthRepository`/`EntityMapper`/`Login*`/`Register*`/`Splash*`/`Profile*`/`Seed*`/`strings`/`Manifest` | 见各 Task |

---

### Task 1: AuthDestination + PhoneValidator + PhoneMasker

**Files:**
- Create: `app/src/main/java/com/example/douyin/auth/AuthDestination.java`
- Create: `app/src/main/java/com/example/douyin/auth/PhoneValidator.java`
- Create: `app/src/main/java/com/example/douyin/auth/PhoneMasker.java`
- Test: `app/src/test/java/com/example/douyin/auth/AuthDestinationTest.java`
- Test: `app/src/test/java/com/example/douyin/auth/PhoneValidatorTest.java`
- Test: `app/src/test/java/com/example/douyin/auth/PhoneMaskerTest.java`

**Interfaces:**
- Produces: `AuthDestination.resolve(boolean hasToken, boolean hasPhone)` → `LOGIN` \| `BIND_PHONE` \| `MAIN`
- Produces: `PhoneValidator.isValidPhone(String)` / `isValidCode(String)`
- Produces: `PhoneMasker.mask(String phone)`

- [ ] **Step 1: 写失败单测**

```java
// AuthDestinationTest
assertEquals(AuthDestination.LOGIN, AuthDestination.resolve(false, false));
assertEquals(AuthDestination.LOGIN, AuthDestination.resolve(false, true));
assertEquals(AuthDestination.BIND_PHONE, AuthDestination.resolve(true, false));
assertEquals(AuthDestination.MAIN, AuthDestination.resolve(true, true));

// PhoneValidatorTest
assertTrue(PhoneValidator.isValidPhone("13800138000"));
assertFalse(PhoneValidator.isValidPhone("12345678901"));
assertFalse(PhoneValidator.isValidPhone("1380013800"));
assertTrue(PhoneValidator.isValidCode("123456"));
assertFalse(PhoneValidator.isValidCode("12345"));

// PhoneMaskerTest
assertEquals("138****8000", PhoneMasker.mask("13800138000"));
assertEquals("", PhoneMasker.mask(null));
```

- [ ] **Step 2: 跑测确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.example.douyin.auth.AuthDestinationTest`
Expected: FAIL（类不存在）

- [ ] **Step 3: 最小实现**

```java
public enum AuthDestination {
    LOGIN, BIND_PHONE, MAIN;

    public static AuthDestination resolve(boolean hasToken, boolean hasPhone) {
        if (!hasToken) {
            return LOGIN;
        }
        return hasPhone ? MAIN : BIND_PHONE;
    }
}

public final class PhoneValidator {
    private PhoneValidator() {}
    public static boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }
    public static boolean isValidCode(String code) {
        return code != null && code.matches("^\\d{6}$");
    }
}

public final class PhoneMasker {
    private PhoneMasker() {}
    public static String mask(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
```

- [ ] **Step 4: 跑测确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.douyin.auth.*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/douyin/auth/AuthDestination.java \
  app/src/main/java/com/example/douyin/auth/PhoneValidator.java \
  app/src/main/java/com/example/douyin/auth/PhoneMasker.java \
  app/src/test/java/com/example/douyin/auth/
git commit -m "feat(auth): add phone route helpers and validators"
```

---

### Task 2: SmsGateway + MockSmsGateway

**Files:**
- Create: `app/src/main/java/com/example/douyin/local/sms/SmsGateway.java`
- Create: `app/src/main/java/com/example/douyin/local/sms/SmsSendResult.java`
- Create: `app/src/main/java/com/example/douyin/local/sms/MockSmsGateway.java`
- Test: `app/src/test/java/com/example/douyin/local/sms/MockSmsGatewayTest.java`

**Interfaces:**
- Produces:
  - `SmsSendResult sendCode(String phone, String scene)` — 成功返回；冷却中抛业务用返回值或由 AuthService 包装（见下：Gateway 返回 `ApiResponse` 风格由 Service 层处理更干净）
  - 推荐 Gateway 抛业务异常不合适；用结果类型：

```java
public final class SmsSendResult {
    public final boolean ok;
    public final String errorMessage; // ok=false 时
    public final String requestId;
    public final int expireInSec;
    public final String debugCode; // Mock 固定 123456
    // 静态工厂 ok(...) / fail(message)
}

public interface SmsGateway {
    SmsSendResult sendCode(String phone, String scene);
    boolean verifyCode(String phone, String scene, String code);
}
```

- Consumes: Task 1 的 `PhoneValidator`（可选，也可由 Service 先校验）

- [ ] **Step 1: 写失败单测**

```java
MockSmsGateway gw = new MockSmsGateway();
// 可注入 clock：构造 (long cooldownMs, long expireMs, LongSupplier nowMs)
MockSmsGateway gw = new MockSmsGateway(60_000L, 300_000L, () -> now);

SmsSendResult r1 = gw.sendCode("13800138000", "login");
assertTrue(r1.ok);
assertEquals("123456", r1.debugCode);

SmsSendResult r2 = gw.sendCode("13800138000", "login");
assertFalse(r2.ok); // 冷却

assertTrue(gw.verifyCode("13800138000", "login", "123456"));
assertFalse(gw.verifyCode("13800138000", "login", "000000"));
assertFalse(gw.verifyCode("13800138000", "register", "123456")); // 场景隔离：register 未发码

// 发 register 码后 login 场景仍失败
gw.sendCode("13900139000", "register");
assertFalse(gw.verifyCode("13900139000", "login", "123456"));
assertTrue(gw.verifyCode("13900139000", "register", "123456"));

// 过期：推进 now 超过 expireMs
assertFalse(gw.verifyCode("13800138000", "login", "123456"));
```

用可变 `AtomicLong now` 注入 `LongSupplier` 控制时间。

- [ ] **Step 2: 跑测确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.example.douyin.local.sms.MockSmsGatewayTest`
Expected: FAIL

- [ ] **Step 3: 实现 MockSmsGateway**

- 内存 `Map<String, Entry>`，key = `scene + "|" + phone`
- Entry: code=`123456`, sentAt, expireAt
- `sendCode`：冷却内 fail「请稍后再试」；否则写入并 `ok(requestId=UUID, expireInSec=300, debugCode=123456)`
- `verifyCode`：匹配且未过期则 true，并删除该 entry（一次性）

- [ ] **Step 4: 跑测通过并 Commit**

```bash
git add app/src/main/java/com/example/douyin/local/sms/ \
  app/src/test/java/com/example/douyin/local/sms/
git commit -m "feat(auth): add mock SMS gateway with cooldown and scenes"
```

---

### Task 3: User 表 phone 字段 + Dao + Migration

**Files:**
- Modify: `app/src/main/java/com/example/douyin/local/db/entity/UserEntity.java`
- Modify: `app/src/main/java/com/example/douyin/local/db/UserDao.java`
- Modify: `app/src/main/java/com/example/douyin/local/db/AppDatabase.java`（version 2 + Migration）
- Modify: `app/src/main/java/com/example/douyin/network/model/UserDto.java` — 增加 `public String phone;`
- Modify: `app/src/main/java/com/example/douyin/local/EntityMapper.java` — 映射 phone
- Modify: `UserProfileDto`（若存在）增加 phone
- Test: `app/src/test/java/com/example/douyin/local/db/UserDaoPhoneTest.java`（用 `AppDatabase.createInMemory`；若单测无 Android 依赖问题则用 Robolectric 或放 androidTest——本项目已有 `allowMainThreadQueries` + androidTest；**优先 androidTest** 若 unit 无法跑 Room）

**本仓库 unit test 现状：** PasswordHasher 等纯 Java 可测；Room 建议：

- Test: `app/src/androidTest/java/com/example/douyin/local/db/UserDaoPhoneTest.java`

**Interfaces:**
- Produces: `UserDao.findByPhone(String)`、`UserDao.updatePhone(long id, String phone)`
- Entity: `@ColumnInfo(name = "phone") public String phone;` + `@Index(value = "phone", unique = true)`（与 username 索引并存）

- [ ] **Step 1: 写 androidTest**

```java
AppDatabase db = AppDatabase.createInMemory(context);
UserEntity u = new UserEntity();
u.username = "13800138000";
u.passwordHash = "";
u.nickname = "测";
u.phone = "13800138000";
u.createdAt = System.currentTimeMillis();
long id = db.userDao().insert(u);
assertEquals(id, db.userDao().findByPhone("13800138000").id);
db.userDao().updatePhone(id, "13900139000");
assertEquals("13900139000", db.userDao().findById(id).phone);
```

- [ ] **Step 2: 跑测失败 → 实现 Entity/Dao/Migration/Mapper → 通过**

Migration:

```java
static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("ALTER TABLE users ADD COLUMN phone TEXT");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_phone ON users(phone)");
    }
};
```

`AppDatabase.get`：`.addMigrations(MIGRATION_1_2)`，`version = 2`。

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(auth): add users.phone column and dao queries"
```

---

### Task 4: LocalAuthService 手机注册/登录/绑号 + 旧 API 关闭

**Files:**
- Modify: `app/src/main/java/com/example/douyin/local/service/LocalAuthService.java`
- Modify: `app/src/main/java/com/example/douyin/local/LocalServices.java`（注入 `SmsGateway` 单例 `MockSmsGateway`）
- Create DTO 若尚未创建（可本 Task 与 Task 5 共用）：见 Task 5
- Test: `app/src/androidTest/java/com/example/douyin/local/service/LocalAuthPhoneTest.java`（或扩展现有 `LocalAuthServiceTest`）

**Interfaces:**
- Consumes: `SmsGateway`, `UserDao.findByPhone/updatePhone`, `PhoneValidator`
- Produces:
  - `ApiResponse<SmsSendResultDto> sendSms(String phone, String scene, Long userIdOrNull)`
  - `ApiResponse<LoginResult> registerByPhone(String phone, String code, String nickname)`
  - `ApiResponse<LoginResult> loginByPhone(String phone, String code)`
  - `ApiResponse<UserDto> bindPhone(long userId, String phone, String code)`
- 旧 `register`/`login`：改为 `ApiResponse.error(410, "请使用手机号登录/注册")`

**sendSms 规则：**
- 校验手机号格式
- `scene` 仅允许 `register`|`login`|`bind`
- `bind`：`userIdOrNull == null` → 401；用户已有 phone → 400「已绑定手机号」
- `register`：`findByPhone != null` → 400「该手机号已注册，请直接登录」
- `login`：不要求已存在（发码允许；登录时再判未注册）——或登录发码时也允许未注册（防枚举）；spec 登录失败在 login 接口提示未注册
- 调用 `smsGateway.sendCode`

**registerByPhone：** verify `register` → 占号检查 → insert（username=phone, passwordHash="", phone=phone, nickname 默认「用户」+后四位）→ createLoginResult

**loginByPhone：** verify `login` → findByPhone 空 → 「该手机号未注册，请先注册」→ createLoginResult

**bindPhone：** 用户已有 phone → 错；verify `bind` → 号被他人占用 → 错；`updatePhone` → getMe

- [ ] **Step 1: 写 androidTest 覆盖：注册成功、重复注册、登录成功、未注册登录、绑号成功、占号绑号**
- [ ] **Step 2: 实现至测试通过**
- [ ] **Step 3: Commit**

```bash
git commit -m "feat(auth): phone OTP register, login, and bind in LocalAuthService"
```

---

### Task 5: API DTO + DouyinApi + LocalApiDispatcher + 错误文案可达

**Files:**
- Create: `SmsSendRequest`, `PhoneLoginRequest`, `PhoneRegisterRequest`, `BindPhoneRequest`, `SmsSendResultDto`（字段与 `SmsSendResult` 对齐，供 JSON）
- Modify: `DouyinApi.java`
- Modify: `LocalApiDispatcher.java`
- Modify: `AuthRepository.java`（本 Task 先修 errorBody；下一 Task 加方法也可合并）

**DouyinApi 新增：**

```java
@POST("api/auth/sms/send")
Call<ApiResponse<SmsSendResultDto>> sendSms(@Body SmsSendRequest body);

@POST("api/auth/phone/register")
Call<ApiResponse<LoginResult>> registerByPhone(@Body PhoneRegisterRequest body);

@POST("api/auth/phone/login")
Call<ApiResponse<LoginResult>> loginByPhone(@Body PhoneLoginRequest body);

@POST("api/users/me/phone")
Call<ApiResponse<UserDto>> bindPhone(@Body BindPhoneRequest body);
```

旧 `register`/`login` 可保留接口定义但 Dispatcher 走 410。

**Dispatcher：**
- `POST /api/auth/sms/send` → 解析 scene；bind 时 `requireUserId`
- `POST /api/auth/phone/register|login`
- `POST /api/users/me/phone` → requireUserId + bindPhone
- 旧 register/login → `ApiResponse.error(410, "请使用手机号登录/注册")`

**修复 AuthRepository.AuthCallback.onResponse：**

当 `!response.isSuccessful()` 时，尝试 `response.errorBody()` 或 `response.body()` 用 Gson 解析 `ApiResponse`，取出 `message`；解析失败再回退「请求失败」。

（因 `LocalApiResult.from` 把业务 code 映射为 HTTP 非 2xx，必须修此处，否则 Toast 永远「请求失败」。）

- [ ] **Step 1: 实现 DTO + Api + Dispatcher + AuthCallback 修复**
- [ ] **Step 2: 用现有 androidTest 或临时调用确认错误文案非「请求失败」**
- [ ] **Step 3: Commit**

```bash
git commit -m "feat(auth): wire phone OTP APIs and surface local error messages"
```

---

### Task 6: AuthRepository 手机号方法

**Files:**
- Modify: `app/src/main/java/com/example/douyin/repository/AuthRepository.java`

**Interfaces:**
- Produces:
  - `sendSms(String phone, String scene, ApiCallback<SmsSendResultDto>)`
  - `registerByPhone(String phone, String code, String nickname, ApiCallback<LoginResult>)`
  - `loginByPhone(String phone, String code, ApiCallback<LoginResult>)`
  - `bindPhone(String phone, String code, ApiCallback<UserDto>)`
- login/register 成功仍 `tokenStore.saveToken`
- 可删除或保留旧 `login`/`register` 方法（UI 不再调用；建议删除避免误用）

- [ ] **Step 1: 实现方法（模式复制现有 AuthCallback）**
- [ ] **Step 2: Commit**

```bash
git commit -m "feat(auth): expose phone OTP methods on AuthRepository"
```

---

### Task 7: 登录/注册 UI 改为验证码

**Files:**
- Modify: `res/layout/activity_login.xml` — 手机号、验证码、获取验证码、登录；去掉密码；DEBUG 跳过改为填演示手机号+123456
- Modify: `res/layout/activity_register.xml` — 手机号、验证码、昵称、获取验证码、注册
- Modify: `LoginActivity.java` / `RegisterActivity.java`
- Create: `auth/SmsCountdownHelper.java`（可选，两页共用）
- Modify: `res/values/strings.xml`
- Modify: `SeedDataInitializer` — demo 用户 `phone = "13800138000"`（新建与已存在用户补写 phone）

**LoginActivity 行为：**
- 校验 PhoneValidator → `loginByPhone`
- 成功后：`routeAfterAuth(LoginResult)` → 若 `user.phone` 空 → BindPhone，否则 Main（CLEAR_TASK）
- DEBUG：`btn_skip_login` 填 `13800138000` / `123456` 后登录（需先 sendSms 或 Mock 允许未发码？**Mock 要求先发码** → skip 时先 `sendSms(login)` 再 `loginByPhone`，或 LocalAuthService 在 DEBUG 接受固定码——更简单：skip 回调里顺序 sendSms → loginByPhone）

**RegisterActivity：**
- sendSms scene=register；registerByPhone；成功 CLEAR_TASK → Main

- [ ] **Step 1: 改 layout + strings**
- [ ] **Step 2: 改 Activity 逻辑**
- [ ] **Step 3: 更新 Seed demo phone**
- [ ] **Step 4: 手工或编译 `.\gradlew.bat :app:assembleDebug`**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat(auth): replace password forms with phone OTP login and register"
```

---

### Task 8: BindPhoneActivity + Splash 路由 + Profile 脱敏

**Files:**
- Create: `auth/BindPhoneActivity.java`
- Create: `res/layout/activity_bind_phone.xml`
- Modify: `AndroidManifest.xml`（注册 Activity，portrait）
- Modify: `SplashActivity.java` — 有 token 时 `getMe`（或读本地：Splash 目前只看 TokenStore；**按 spec 需知 phone**）

**Splash 实现要点：**
- 未登录 → Login
- 已登录 → `AuthRepository.getMe`：
  - 成功 → `AuthDestination.resolve(true, !TextUtils.isEmpty(me.phone))` → Main 或 BindPhone
  - 失败 401 → Login
- 注意 Splash `noHistory`：异步回调里再 startActivity；展示短暂 splash 文案即可

**BindPhoneActivity：**
- 禁止跳过；`onBackPressed`/`OnBackPressedDispatcher`：不 finish 到 Main（可 `moveTaskToBack` 或忽略）；绑成功 CLEAR_TASK → Main
- sendSms scene=bind；bindPhone

**统一入口辅助（建议）：**

```java
// AuthNavigator.java
public static void openAfterAuth(Activity activity, UserDto user) { ... }
public static void openByDestination(Activity activity, AuthDestination d) { ... }
```

Login/Register/Splash/Bind 共用，避免漏闸门。

**Profile：**
- `UserProfileController.bindProfile`：若有 phone，在昵称下或合适 TextView 显示 `PhoneMasker.mask`；若 layout 无控件，可临时拼到昵称副标题或增加 `tv_phone`（优先加 TextView，改 `fragment_profile` / 对应 layout）

- [ ] **Step 1: BindPhone UI + Manifest**
- [ ] **Step 2: Splash + AuthNavigator**
- [ ] **Step 3: Profile 脱敏展示**
- [ ] **Step 4: `assembleDebug` + 手工路径核对**
- [ ] **Step 5: Commit**

```bash
git commit -m "feat(auth): enforce bind-phone gate and show masked phone on profile"
```

---

### Task 9: 更新 androidTest + 自检

**Files:**
- Modify: `app/src/androidTest/.../AuthApiTest.java` — 改为 phone 登录（先 sendSms login，再 loginByPhone）；demo 手机 `13800138000` / `123456`
- Modify: `app/src/androidTest/.../LocalAuthServiceTest.java` — 去掉密码注册断言或改为 410；补充 phone 路径（若 Task 4 已覆盖可精简）

- [ ] **Step 1: 更新测试**
- [ ] **Step 2: Run**

```bash
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

（无设备时至少 unit + assembleDebug）

- [ ] **Step 3: Commit**

```bash
git commit -m "test(auth): update instrumented tests for phone OTP auth"
```

---

## 手工验收清单

1. 新安装：注册手机号 → 验证码 123456 → 进首页；无密码框
2. 退出 → 验证码登录同一号 → 进首页
3. 未注册号登录 → 提示先注册
4. 已注册号再注册发码/提交 → 提示已注册
5. DEBUG 跳过：演示号进首页
6. 模拟存量：DB 中用户 phone=null 且有 token → 冷启动进绑号页 → 绑完进首页
7. 个人页显示 `138****8000`
8. 错误码提示不是笼统「请求失败」

---

## Spec Coverage Self-Check

| Spec 项 | Task |
|---------|------|
| 仅验证码登录/注册 | 7 |
| MockSmsGateway + 场景 | 2, 4 |
| 强制存量绑号闸门 | 1, 8 |
| API 路径 | 5 |
| phone 字段/迁移 | 3 |
| 个人页脱敏 | 1, 8 |
| 旧密码 API 关闭 | 4, 5 |
| 单测网关/路由/鉴权 | 1, 2, 4, 9 |
| 错误文案可达 | 5 |

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-16-phone-otp-auth.md`. Two execution options:

**1. Subagent-Driven (recommended)** — 每 Task 新开子代理，Task 间复查，迭代快

**2. Inline Execution** — 本会话按 executing-plans 连续执行并设检查点

Which approach?
