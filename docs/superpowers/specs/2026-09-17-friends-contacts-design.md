# 通讯录发现朋友 + 单向关注 设计说明

**日期：** 2026-09-17  
**状态：** 已确认（实现计划已就绪）  
**范围：** 朋友 Tab 通讯录发现已注册用户；单向关注/取消关注；个人页展示关注数与「我的关注」列表。不做私信、好友申请、粉丝列表。

## 背景

底栏「朋友」当前为 `PlaceholderFragment`。账号体系已支持手机号绑定（`UserEntity.phone` 唯一）。本设计用本机通讯录手机号匹配已注册用户，并建立单向关注关系。

## 目标

1. 登录用户可在朋友 Tab 授权读取通讯录，发现已在 App 注册且绑定手机号的用户。
2. 对匹配用户单向关注 / 取消关注（类抖音，无需对方同意）。
3. 个人页展示关注数，并可打开「我的关注」列表。
4. 通讯录姓名仅留在本机，不上传服务端。

## 非目标

- 消息 / 私信、好友申请、粉丝列表
- 朋友 Tab 内「我的关注」分区（关注列表放个人页）
- 真实服务端；继续 LocalApi + Room
- 推荐「可能认识的人」、复杂排序算法
- 将通讯录联系人姓名写入服务端或日志

## 方案选型

采用 **通讯录发现 + Follow 表**：

- 客户端：`READ_CONTACTS` → `DeviceContactsReader` → `PhoneNormalizer` → 批量 match API
- 服务端（本地）：`UserDao` 按 phone IN 查询；`FollowEntity` 存关注关系
- UI：`FriendsFragment` 仅发现列表；`FollowingListActivity`（或等价页面）挂在个人页

## 产品流程与权限

### 入口

- 底栏「朋友」：与个人页相同，**未登录** → Toast + 跳转登录
- 进入后为发现页（无 Tab 内关注列表）

### 通讯录权限

1. 无权限：说明文案 +「允许访问通讯录」
2. 拒绝：空态提示需要权限，可再次申请
3. 已授权：读取电话号码，去重、归一化后批量匹配

### 发现列表行

- 通讯录备注名（本地）
- App 昵称（不展示完整手机号）
- 按钮：未关注「关注」／已关注「已关注」（再点直接取消）
- 点击行：进入该用户个人页

### 过滤

- 不展示自己
- 仅展示通讯录号码对应的已注册绑号用户
- 无匹配：空态「通讯录中暂无 Douyin 好友」

### 个人页补充

- 展示「关注 N」；点击打开「我的关注」列表（可取消关注）
- 本轮不做粉丝列表

## 数据模型

### FollowEntity（表 `follows`）

| 字段 | 说明 |
|------|------|
| id | PK 自增 |
| follower_id | 关注者 userId |
| followee_id | 被关注者 userId |
| created_at | 时间戳 |

- 唯一索引 `(follower_id, followee_id)`
- Service 层禁止 `follower_id == followee_id`

### User

沿用现有 `phone` 字段与唯一索引；Room 升版加入 follows 表。

### Seed

- 保留 demo `13800138000`
- 再增加 2–3 个带手机号的种子用户，便于本机通讯录写入后联调

## 号码归一化

本地 `PhoneNormalizer`：

1. 去空格、`-`、`()`
2. 去前缀 `+86` / `0086` / 开头 `86`（仅当剩余为 11 位手机号时）
3. 用现有 `PhoneValidator`（`^1[3-9]\d{9}$`）过滤

非法号码丢弃，不参与 match。

## API

基址 `https://app.local/`，需登录（Authorization Bearer）。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `api/users/match-phones` | body `{ "phones": ["138..."] }` → `{ "matches": [ { "user": UserDto, "following": boolean } ] }`；响应**不回**完整 phone（客户端用请求号映射通讯录名） |
| POST | `api/users/{id}/follow` | 关注；已关注幂等成功 |
| DELETE | `api/users/{id}/follow` | 取消关注 |
| GET | `api/users/me/following` | 我的关注用户列表 |
| GET | `api/users/me` / profile | 增加 `followingCount`（`followerCount` 本轮可不做或恒 0） |

### 错误约定

| 条件 | 提示 |
|------|------|
| 未登录 | 401 |
| 关注自己 | 400 |
| 目标用户不存在 | 404 |
| 无通讯录权限 | 仅客户端空态 |

## 模块与页面

```text
FriendsFragment / FollowingListActivity
  → FriendRepository
    → DouyinApi → LocalApiDispatcher
      → LocalFollowService (+ match via UserDao)
        → FollowDao / UserDao
DeviceContactsReader + PhoneNormalizer（客户端 only）
```

| 组件 | 职责 |
|------|------|
| `FriendsFragment` | 替换 Placeholder；权限 + 发现列表 |
| `ContactMatchAdapter` | 行 UI + 关注按钮 |
| `DeviceContactsReader` | ContentResolver 读电话 |
| `PhoneNormalizer` | 归一化 |
| `FollowingListActivity` | 个人页关注列表 |
| `FollowEntity` / `FollowDao` / Migration | 持久化 |
| `FriendRepository` + DTO | 网络封装 |

### 主要修改

- `MainActivity`：`nav_friends` → `FriendsFragment`；登录门禁
- `AndroidManifest`：`READ_CONTACTS`
- `AppDatabase`、`DouyinApi`、`LocalApiDispatcher`、`LocalServices`
- Profile UI / DTO：`followingCount` + 入口
- `SeedDataInitializer`、`strings.xml`

## 错误与空态（UI）

| 场景 | 行为 |
|------|------|
| 未登录进朋友 | Toast + 登录 |
| 无权限 | 引导授权 |
| 无有效手机号 | 「未找到可匹配的手机号」 |
| 匹配为空 | 「通讯录中暂无 Douyin 好友」 |
| 请求失败 | Toast 文案 |
| 重复关注 | 幂等，按钮已关注 |

## 测试与验收

### 单测

1. `PhoneNormalizer`：`+86`、空格、横线 → 合法号；非法丢弃
2. Follow：关注、重复、取消、禁自关
3. Match：phones → 对应用户；排除当前用户

### 手工

1. 登录 → 朋友 → 授权 → 本机通讯录含种子号时能匹配
2. 关注 / 取消；个人页关注数与「我的关注」一致
3. 拒权限空态；未登录跳登录
4. 点击行进个人页

### 验收标准

- 朋友 Tab = 通讯录发现 + 关注操作，无私信
- 通讯录姓名不离开本机
- 关注单向可取消
- 个人页可查看并管理「我的关注」

## 后续（非本轮）

- 粉丝列表、互关标识强化
- 消息 Tab / 私信
- 朋友 Tab 内关注分区
- 真实后端与隐私合规强化
