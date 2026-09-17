# 精简私信 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消息 Tab 展示会话列表；与已关注用户文本私信；双账号切换可验证收发与未读。

**Architecture:** `ConversationEntity` + `MessageEntity`（Room v4）→ `LocalMessageService`（关注校验 / upsert 会话 / 已读）→ LocalApi → `MessageRepository` → `MessagesFragment` / `ChatActivity`；入口挂在他人资料与「我的关注」。

**Tech Stack:** Java 11、AppCompat、RecyclerView、Room、Retrofit LocalApi（`https://app.local/`）

**Spec:** `docs/superpowers/specs/2026-09-17-messaging-design.md`

## Global Constraints

- 语言：Java（不引入 Kotlin）
- 无独立后端；继续 LocalApi + Room
- 发起新会话须已关注对方；已有会话任一方可续发
- 仅文本；不做图片/语音/快捷回复/推送/自动回复
- 消息 Tab 需登录（对齐朋友/我）
- 包名新建 `com.example.douyin.message`

---

## File Structure

| 路径 | 职责 |
|------|------|
| `local/db/entity/ConversationEntity.java` | conversations 表 |
| `local/db/entity/MessageEntity.java` | messages 表 |
| `local/db/ConversationDao.java` | 会话 CRUD / 按用户列表 |
| `local/db/MessageDao.java` | 消息 CRUD / 未读 |
| `local/db/AppDatabase.java` | version 4 + `MIGRATION_3_4` |
| `local/service/LocalMessageService.java` | 业务规则 |
| `network/model/SendMessageRequest.java` | `{ toUserId, content }` |
| `network/model/MessageDto.java` | 消息 DTO |
| `network/model/ConversationDto.java` | 会话列表项 DTO |
| `network/DouyinApi.java` | 3 个消息接口 |
| `network/LocalApiDispatcher.java` | 路由 |
| `local/LocalServices.java` | 注入 messageService |
| `network/LocalApiInterceptor.java` | 传入 messageService |
| `repository/MessageRepository.java` | Retrofit 封装 |
| `message/MessagesFragment.java` | 会话列表 |
| `message/ConversationAdapter.java` | 列表行 |
| `message/ChatActivity.java` | 聊天页 |
| `message/ChatMessageAdapter.java` | 气泡 |
| layouts / drawables / strings | UI |
| `MainActivity` / Profile / FollowingList / Manifest | 入口与门禁 |
| `androidTest/.../LocalMessageServiceTest.java` | 服务单测 |

---

### Task 1: Conversation + Message 表与 Migration

**Files:**
- Create: `app/src/main/java/com/example/douyin/local/db/entity/ConversationEntity.java`
- Create: `app/src/main/java/com/example/douyin/local/db/entity/MessageEntity.java`
- Create: `app/src/main/java/com/example/douyin/local/db/ConversationDao.java`
- Create: `app/src/main/java/com/example/douyin/local/db/MessageDao.java`
- Modify: `app/src/main/java/com/example/douyin/local/db/AppDatabase.java` — version **4**，entities 加入两表，注册 `MIGRATION_3_4`
- Test: `app/src/androidTest/java/com/example/douyin/local/db/MessageDaoTest.java`（可选轻量：insert + findByPair）

**Interfaces:**
- Produces:
  - `ConversationDao.findByPair(low, high)` / `insert` / `update` / `listForUser(userId)`
  - `MessageDao.insert` / `listByConversation(convId)` / `markRead(convId, viewerId, readAt)` / `countUnread(convId, viewerId)`
- Consumes: 现有 Room / AppDatabase 模式

- [ ] **Step 1: 写 Entity**

```java
@Entity(tableName = "conversations",
        indices = {@Index(value = {"user_low_id", "user_high_id"}, unique = true)})
public class ConversationEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "user_low_id") public long userLowId;
    @ColumnInfo(name = "user_high_id") public long userHighId;
    @ColumnInfo(name = "last_message_preview") public String lastMessagePreview;
    @ColumnInfo(name = "last_message_at") public long lastMessageAt;
    @ColumnInfo(name = "created_at") public long createdAt;
}

@Entity(tableName = "messages",
        indices = {@Index("conversation_id"), @Index("sender_id")})
public class MessageEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    @ColumnInfo(name = "conversation_id") public long conversationId;
    @ColumnInfo(name = "sender_id") public long senderId;
    @ColumnInfo(name = "content") public String content;
    @ColumnInfo(name = "created_at") public long createdAt;
    @ColumnInfo(name = "read_at") public Long readAt; // nullable
}
```

- [ ] **Step 2: 写 Dao**

```java
@Dao
public interface ConversationDao {
    @Insert long insert(ConversationEntity e);
    @Update int update(ConversationEntity e);
    @Query("SELECT * FROM conversations WHERE user_low_id = :low AND user_high_id = :high LIMIT 1")
    ConversationEntity findByPair(long low, long high);
    @Query("SELECT * FROM conversations WHERE user_low_id = :userId OR user_high_id = :userId ORDER BY last_message_at DESC")
    List<ConversationEntity> listForUser(long userId);
}

@Dao
public interface MessageDao {
    @Insert long insert(MessageEntity e);
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    List<MessageEntity> listByConversation(long conversationId);
    @Query("UPDATE messages SET read_at = :readAt WHERE conversation_id = :conversationId AND sender_id != :viewerId AND read_at IS NULL")
    int markRead(long conversationId, long viewerId, long readAt);
    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :conversationId AND sender_id != :viewerId AND read_at IS NULL")
    int countUnread(long conversationId, long viewerId);
}
```

- [ ] **Step 3: AppDatabase version 4**

- `@Database(entities = { ..., ConversationEntity.class, MessageEntity.class }, version = 4, ...)`
- 增加 `conversationDao()` / `messageDao()`
- `MIGRATION_3_4`：`CREATE TABLE conversations (...)` + unique index；`CREATE TABLE messages (...)` + indexes
- `.addMigrations(..., MIGRATION_3_4)`
- `createInMemory` 无需改 migrations（已 createFrom scratch）

- [ ] **Step 4: 编译确认**

Run: `.\gradlew.bat :app:compileDebugJavaWithJavac`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/douyin/local/db/
git commit -m "feat(messages): add conversations/messages tables (Room v4)"
```

---

### Task 2: LocalMessageService + 仪器测试

**Files:**
- Create: `app/src/main/java/com/example/douyin/local/service/LocalMessageService.java`
- Test: `app/src/androidTest/java/com/example/douyin/local/service/LocalMessageServiceTest.java`

**Interfaces:**
- Consumes: `UserDao`, `FollowDao`, `ConversationDao`, `MessageDao`；`EntityMapper.toUserDto`
- Produces:
  - `ApiResponse<MessageDto> send(long fromUserId, long toUserId, String content)`
  - `ApiResponse<List<ConversationDto>> listConversations(long userId)`
  - `ApiResponse<List<MessageDto>> listMessages(long viewerId, long peerUserId)` // 副作用 markRead

**DTO 字段（本 Task 可先写在 network.model，或与 Task 3 同 commit；推荐本 Task 一并创建）：**

```java
public class SendMessageRequest {
    public long toUserId;
    public String content;
}
public class MessageDto {
    public long id;
    public long conversationId;
    public long senderId;
    public String content;
    public long createdAt;
    public Long readAt;
}
public class ConversationDto {
    public long conversationId;
    public UserDto peer;
    public String lastPreview;
    public long lastAt;
    public int unreadCount;
}
```

- [ ] **Step 1: 写失败测试（先于实现）**

```java
@RunWith(AndroidJUnit4.class)
public class LocalMessageServiceTest {
    // setUp: in-memory db + LocalMessageService(userDao, followDao, conversationDao, messageDao)
    // insert users A,B,C; A follows B only

    @Test
    public void send_requiresFollow_whenNoConversation() {
        assertEquals(403, messageService.send(userA, userC, "hi").code); // A not following C
        assertEquals(0, messageService.send(userA, userB, "hello").code);
    }

    @Test
    public void send_allowsReplyWithoutFollow_whenConversationExists() {
        assertEquals(0, messageService.send(userA, userB, "hi").code);
        // B does not follow A
        assertEquals(0, messageService.send(userB, userA, "reply").code);
    }

    @Test
    public void send_rejectsSelfAndBlank() {
        assertEquals(400, messageService.send(userA, userA, "x").code);
        assertEquals(400, messageService.send(userA, userB, "  ").code);
    }

    @Test
    public void samePair_singleConversation() {
        messageService.send(userA, userB, "1");
        messageService.send(userA, userB, "2");
        assertEquals(1, db.conversationDao().listForUser(userA).size());
    }

    @Test
    public void listMessages_marksRead_andClearsUnread() {
        messageService.send(userA, userB, "hi");
        ApiResponse<List<ConversationDto>> before = messageService.listConversations(userB);
        assertEquals(1, before.data.get(0).unreadCount);
        messageService.listMessages(userB, userA);
        ApiResponse<List<ConversationDto>> after = messageService.listConversations(userB);
        assertEquals(0, after.data.get(0).unreadCount);
    }

    @Test
    public void listConversations_orderedByLastAtDesc() {
        messageService.send(userA, userB, "first");
        // insert follow A->C then message later
        followService.follow(userA, userC);
        messageService.send(userA, userC, "second");
        List<ConversationDto> list = messageService.listConversations(userA).data;
        assertEquals(userC, list.get(0).peer.id);
        assertEquals(userB, list.get(1).peer.id);
    }
}
```

- [ ] **Step 2: 跑测确认失败**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.douyin.local.service.LocalMessageServiceTest`

Expected: 编译失败或测试失败（类不存在 / 方法不存在）

- [ ] **Step 3: 实现 LocalMessageService**

核心逻辑：

```java
public ApiResponse<MessageDto> send(long fromUserId, long toUserId, String content) {
    if (fromUserId == toUserId) return ApiResponse.error(400, "不能发给自己");
    if (content == null || content.trim().isEmpty()) return ApiResponse.error(400, "消息不能为空");
    content = content.trim();
    UserEntity peer = userDao.findById(toUserId);
    if (peer == null) return ApiResponse.error(404, "用户不存在");

    long low = Math.min(fromUserId, toUserId);
    long high = Math.max(fromUserId, toUserId);
    ConversationEntity conv = conversationDao.findByPair(low, high);
    if (conv == null) {
        if (followDao.find(fromUserId, toUserId) == null) {
            return ApiResponse.error(403, "关注后才能发私信");
        }
        conv = new ConversationEntity();
        conv.userLowId = low;
        conv.userHighId = high;
        conv.createdAt = System.currentTimeMillis();
        conv.lastMessageAt = conv.createdAt;
        conv.lastMessagePreview = preview(content);
        conv.id = conversationDao.insert(conv);
    }
    long now = System.currentTimeMillis();
    MessageEntity msg = new MessageEntity();
    msg.conversationId = conv.id;
    msg.senderId = fromUserId;
    msg.content = content;
    msg.createdAt = now;
    msg.readAt = null;
    msg.id = messageDao.insert(msg);

    conv.lastMessageAt = now;
    conv.lastMessagePreview = preview(content); // max 50 chars
    conversationDao.update(conv);
    return ApiResponse.ok(toMessageDto(msg));
}

public ApiResponse<List<MessageDto>> listMessages(long viewerId, long peerUserId) {
    // find pair; if null return ok(emptyList)
    // markRead(conv.id, viewerId, now)
    // map listByConversation → MessageDto
}

public ApiResponse<List<ConversationDto>> listConversations(long userId) {
    // listForUser → for each, peer = other id, unread = countUnread, UserDto via EntityMapper (phone=null)
}
```

- [ ] **Step 4: 跑测通过**

Run: 同 Step 2，Expected: PASS

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(messages): LocalMessageService with follow-gated send and read receipts"
```

---

### Task 3: API 路由 + Repository

**Files:**
- Modify: `DouyinApi.java` — 增加 3 方法
- Modify: `LocalApiDispatcher.java` — 增加 message 路由；`dispatch` 签名加 `LocalMessageService`
- Modify: `LocalApiInterceptor.java` — 传入 `LocalServices.messages()`
- Modify: `LocalServices.java` — init / getter / `resetForTests`
- Create: `repository/MessageRepository.java`
- DTOs：若 Task 2 未建则本 Task 创建

**Interfaces:**
- Consumes: `LocalMessageService` 方法签名（Task 2）
- Produces: `MessageRepository.listConversations / listMessages(peerId) / send(toUserId, content)` + `ApiCallback`

- [ ] **Step 1: DouyinApi**

```java
@GET("api/messages/conversations")
Call<ApiResponse<List<ConversationDto>>> getConversations();

@GET("api/messages/conversations/{peerUserId}")
Call<ApiResponse<List<MessageDto>>> getMessages(@Path("peerUserId") long peerUserId);

@POST("api/messages")
Call<ApiResponse<MessageDto>> sendMessage(@Body SendMessageRequest body);
```

- [ ] **Step 2: Dispatcher 路由（插在 404 之前）**

```java
// GET /api/messages/conversations
// GET /api/messages/conversations/{peerId}  — parsePathLong(path, "/api/messages/conversations/", "")
// POST /api/messages  — gson.fromJson(body, SendMessageRequest.class)
```

均 `requireUserId`；失败 401。调用 `messageService.*` → `LocalApiResult.from(...)`。

注意：更新所有 `dispatch(...)` 调用处（Interceptor）补参。

- [ ] **Step 3: LocalServices**

```java
messageService = new LocalMessageService(
    database.userDao(), database.followDao(),
    database.conversationDao(), database.messageDao());
public static LocalMessageService messages() { ... }
// resetForTests 清空 messageService
```

- [ ] **Step 4: MessageRepository**

照抄 `FriendRepository` 的 `FriendCallback` 模式（可内嵌 `MessageCallback`）：

```java
public void listConversations(ApiCallback<List<ConversationDto>> cb);
public void listMessages(long peerUserId, ApiCallback<List<MessageDto>> cb);
public void send(long toUserId, String content, ApiCallback<MessageDto> cb);
```

- [ ] **Step 5: 编译**

Run: `.\gradlew.bat :app:compileDebugJavaWithJavac`

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(messages): wire LocalApi endpoints and MessageRepository"
```

---

### Task 4: MessagesFragment 会话列表 + 登录门禁

**Files:**
- Create: `message/MessagesFragment.java`
- Create: `message/ConversationAdapter.java`
- Create: `res/layout/fragment_messages.xml`
- Create: `res/layout/item_conversation.xml`
- Modify: `MainActivity.java` — `nav_messages` → `MessagesFragment`；登录门禁加入 `nav_messages`
- Modify: `res/values/strings.xml`

**Interfaces:**
- Consumes: `MessageRepository.listConversations`；点击行 → `ChatActivity`（Task 5 可先用 Intent 占位，或本 Task 与 Task 5 顺序：先做 ChatActivity 空壳再接列表）
- Produces: 可展示的会话列表 UI

- [ ] **Step 1: strings**

```xml
<string name="messages_title">消息</string>
<string name="messages_empty">关注用户后即可发起私信</string>
<string name="messages_go_following">查看我的关注</string>
<string name="messages_dm_need_follow">关注后才能发私信</string>
<string name="messages_send">发送</string>
<string name="messages_input_hint">输入消息…</string>
<string name="action_dm">私信</string>
```

- [ ] **Step 2: fragment_messages.xml**

- 标题栏「消息」
- `RecyclerView` + 空态 `TextView` + 可选按钮跳转 `FollowingListActivity`
- `ProgressBar`

- [ ] **Step 3: ConversationAdapter**

行：圆形头像（可用颜色占位或 Glide/现有头像加载若有）、昵称、`lastPreview`、相对时间或简单格式化、`unreadCount` 角标（0 则 GONE）

- [ ] **Step 4: MessagesFragment**

- `onResume` 刷新列表（从聊天返回后未读更新）
- `MessageRepository.listConversations`
- 空态显示；失败 Toast
- 行点击：`ChatActivity.start(context, peer.id)`（Task 5 提供）

- [ ] **Step 5: MainActivity**

```java
// login gate:
if ((itemId == R.id.nav_profile || itemId == R.id.nav_friends || itemId == R.id.nav_messages)
        && !authRepository.isLoggedIn()) { ... }

// createFragment:
if (navItemId == R.id.nav_messages) return new MessagesFragment();
```

缓存 tag 若存在（如 `tag_messages`）按现有 feed/friends 模式加一项。

- [ ] **Step 6: 安装冒烟**

Run: `.\gradlew.bat :app:installDebug`  
手动：登录 → 消息 Tab 不再「敬请期待」，空态可见。

- [ ] **Step 7: Commit**

```bash
git commit -m "feat(messages): MessagesFragment conversation list with login gate"
```

---

### Task 5: ChatActivity 聊天页

**Files:**
- Create: `message/ChatActivity.java`
- Create: `message/ChatMessageAdapter.java`
- Create: `res/layout/activity_chat.xml`
- Create: `res/layout/item_chat_message_in.xml` / `item_chat_message_out.xml`（或单 layout + 左右约束）
- Create: `res/drawable/bg_chat_bubble_in.xml`（白圆角）
- Create: `res/drawable/bg_chat_bubble_out.xml`（`douyin_red` 圆角）
- Modify: `AndroidManifest.xml` — 注册 Activity

**Interfaces:**
- Consumes: `MessageRepository.listMessages(peerId)` / `send(peerId, text)`；`EXTRA_PEER_USER_ID`
- Produces: `public static void start(Context, long peerUserId)`

- [ ] **Step 1: Manifest**

```xml
<activity
    android:name=".message.ChatActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:windowSoftInputMode="adjustResize" />
```

- [ ] **Step 2: activity_chat.xml**

- 顶栏：返回、对端头像、昵称（可先用 peerId 拉取：复用 `UserRepository` 若有 getUser；否则从 Intent 额外传 nickname，或 listMessages 前用 following/profile 已有数据。**推荐：** Intent 传 `EXTRA_PEER_USER_ID`，顶栏用 `UserRepository`/`DouyinApi` 已有用户资料接口；若无单独 getUser，则在 `ConversationDto.peer` / Following 传入 `EXTRA_PEER_NICKNAME` + `EXTRA_PEER_AVATAR` 可选，缺省显示「用户」。）
- 中部：`RecyclerView` 消息
- 底部：`EditText` +「发送」按钮（无相机/语音/表情）

**顶栏用户信息最小方案：** `ChatActivity` extras：`EXTRA_PEER_USER_ID`（必填）、`EXTRA_PEER_NICKNAME`（可选）。打开时 `listMessages`；昵称优先 extra，否则会话列表里已有则传入。

- [ ] **Step 3: ChatMessageAdapter**

- `getItemViewType`：`senderId == myUserId` → OUT，否则 IN
- 需要当前用户 id：`AuthRepository` / `TokenStore` 现有方法（与项目一致，查找 `getCurrentUserId` 或 profile me）
- 气泡绑定 `content`；可选小字时间

- [ ] **Step 4: ChatActivity 逻辑**

```java
onCreate: loadMessages()
sendBtn: debounce → repository.send → onSuccess append + clear input + scrollToBottom
onError 403: AppToast R.string.messages_dm_need_follow
```

获取 `myUserId`：复用项目内已有方式（如 `AuthRepository.getUserId()` — 实现时搜索确认）。

- [ ] **Step 5: 编译安装，单账号自发自测不可（禁发给自己）；用关注用户发一条可见气泡**

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(messages): ChatActivity text bubbles and send"
```

---

### Task 6: 私信入口（资料页 + 我的关注）

**Files:**
- Modify: `res/layout/layout_user_profile.xml` — 他人可见「私信」按钮（`btn_dm`）
- Modify: `profile/UserProfileController.java` — 非 self 时显示；点击 `ChatActivity.start`
- Modify: `friends/FollowingListActivity.java` — 行点击或按钮进聊天（建议整行点击进 `ChatActivity`，保留取消关注按钮）

**Interfaces:**
- Consumes: `ChatActivity.start(Context, peerUserId, nickname)`
- Produces: 两处入口可达聊天页

- [ ] **Step 1: layout 加按钮**

在统计区下方加：

```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_dm"
    android:text="@string/action_dm"
    android:visibility="gone"
    ... />
```

- [ ] **Step 2: Controller**

```java
btnDm = root.findViewById(R.id.btn_dm);
btnDm.setVisibility(showLogout ? View.GONE : View.VISIBLE); // 他人页 showLogout=false
btnDm.setOnClickListener(v -> ChatActivity.start(root.getContext(), userId, currentNickname));
```

- [ ] **Step 3: FollowingListActivity**

行点击（非取关按钮）→ `ChatActivity.start(this, user.id, user.nickname)`

- [ ] **Step 4: 空态引导**

`MessagesFragment` 空态按钮 → `FollowingListActivity`

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(messages): add DM entry from profile and following list"
```

---

### Task 7: 双账号手工验收 + Spec 状态

**Files:**
- Modify: `docs/superpowers/specs/2026-09-17-messaging-design.md` — 状态改为「已确认（实现计划已就绪/已实现）」视完成情况

- [ ] **Step 1: 手工清单（必须全部勾选）**

1. 登录 A → 关注 B → 从关注列表进聊天发「你好」
2. 退出 → 登录 B → 消息 Tab 有会话、未读 ≥1 → 打开聊天未读清零 → 回复「收到」
3. 再登 A → 可见「收到」
4. A 对未关注用户 C：资料页点私信后发送 → Toast「关注后才能发私信」
5. 未登录点消息 Tab → 登录页
6. 消息 Tab 无「敬请期待」

- [ ] **Step 2: 回归仪器测试**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.douyin.local.service.LocalMessageServiceTest`

- [ ] **Step 3: 更新 spec 状态行并 Commit（若有文档改动）**

```bash
git commit -m "docs: mark messaging design implemented"
```

---

## Self-Review (plan vs spec)

| Spec 要求 | Task |
|-----------|------|
| conversations + messages / Room v4 | Task 1 |
| 关注校验发起 / 已有会话可续发 | Task 2 |
| 未读 markRead / unreadCount | Task 2 |
| GET/POST API + Repository | Task 3 |
| MessagesFragment + 登录门禁 | Task 4 |
| ChatActivity 气泡文本 | Task 5 |
| 资料 / 关注列表入口 | Task 6 |
| 双账号验收 | Task 7 |
| 不做多媒体/快捷回复 | Global Constraints + UI 无这些控件 |

无 TBD 占位；DTO / Service 方法名在 Task 2–3 一致。
