# 绑定手机号 + 手机验证码登录 设计说明

**日期：** 2026-09-16  
**状态：** 已评审（待实现计划）  
**范围：** 本轮只做账号手机号强制绑定与手机验证码登录；朋友/消息页另立项。

## 背景

当前 App 仅支持用户名密码注册/登录；`UserEntity` 无 `phone` 字段；底栏「朋友」「消息」仍为占位。本设计落地账号侧手机号能力，并为日后真实短信 SDK 预留接口形状。

## 目标

1. 用户必须绑定手机号后才能进入 `MainActivity`。
2. 登录页支持「账号密码」与「手机验证码」两种方式。
3. 短信发送/校验先走本地 `MockSmsGateway`；业务 API 路径与 DTO 按真实短信风格设计，替换网关实现时无需改调用方。

## 非目标（本轮不做）

- 改绑 / 解绑手机号
- 接入真实短信 SDK / 运营商
- 验证码登录自动注册开户
- 朋友页、消息/私信
- 手机号 + 密码登录（无验证码）

## 方案选型

采用 **短信网关抽象（SmsGateway）**：

- `MockSmsGateway`：内存验证码、固定码 `123456`、60s 冷却、场景隔离
- `LocalAuthService` 负责绑号写库、手机登录发 token、唯一性校验
- UI 经 `AuthRepository` → `DouyinApi` → `LocalApiDispatcher` 调用，与现有 LocalApi 架构一致

## 产品流程与闸门

### 入口

1. **登录页**：两个 Tab ——「账号密码」｜「手机验证码」
2. **注册成功后**：不进主页，直接进入绑号页
3. **账号密码登录成功但未绑号**：进入绑号页（不可返回主页）
4. **手机验证码登录**：仅已绑定该手机号的账号可登录；未注册/未绑定 → 提示先注册并绑定
5. **个人页**：展示脱敏手机号（如 `138****8000`）；无改绑入口

### 强制闸门

路由决策（可抽纯函数便于单测）：

| token | phone | 去向 |
|-------|-------|------|
| 无 | — | `LoginActivity` |
| 有 | 空 | `BindPhoneActivity` |
| 有 | 非空 | `MainActivity` |

规则：

- `Splash` / 登录 / 注册成功后：`GET me`，若 `phone` 为空 → `BindPhoneActivity`，并清理回退栈，Back 不进入 Main
- 绑号成功 → `MainActivity`，清理登录/绑号栈
- 冷启动已有 token：hydrate 后同样检查 `phone`

### 校验与验证码规则

- 大陆手机号：11 位，以 `1` 开头
- 一号一账号：已被其他用户绑定则绑号失败
- 验证码：6 位数字；有效期 5 分钟；重发冷却 60 秒
- Mock 固定验证码：`123456`；DEBUG 下发送成功可 Toast「验证码：123456」
- 场景码：`bind` / `login`，同号不同场景互不串码

## 数据模型

### User

在 `UserEntity`、`UserDto`、`UserProfileDto` 增加：

- `phone`：`String`，可空；数据库唯一索引（允许多个 `null` 或按 Room 能力处理未绑用户）

Room 升版迁移：既有用户 `phone = null`，下次进入强制绑号流程。

会话仍使用现有 `TokenStore`（token + userId）；不单独持久化手机号到 Preferences。

## API

基址与现有一致：`https://app.local/`，经 `LocalApiInterceptor` 分发。

| 方法 | 路径 | 鉴权 | Body / 说明 |
|------|------|------|-------------|
| POST | `api/auth/sms/send` | `scene=bind` 需要登录；`scene=login` 不需要 | `{ "phone": "...", "scene": "bind" \| "login" }` → `{ "requestId", "expireInSec", "debugCode?" }` |
| POST | `api/auth/phone/login` | 否 | `{ "phone", "code" }` → 与现有 login 一致：token + user |
| POST | `api/users/me/phone` | 是 | `{ "phone", "code" }` → 更新后的当前用户 |
| GET | `api/users/me` | 是 | 响应增加 `phone` 字段（完整号码；UI 负责脱敏） |

### 错误约定

与现有 `ApiResponse` 风格一致，至少覆盖：

| 条件 | 提示方向 |
|------|----------|
| 手机号格式非法 | 前端优先拦截；API 亦可拒 |
| 验证码错误或过期 | 「验证码错误或已过期」 |
| 发送过频 / 冷却中 | 「请稍后再试」 |
| 绑号：手机号已被占用 | 「该手机号已绑定其他账号」 |
| 登录：手机号未绑定任何账号 | 「该手机号未绑定账号，请先注册」 |
| `bind` 场景未登录发码 | 401，回登录 |
| 已绑定用户再次绑号 | 「已绑定手机号」 |

## 模块与页面

### 分层

```text
UI (LoginActivity / BindPhoneActivity / SplashActivity / ProfileFragment)
  → AuthRepository（sendSms / loginByPhone / bindPhone）
    → DouyinApi
      → LocalApiDispatcher
        → LocalAuthService + SmsGateway (MockSmsGateway)
          → UserDao / TokenStore
```

### SmsGateway

```text
sendCode(phone, scene) → { requestId, expireInSec, debugCode? }
verifyCode(phone, scene, code) → boolean
```

日后真实短信：新增 `RealSmsGateway` 实现同一接口，业务路径与 DTO 不变。

### UI 职责

| 组件 | 职责 |
|------|------|
| `LoginActivity` | 双 Tab；成功后走统一路由（是否已绑号） |
| `BindPhoneActivity`（新建） | 手机号 + 验证码；无「跳过」；成功进 Main |
| `RegisterActivity` | 注册成功 → BindPhone，不进 Main |
| `SplashActivity` | hydrate 后按 token/phone 路由 |
| `ProfileFragment` | 脱敏展示手机号 |
| 共用表单逻辑（Helper 或简单封装） | 手机号校验、倒计时、验证码输入（登录 Tab 与绑号页复用） |

### 主要文件影响

- **新建：** `BindPhoneActivity`、对应 layout、`SmsGateway`、`MockSmsGateway`、短信/绑号相关 Request/Response DTO、可选路由纯函数与单测
- **修改：** `UserEntity`（含 DB version/migration）、`LocalAuthService`、`LocalApiDispatcher`、`DouyinApi`、`AuthRepository`、`LoginActivity`、`SplashActivity`、`RegisterActivity`、`ProfileFragment`、`AndroidManifest`、`strings.xml`

## 错误处理（UI）

- 格式问题：前端拦截，不发请求
- 冷却中：按钮倒计时，禁止点击
- 验证码错误：保留手机号，允许重试
- Mock 不依赖外网与运营商

## 测试与验收

### 单测（优先 JVM）

1. `MockSmsGateway`：发码、冷却、过期、场景隔离、校验成功/失败
2. `LocalAuthService`：绑号成功与唯一冲突；手机登录发 token；未绑定登录失败
3. 路由纯函数：`(hasToken, hasPhone) → Login | Bind | Main`

### 手工验收

1. 注册 → 强制绑号 → 进入首页；Back 无法回到未绑状态进 Main
2. 冷启动：已登录未绑 → 绑号页
3. 登录双 Tab：密码登录未绑 → 绑号；验证码登录仅已绑号可用
4. 个人页展示脱敏手机号
5. 错误路径：错码、占号、未绑定号登录

### 验收标准

- 未绑号无法进入 `MainActivity`
- 手机验证码登录仅对已绑号用户有效
- 替换 `SmsGateway` 实现时，API 路径与 DTO 保持不变

## 后续（非本轮）

- 朋友页完善（关注/好友列表等）
- 真实短信网关接入
- 改绑 / 解绑流程
