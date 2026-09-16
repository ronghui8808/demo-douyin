# 手机号验证码注册 / 登录 设计说明

**日期：** 2026-09-16  
**状态：** 已确认（实现计划已就绪）  
**范围：** 账号体系改为「仅手机号 + 验证码」注册与登录；朋友/消息页另立项。

## 修订说明（相对上一版）

- **去掉**账号密码登录与密码注册表单。
- **注册**改为：手机号 + 验证码 + 昵称 → 开户时即写入 `phone`，成功后直接进主页。
- **登录**仅保留手机验证码；未注册号提示先注册（不自动开户）。
- **绑号页**仅服务存量无 `phone` 的旧账号（Room 迁移后）；新用户不再走绑号闸门。

## 背景

当前 App 仅支持用户名密码注册/登录；`UserEntity` 无 `phone` 字段。本设计将鉴权切换为手机验证码，并为日后真实短信 SDK 预留接口形状。

## 目标

1. 登录、注册均只使用手机号 + 短信验证码（Mock 可替换）。
2. 新用户注册成功即已绑定手机号，可直接进入 `MainActivity`。
3. 存量无手机号用户：强制进入绑号页，完成后才能进主页。
4. `SmsGateway` 抽象；业务 API 形状贴近真实短信，替换实现时调用方不变。

## 非目标（本轮不做）

- 账号密码登录 / 密码注册 UI
- 改绑 / 解绑手机号
- 接入真实短信 SDK
- 验证码登录自动注册（登录与注册分离）
- 朋友页、消息/私信

## 方案选型

采用 **短信网关抽象（SmsGateway）**：

- `MockSmsGateway`：内存验证码、固定码 `123456`、60s 冷却、场景隔离
- `LocalAuthService` 负责注册开户、手机登录、存量绑号、唯一性校验
- UI 经 `AuthRepository` → `DouyinApi` → `LocalApiDispatcher`，与现有 LocalApi 一致

## 产品流程

### 登录（`LoginActivity`）

- 仅手机号 + 验证码 +「登录」；链到注册页
- 校验 `scene=login` 验证码 → 按 `phone` 查用户
  - 存在 → 发 token → Main（若无 phone 则不应出现；存量异常走绑号）
  - 不存在 → 「该手机号未注册，请先注册」

### 注册（`RegisterActivity`）

- 手机号 + 验证码 + 昵称（必填或默认「用户」+ 后四位）
- 发码 `scene=register`：若号已被占用 → 「该手机号已注册，请直接登录」
- 校验通过 → 创建用户（`phone` 已设）→ token → **直接 Main**（不经绑号页）
- `username`：内部使用手机号（或 `p_` + 手机号），保证唯一；不再向用户展示「用户名」字段
- `passwordHash`：新用户置空或固定占位；UI 不再收集密码

### 存量绑号（`BindPhoneActivity`）

- 仅当：已登录且 `me.phone` 为空（旧数据迁移）
- 发码 `scene=bind`（需登录）→ 校验 → 写入 `phone` → Main
- 无「跳过」；Back 不进入 Main

### 个人页

- 展示脱敏手机号；无改绑入口

### 路由闸门

| token | phone | 去向 |
|-------|-------|------|
| 无 | — | `LoginActivity` |
| 有 | 空 | `BindPhoneActivity`（存量） |
| 有 | 非空 | `MainActivity` |

新用户注册/登录成功后 `phone` 必有值，正常只走 Login ↔ Main。

### 校验与验证码规则

- 大陆手机号：11 位，以 `1` 开头
- 一号一账号（`phone` 唯一）
- 验证码：6 位；5 分钟有效；60s 重发冷却
- Mock 固定 `123456`；DEBUG 发送成功可 Toast 展示
- 场景：`register` / `login` / `bind`，同号不同场景互不串码

## 数据模型

- `UserEntity` / `UserDto` / `UserProfileDto` 增加 `phone`（可空，唯一索引）
- `passwordHash`：保留列以兼容旧行；新用户可为空字符串
- `username`：新用户设为手机号（唯一）；旧用户名密码账号迁移后靠绑号补 `phone`
- Room 升版迁移：旧用户 `phone = null` → 下次冷启动进绑号页
- 会话仍用 `TokenStore`

## API

| 方法 | 路径 | 鉴权 | Body / 说明 |
|------|------|------|-------------|
| POST | `api/auth/sms/send` | `bind` 需登录；`login`/`register` 不需 | `{ phone, scene: "register"\|"login"\|"bind" }` → `{ requestId, expireInSec, debugCode? }` |
| POST | `api/auth/phone/register` | 否 | `{ phone, code, nickname }` → token + user（已含 phone） |
| POST | `api/auth/phone/login` | 否 | `{ phone, code }` → token + user |
| POST | `api/users/me/phone` | 是 | `{ phone, code }` → 更新后的 me（存量绑号） |
| GET | `api/users/me` | 是 | 含 `phone`（完整号；UI 脱敏） |

### 旧接口处理

- `POST api/auth/login`（用户名密码）、原 `POST api/auth/register`（用户名密码）：**UI 不再调用**；Local 实现可保留兼容或返回 410「请使用手机号登录/注册」，实现计划中二选一（推荐直接改为错误提示，避免双轨）。

### 错误约定

| 条件 | 提示 |
|------|------|
| 手机号格式非法 | 前端拦截；API 亦可拒 |
| 验证码错误或过期 | 「验证码错误或已过期」 |
| 发送过频 | 「请稍后再试」 |
| 注册：号已占用 | 「该手机号已注册，请直接登录」 |
| 登录：号未注册 | 「该手机号未注册，请先注册」 |
| 绑号：号已被他人占用 | 「该手机号已绑定其他账号」 |
| `bind` 未登录 | 401 |
| 已有 phone 再绑 | 「已绑定手机号」 |

## 模块与页面

```text
UI (LoginActivity / RegisterActivity / BindPhoneActivity / Splash / Profile)
  → AuthRepository（sendSms / registerByPhone / loginByPhone / bindPhone）
    → DouyinApi → LocalApiDispatcher
      → LocalAuthService + SmsGateway(Mock) → UserDao / TokenStore
```

| 组件 | 职责 |
|------|------|
| `LoginActivity` | 仅验证码登录；入口去注册 |
| `RegisterActivity` | 手机号 + 验证码 + 昵称；成功进 Main |
| `BindPhoneActivity` | 仅存量无 phone 用户 |
| `SplashActivity` | 按 token/phone 路由 |
| `ProfileFragment` | 脱敏手机号 |
| 共用 Helper | 校验、倒计时、验证码输入（登录/注册/绑号复用） |

### 主要文件影响

- **新建：** `BindPhoneActivity`、layout、`SmsGateway`、`MockSmsGateway`、DTO、路由纯函数与单测
- **修改：** `UserEntity`（+migration）、`LocalAuthService`、`LocalApiDispatcher`、`DouyinApi`、`AuthRepository`、`LoginActivity`（去掉密码表单）、`RegisterActivity`（改为手机注册）、`SplashActivity`、`ProfileFragment`、Manifest、strings
- **可删/闲置：** 登录 layout 中密码相关控件；`PasswordHasher` 可暂留供旧数据，新路径不依赖

## 测试与验收

### 单测

1. `MockSmsGateway`：发码、冷却、过期、三场景隔离
2. `LocalAuthService`：手机注册成功；重复号失败；登录成功/未注册失败；存量绑号与冲突
3. 路由：`(hasToken, hasPhone) → Login | Bind | Main`

### 手工

1. 新用户：注册（验证码）→ 直接进首页
2. 登录：已注册号验证码进首页；未注册号提示注册
3. 无密码登录入口
4. 存量：清库前旧用户或手动 `phone=null` → 冷启动进绑号 → 完成后进首页
5. 个人页脱敏；错码 / 占号 / 过频

### 验收标准

- 无账号密码登录入口
- 新用户注册即带 phone，进 Main 不经绑号
- 未绑号（存量）无法进 Main
- 替换 `SmsGateway` 时 API 路径与 DTO 不变

## 后续（非本轮）

- 朋友页完善
- 真实短信网关
- 改绑 / 解绑
- 清理用户名密码遗留列与旧 API
