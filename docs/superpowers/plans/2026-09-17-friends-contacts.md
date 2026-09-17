# 通讯录发现朋友 + 单向关注 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在朋友 Tab 通过本机通讯录发现已注册用户，支持单向关注/取消关注；个人页展示关注数与「我的关注」列表。

**Architecture:** 客户端读通讯录并归一化手机号 → `FriendRepository` → LocalApi；`LocalFollowService` 负责 match（`UserDao.findByPhones`）与 Follow 表 CRUD；UI 为 `FriendsFragment` + `FollowingListActivity` + 可选 `UserProfileActivity`（查看他人资料）。

**Tech Stack:** Java 11、AppCompat、RecyclerView、Room（version 3）、Retrofit LocalApi、`READ_CONTACTS` 运行时权限

**Spec:** `docs/superpowers/specs/2026-09-17-friends-contacts-design.md`

## Global Constraints

- 语言：Java（不引入 Kotlin）
- 无独立后端；`https://app.local/` + LocalApi
- 通讯录**姓名不上传**服务端；match 只传归一化 phones
- 单向关注，无需对方同意；禁止关注自己
- 朋友 Tab 仅发现列表；关注列表在个人页
- 进朋友 Tab 需登录（对齐个人页）
- 本轮不做：私信、好友申请、粉丝列表、推荐算法

---

## File Structure

| 路径 | 职责 |
|------|------|
| `friends/PhoneNormalizer.java` | 号码归一化 |
| `friends/DeviceContactsReader.java` | 读通讯录电话 + 本地姓名 |
| `friends/ContactEntry.java` | `{ normalizedPhone, displayName }` |
| `friends/FriendsFragment.java` + layouts | 发现页 |
| `friends/ContactMatchAdapter.java` | 列表行 |
| `friends/FollowingListActivity.java` | 我的关注 |
| `profile/UserProfileActivity.java` | 查看任意 userId 资料（复用 UserProfileController） |
| `local/db/entity/FollowEntity.java` | follows 表 |
| `local/db/FollowDao.java` | CRUD |
| `local/service/LocalFollowService.java` | match / follow / unfollow / list |
| `repository/FriendRepository.java` | Retrofit 封装 |
| DTO：`MatchPhonesRequest`、`MatchPhonesResult`、`PhoneMatchItem` 等 | JSON |

---

### Task 1: PhoneNormalizer

**Files:**
- Create: `app/src/main/java/com/example/douyin/friends/PhoneNormalizer.java`
- Test: `app/src/test/java/com/example/douyin/friends/PhoneNormalizerTest.java`

**Interfaces:**
- Produces: `public static String normalize(String raw)` → 合法 11 位或 `null`
- Uses: `PhoneValidator.isValidPhone` after cleaning

- [ ] **Step 1: 写失败单测**

```java
assertEquals("13800138000", PhoneNormalizer.normalize("138-0013-8000"));
assertEquals("13800138000", PhoneNormalizer.normalize("+86 13800138000"));
assertEquals("13800138000", PhoneNormalizer.normalize("008613800138000"));
assertEquals("13800138000", PhoneNormalizer.normalize("8613800138000"));
assertNull(PhoneNormalizer.normalize("12345"));
assertNull(PhoneNormalizer.normalize(null));
assertNull(PhoneNormalizer.normalize("12345678901")); // 非 1[3-9]
```

- [ ] **Step 2: 跑测确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests com.example.douyin.friends.PhoneNormalizerTest`

- [ ] **Step 3: 实现**

```java
public final class PhoneNormalizer {
    private PhoneNormalizer() {}
    public static String normalize(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9+]", "");
        // strip +86 / 0086 / leading 86 when leftover is 11 digits
        // then PhoneValidator.isValidPhone
        ...
    }
}
```

注意：先去非数字（保留处理前缀逻辑）；`8613800138000` → `13800138000`。

- [ ] **Step 4: 跑测通过并 Commit**

```bash
git commit -m "feat(friends): add PhoneNormalizer for contact matching"
```

---

### Task 2: FollowEntity + FollowDao + Migration + findByPhones

**Files:**
- Create: `FollowEntity.java`, `FollowDao.java`
- Modify: `AppDatabase.java` — version **3**，`MIGRATION_2_3` 建表 `follows`
- Modify: `UserDao.java` — `List<UserEntity> findByPhones(List<String> phones)`

**FollowEntity:**

```java
@Entity(tableName = "follows",
  indices = {@Index(value = {"follower_id", "followee_id"}, unique = true),
             @Index("followee_id")})
public class FollowEntity {
  @PrimaryKey(autoGenerate = true) public long id;
  @ColumnInfo(name = "follower_id") public long followerId;
  @ColumnInfo(name = "followee_id") public long followeeId;
  @ColumnInfo(name = "created_at") public long createdAt;
}
```

**FollowDao:**

```java
@Insert(onConflict = OnConflictStrategy.IGNORE) long insert(FollowEntity e);
@Query("DELETE FROM follows WHERE follower_id = :followerId AND followee_id = :followeeId") int delete(...);
@Query("SELECT * FROM follows WHERE follower_id = :followerId AND followee_id = :followeeId LIMIT 1") FollowEntity find(...);
@Query("SELECT * FROM follows WHERE follower_id = :followerId ORDER BY created_at DESC") List<FollowEntity> listByFollower(...);
@Query("SELECT COUNT(*) FROM follows WHERE follower_id = :followerId") int countByFollower(...);
```

**UserDao:**

```java
@Query("SELECT * FROM users WHERE phone IN (:phones)")
List<UserEntity> findByPhones(List<String> phones);
```

（空 list 时 Room 行为：调用方勿传空，或 Service 对空直接返回空结果。）

**Migration 2→3:**

```sql
CREATE TABLE IF NOT EXISTS follows (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  follower_id INTEGER NOT NULL,
  followee_id INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS index_follows_follower_id_followee_id ON follows(follower_id, followee_id);
CREATE INDEX IF NOT EXISTS index_follows_followee_id ON follows(followee_id);
```

- [ ] **Step 1: androidTest** `FollowDaoTest` — insert、unique ignore、delete、count；`findByPhones` 命中
- [ ] **Step 2: 实现至 compile 通过**（无设备则 compile androidTest + DONE_WITH_CONCERNS）
- [ ] **Step 3: Commit** `feat(friends): add follows table and findByPhones query`

---

### Task 3: LocalFollowService（match / follow / following）

**Files:**
- Create: `local/service/LocalFollowService.java`
- Modify: `LocalServices.java` — 持有并暴露 `follow()`
- Create DTOs（可本 Task 或 Task 4）:
  - `MatchPhonesRequest` — `List<String> phones`
  - `PhoneMatchItem` — `UserDto user; boolean following;`（构造时 **清空 `user.phone`**）
  - `MatchPhonesResult` — `List<PhoneMatchItem> matches`
- Modify: `UserDto` / `UserProfileDto` — 增加 `int followingCount`（默认 0）
- Modify: `LocalAuthService.getMe` / `LocalVideoService.getUserProfile` — 填入 `followingCount`（需注入 FollowDao 或 LocalFollowService）

**LocalFollowService 方法:**

```java
ApiResponse<MatchPhonesResult> matchPhones(long viewerId, List<String> phones);
ApiResponse<Void> follow(long followerId, long followeeId); // 或 ApiResponse<Object>
ApiResponse<Void> unfollow(long followerId, long followeeId);
ApiResponse<List<UserDto>> listFollowing(long followerId);
int countFollowing(long followerId);
boolean isFollowing(long followerId, long followeeId);
```

**matchPhones 规则:**
1. phones 去重；空 → 空 matches
2. `userDao.findByPhones`
3. 排除 `viewerId`；`user.phone = null` 再放入 DTO
4. `following = followDao.find(...) != null`

**follow:** followee 不存在 → 404；自己 → 400「不能关注自己」；已存在 IGNORE 后 ok

**Test:** androidTest `LocalFollowServiceTest` — 关注/重复/取消/自关；match 排除自己

- [ ] Commit: `feat(friends): implement LocalFollowService match and follow`

---

### Task 4: DouyinApi + Dispatcher + FriendRepository

**Files:**
- Modify: `DouyinApi.java`, `LocalApiDispatcher.java`
- Create: `repository/FriendRepository.java`

**API:**

```java
@POST("api/users/match-phones")
Call<ApiResponse<MatchPhonesResult>> matchPhones(@Body MatchPhonesRequest body);

@POST("api/users/{id}/follow")
Call<ApiResponse<Void>> follow(@Path("id") long userId);

@HTTP(method = "DELETE", path = "api/users/{id}/follow", hasBody = false)
Call<ApiResponse<Void>> unfollow(@Path("id") long userId);
// 若 Retrofit DELETE 不便，可用 @POST("api/users/{id}/unfollow") — 优先 DELETE 与规格一致

@GET("api/users/me/following")
Call<ApiResponse<List<UserDto>>> getMyFollowing();
```

注意：现有 `ApiResponse` 对 `Void` data 可能需用空对象或 `Boolean`；若 `data==null` 被 AuthCallback 判失败，follow 成功时返回 `ApiResponse.ok(Boolean.TRUE)` 或空 `UserDto` 占位。**推荐** `ApiResponse<Boolean>` 且 `ok(true)`。

**Dispatcher:** requireUserId；路由到 `LocalFollowService`。

**FriendRepository:** 复制 `AuthRepository` 回调风格（或共用 errorBody 解析）；方法 `matchPhones` / `follow` / `unfollow` / `getMyFollowing`。

- [ ] Compile + Commit: `feat(friends): wire follow APIs and FriendRepository`

---

### Task 5: Seed 额外用户 + DeviceContactsReader

**Files:**
- Modify: `SeedDataInitializer.java` — 增加用户例如：
  - `13900000001` / 昵称「种子好友一」
  - `13900000002` / 昵称「种子好友二」
  - `13900000003` / 昵称「种子好友三」
  - username = phone；passwordHash = `""`
- Create: `ContactEntry.java` — `String phone; String displayName;`
- Create: `DeviceContactsReader.java`

```java
public final class DeviceContactsReader {
  public List<ContactEntry> loadNormalizedContacts(ContentResolver resolver) {
    // query ContactsContract.CommonDataKinds.Phone
    // for each: normalize phone; keep first displayName per phone
  }
}
```

权限检查在 Fragment，Reader 假定已授权。

- [ ] Commit: `feat(friends): seed contact-match users and DeviceContactsReader`

（Reader 可不单测；Normalizer 已覆盖。）

---

### Task 6: FriendsFragment UI + MainActivity 门禁

**Files:**
- Create: `fragment_friends.xml`、`item_contact_match.xml`
- Create: `FriendsFragment.java`、`ContactMatchAdapter.java`
- Modify: `MainActivity.java` — friends 登录检查；`FriendsFragment` 替换 Placeholder
- Modify: `AndroidManifest.xml` — `READ_CONTACTS`
- Modify: `strings.xml`

**FriendsFragment 流程:**
1. `onResume`/首次：检查权限 → 无则显示 CTA；有则 `loadAndMatch()`
2. `loadAndMatch`：`DeviceContactsReader` → phones → `FriendRepository.matchPhones` → 合并本地 `Map<phone,name>`（match 响应无 phone：Service 应在 `PhoneMatchItem` 增加可选 `matchedPhone` **仅用于客户端映射**，或客户端用「请求 phones × 本地 ContactEntry」在匹配前建立 phone→name，响应用 userId 对不上姓名——

**映射策略（锁定）：**  
`PhoneMatchItem` 增加 `String matchedPhone`（服务端回填该用户实体上的 phone，**仅用于客户端关联通讯录名**；UI **禁止**把 `matchedPhone` 当正文展示，列表只显示 `displayName` + `nickname`）。规格「响应不回完整 phone 给 UI」理解为不展示；传输层可带 `matchedPhone` 供 join。若坚持响应无 phone：客户端对每个 ContactEntry 单独 match 不现实。故 **允许 matchedPhone 字段，Adapter 不绑定到 TextView**。

3. Adapter：关注按钮调 follow/unfollow，乐观更新 `following`
4. 点击行：`UserProfileActivity`（Task 7 若尚未建，先 Toast userId 或本 Task 一并建薄 Activity）

**MainActivity:**

```java
if (itemId == R.id.nav_friends && !authRepository.isLoggedIn()) {
  // same as profile
}
...
if (navItemId == R.id.nav_friends) {
  return new FriendsFragment();
}
```

- [ ] assembleDebug + Commit: `feat(friends): add FriendsFragment contact discovery UI`

---

### Task 7: UserProfileActivity + FollowingList + Profile 关注数

**Files:**
- Create: `UserProfileActivity` + 可选简单 layout（include `layout_user_profile`）；`EXTRA_USER_ID`
- Create: `FollowingListActivity` + `item_following.xml` + Adapter
- Modify: `layout_user_profile.xml` — 增加「关注 N」可点击 TextView（`tv_following_count`）
- Modify: `UserProfileController` — 绑定 followingCount；仅当查看自己时显示入口并启动 FollowingList；他人资料可不显示入口或只读数字
- Manifest 注册两个 Activity
- FriendsFragment 点击 → UserProfileActivity

**FollowingListActivity:** `getMyFollowing` → 列表；每行取消关注

- [ ] assembleDebug + Commit: `feat(friends): following list and profile following count`

---

### Task 8: 测试整理与验收

**Files:**
- 补齐/跑通 unit：`PhoneNormalizerTest`
- androidTest：Follow/LocalFollow（有设备则跑）
- 手工清单对照 spec

- [ ] `.\gradlew.bat :app:testDebugUnitTest` + `assembleDebug`
- [ ] Commit: `test(friends): cover normalizer and follow service paths`

---

## 手工验收清单

1. 未登录点朋友 → 登录页
2. 登录后授权通讯录；本机添加 `13900000001` 等 → 发现列表出现种子用户（显示通讯录名 + 昵称）
3. 关注 ↔ 已关注；个人页关注数变化；「我的关注」可取消
4. 点行进他人资料页
5. 拒权限有空态；匹配空有空态
6. 列表不展示完整手机号

---

## Spec Coverage

| Spec 项 | Task |
|---------|------|
| PhoneNormalizer | 1 |
| Follow 表 / findByPhones | 2 |
| match / follow 业务 | 3 |
| API + Repository | 4 |
| Seed + 读通讯录 | 5 |
| FriendsFragment + 权限 + 登录门 | 6 |
| 个人页关注 + 列表 + 他人页 | 7 |
| 测试 | 8 |

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-17-friends-contacts.md`. Two execution options:

**1. Subagent-Driven (recommended)** — 每 Task 新开子代理，中间复查  

**2. Inline Execution** — 本会话连续执行并设检查点  

Which approach?
