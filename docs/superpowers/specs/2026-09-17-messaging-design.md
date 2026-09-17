# 精简私信（与已关注用户对话）设计说明

**日期：** 2026-09-17  
**状态：** 已确认（实现计划已就绪）  
**范围：** 消息 Tab 会话列表；与已关注用户的文本私信；双账号切换可验证收发。不做多媒体、快捷回复、推送。

## 背景

底栏「消息」当前为 `PlaceholderFragment`。关注体系（`FollowEntity` / `FriendRepository` / 「我的关注」）已落地。朋友规格将私信列为非目标；本设计单独实现精简私信。

UI 参考客服/公众号聊天截图，本轮**精简**为：顶栏 + 气泡列表 + 文本输入发送；不做快捷条、富文本蓝链、相机/语音/表情/+。

## 目标

1. 登录用户在消息 Tab 查看与自己相关的会话列表。
2. 对已关注用户发起文本对话；消息持久化，换账号登录后对方可见。
3. 从他人资料、「我的关注」进入聊天页。
4. 打开聊天后清除该会话未读；列表展示未读数。

## 非目标

- 快捷回复、气泡内蓝链、相机/语音/表情/+、认证标、在线状态
- 推送、输入中、已读回执 UI、撤回/删会话
- 互关强制、陌生人私信
- 真实服务端；继续 LocalApi + Room
- 自动回复机器人

## 方案选型

采用 **会话表 + 消息表**：

- `conversations`：一对用户唯一一行（userId 按大小排序），存最后摘要与时间
- `messages`：归属会话，含发送者、正文、已读时间
- 发信前用现有 `FollowDao` 校验「当前用户已关注对方」
- 联调方式：双账号切换（不做自动回复）

备选（未采用）：仅消息表聚合会话（列表/未读更绕）；纯内存假数据（无法双账号验证）。

## 产品规则

### 发信权限

- **发起新会话**：当前用户必须已关注 `toUserId`（单向即可；对方是否关注我无关）。未关注且尚无会话 → 403，Toast「关注后才能发私信」。
- **已有会话续发**：双方任一登录账号均可在该会话继续发文本（即使未回关），便于对方回复与双账号联调。
- 禁止发给自己；内容不能为空。

### 入口

| 入口 | 行为 |
|------|------|
| 底栏「消息」 | 未登录 → Toast + 登录；已登录 → `MessagesFragment` 会话列表 |
| 会话行点击 | 打开 `ChatActivity`（peerUserId） |
| 他人 `UserProfileActivity` | 「私信」→ `ChatActivity`；未关注时发送仍会被拒 |
| `FollowingListActivity` | 行点击或「私信」→ `ChatActivity`（已关注，可直接发） |
| 会话空态 | 文案引导关注；可跳转「我的关注」或朋友 Tab |

### 未读

- 消息默认 `read_at = null`（对接收方而言未读）。
- `GET` 拉取与某 peer 的消息时：将该会话中「对方发来且未读」的消息标记 `read_at = now`。
- 会话列表 `unreadCount`：对方发来且 `read_at IS NULL` 的条数。

## 数据模型

### ConversationEntity（表 `conversations`）

| 字段 | 说明 |
|------|------|
| id | PK 自增 |
| user_low_id | 双方 userId 较小者 |
| user_high_id | 双方 userId 较大者 |
| last_message_preview | 最后一条文本摘要（截断，如 50 字） |
| last_message_at | 最后消息时间戳 |
| created_at | 会话创建时间 |

- 唯一索引 `(user_low_id, user_high_id)`
- Service 层保证 `user_low_id < user_high_id`，且二者均存在

### MessageEntity（表 `messages`）

| 字段 | 说明 |
|------|------|
| id | PK 自增 |
| conversation_id | FK → conversations |
| sender_id | 发送者 userId |
| content | 纯文本 |
| created_at | 发送时间 |
| read_at | 接收方已读时间；未读为 null |

- Room 升版（当前 follows 为 version 3 → **version 4**）加两表及 migration

## API

基址 `https://app.local/`，需登录（Authorization Bearer）。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `api/messages/conversations` | 当前用户会话列表：peer `UserDto`、`lastPreview`、`lastAt`、`unreadCount`；按 `last_message_at` 倒序 |
| GET | `api/messages/conversations/{peerUserId}` | 与该用户消息列表（时间正序）；无会话则 `[]`；副作用：标记对方发来的未读为已读 |
| POST | `api/messages` | body `{ "toUserId": long, "content": string }` → 若无会话则校验关注后创建；若已有会话则直接发 → insert 消息 → 更新会话摘要 → `MessageDto` |

### 错误约定

| 条件 | HTTP | 提示 |
|------|------|------|
| 未登录 | 401 | 跳转登录 |
| 发给自己 / content 空白 | 400 | Toast |
| 未关注且无会话 | 403 | 「关注后才能发私信」 |
| 对方不存在 | 404 | Toast |
| 其它失败 | 5xx/业务码 | 通用失败 Toast |

## 模块与页面

```text
MessagesFragment / ChatActivity
  → MessageRepository
    → DouyinApi → LocalApiDispatcher
      → LocalMessageService
        → ConversationDao / MessageDao / FollowDao / UserDao
```

| 组件 | 职责 |
|------|------|
| `MessagesFragment` | 替换 Placeholder；登录门禁；会话 RecyclerView |
| `ConversationAdapter` | 对端头像/昵称、摘要、时间、未读角标 |
| `ChatActivity` | 顶栏返回+昵称/头像；消息列表；输入+发送 |
| `ChatMessageAdapter` | 左收右发气泡 |
| `ConversationEntity` / `MessageEntity` / Dao / Migration | 持久化 |
| `LocalMessageService` | 业务规则 |
| `MessageRepository` + DTO | 网络封装 |
| Profile / FollowingList | 私信入口 |

### 主要修改

- `MainActivity`：`nav_messages` → `MessagesFragment`；登录门禁（同 friends/profile）
- `AppDatabase` version 4 + migration
- `DouyinApi`、`LocalApiDispatcher`、`LocalServices`
- `UserProfileActivity` / `FollowingListActivity` 入口
- `AndroidManifest` 注册 `ChatActivity`
- `strings.xml`、布局与简单气泡 drawable

### UI 相对截图的取舍

**做：** 浅灰背景、圆形头像、白接收气泡、主题色发送气泡、底栏输入+发送。  
**不做：** 快捷回复横滑、气泡内可点蓝链、相机/语音/表情/+、认证 V、在线文案。

## 错误与空态（UI）

| 场景 | 行为 |
|------|------|
| 未登录进消息 | Toast + 登录 |
| 会话列表空 | 「关注用户后即可发起私信」+ 引导 |
| 未关注发信 | Toast「关注后才能发私信」 |
| 发送中 | 防抖；成功清空输入并滚到底 |
| 请求失败 | Toast |

## 测试与验收

### 单测（LocalMessageService）

1. 无会话且未关注 → 发信失败；已关注 → 成功并创建唯一会话
2. 已有会话时未回关方仍可续发；同一对 userId 不新建第二会话
3. 禁止发给自己；空 content 拒绝
4. 拉取消息后对方来信 `read_at` 被设置；`unreadCount` 归零
5. 会话列表按 `last_message_at` 倒序

### 手工（双账号）

1. A 关注 B → A 发私信 → 登出
2. 登 B → 消息 Tab 见会话与未读 → 打开聊天未读消失 → B 回复
3. 再登 A → 可见 B 的回复
4. 未关注用户点私信/发送 → 被拒
5. 消息 Tab 不再是「敬请期待」

### 验收标准

- 仅文本私信；仅已关注可成功发送
- 同一会话双方账号可见完整历史
- 未读在打开聊天后正确清零
- 资料页 / 关注列表可进入聊天

## 后续（非本轮）

- 快捷回复与富文本
- 图片/语音
- 推送与已读回执 UI
- 真实后端与会话免打扰
