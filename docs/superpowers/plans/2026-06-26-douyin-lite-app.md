# 仿抖音 App 完整实现计划（Java · 客户端本地后端 + Retrofit）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Android 工程上，用 **Java** + 原生 View 体系实现仿抖音 App：纵向视频流、登录注册、评论列表、视频拍摄/剪辑/发布；不建独立后端，业务逻辑在客户端，UI 通过 **Retrofit** 调用本地 API。

**Architecture:** **Retrofit + OkHttp 本地拦截器**——`DouyinApi` 定义 REST 接口，`LocalApiInterceptor` 拦截 `https://app.local/` 并路由到 `LocalAuthService` / `LocalVideoService` / `LocalCommentService`，读写 **Room** 与本地文件。异步采用 **Retrofit `Call.enqueue()` + `Executor` + 主线程 `Handler`**（纯 Java，不引入 Kotlin/Coroutines）。视频播放 `MediaPlayer + TextureView`；拍摄 Camera2 + MediaRecorder；剪辑 MediaExtractor + MediaMuxer。

**Tech Stack:**
- **语言：** Java 11（全部业务与 UI 代码）
- **Android UI：** AppCompatActivity、Fragment、ViewPager2、MediaPlayer、Camera2、RecyclerView、BottomSheetDialogFragment
- **网络：** Retrofit 2、OkHttp 3、**Gson**（JSON，Java 友好）
- **本地存储：** Room + `annotationProcessor`、SharedPreferences、 `filesDir/videos/`
- **异步：** `ExecutorService`、`Handler(Looper.getMainLooper())`、Retrofit Callback
- **不引入：** Kotlin、Coroutines、Compose、ExoPlayer、Glide、独立后端模块

---

## 一、功能范围

（与前一版相同，略）

### 1.2 Retrofit API（DouyinApi.java）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `api/auth/register` | 注册 | 否 |
| POST | `api/auth/login` | 登录 | 否 |
| GET | `api/users/me` | 当前用户 | 是 |
| GET | `api/videos/feed` | Feed 分页 | 可选 |
| POST | `api/videos` | 发布（multipart） | 是 |
| POST | `api/videos/{id}/like` | 点赞 | 是 |
| GET | `api/videos/{id}/comments` | 评论列表 | 否 |
| POST | `api/videos/{id}/comments` | 发表评论 | 是 |
| GET | `api/users/{id}/videos` | 用户作品 | 否 |

---

## 二、整体架构

（架构图不变，实现语言改为 Java）

### 2.1 LocalApiInterceptor（Java）

```java
public class LocalApiInterceptor implements Interceptor {

    private final LocalAuthService authService;
    private final LocalVideoService videoService;
    private final LocalCommentService commentService;
    private final TokenStore tokenStore;
    private final Gson gson;

    public LocalApiInterceptor(LocalAuthService authService,
                               LocalVideoService videoService,
                               LocalCommentService commentService,
                               TokenStore tokenStore,
                               Gson gson) {
        this.authService = authService;
        this.videoService = videoService;
        this.commentService = commentService;
        this.tokenStore = tokenStore;
        this.gson = gson;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!"app.local".equals(request.url().host())) {
            return chain.proceed(request);
        }
        LocalApiResult result = LocalApiDispatcher.dispatch(
                request, authService, videoService, commentService, tokenStore, gson);
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(result.httpCode)
                .message("OK")
                .body(ResponseBody.create(
                        result.json,
                        MediaType.parse("application/json")))
                .build();
    }
}
```

### 2.2 工程目录（Java 源码）

```
app/src/main/java/com/example/douyin/
├── DouyinApp.java
├── MainActivity.java
├── auth/
│   ├── SplashActivity.java
│   ├── LoginActivity.java
│   └── RegisterActivity.java
├── network/
│   ├── DouyinApi.java              # Retrofit 接口
│   ├── RetrofitClient.java
│   ├── LocalApiInterceptor.java
│   ├── LocalApiDispatcher.java
│   ├── ApiResponse.java
│   ├── ApiCallback.java            # 统一回调接口
│   └── TokenStore.java
├── local/
│   ├── service/
│   │   ├── LocalAuthService.java
│   │   ├── LocalVideoService.java
│   │   └── LocalCommentService.java
│   ├── db/
│   │   ├── AppDatabase.java
│   │   ├── UserDao.java
│   │   ├── VideoDao.java
│   │   ├── CommentDao.java
│   │   ├── LikeDao.java
│   │   └── entity/
│   │       ├── UserEntity.java
│   │       ├── VideoEntity.java
│   │       ├── CommentEntity.java
│   │       └── LikeEntity.java
│   └── SeedDataInitializer.java
├── repository/
│   ├── AuthRepository.java
│   ├── VideoRepository.java
│   └── CommentRepository.java
├── util/
│   └── AppExecutors.java           # 线程池 + 主线程 Handler
├── feed/
│   ├── FeedFragment.java
│   ├── FeedPagerAdapter.java
│   └── VideoPageFragment.java
├── comment/
│   ├── CommentBottomSheet.java
│   └── CommentAdapter.java
├── publish/
│   ├── CameraRecordActivity.java
│   ├── VideoEditorActivity.java
│   └── VideoTrimmer.java
├── player/
│   └── VideoPlayerController.java
└── profile/
    └── ProfileFragment.java
```

> **迁移说明：** 删除现有 `MainActivity.kt` 及 Kotlin 测试文件，统一改为 `.java`；Gradle 脚本可保持 `.kts`，但 `app` 模块不再应用 Kotlin 插件。

---

## 三、Java 异步与回调规范

Kotlin `suspend` 在 Java 中按以下模式替代：

### 3.1 AppExecutors

```java
public final class AppExecutors {

    private static final AppExecutors INSTANCE = new AppExecutors();

    private final ExecutorService diskIo = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static AppExecutors get() {
        return INSTANCE;
    }

    public void diskIo(Runnable runnable) {
        diskIo.execute(runnable);
    }

    public void mainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }
}
```

### 3.2 ApiCallback

```java
public interface ApiCallback<T> {
    void onSuccess(T data);
    void onError(int code, String message);
}
```

### 3.3 Repository 调用 Retrofit（Java）

```java
public class AuthRepository {

    private final DouyinApi api;

    public void login(String username, String password, ApiCallback<LoginResult> callback) {
        api.login(new LoginRequest(username, password)).enqueue(new Callback<ApiResponse<LoginResult>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResult>> call,
                                   Response<ApiResponse<LoginResult>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().code == 0) {
                    AppExecutors.get().mainThread(() ->
                            callback.onSuccess(response.body().data));
                } else {
                    AppExecutors.get().mainThread(() ->
                            callback.onError(400, "登录失败"));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginResult>> call, Throwable t) {
                AppExecutors.get().mainThread(() ->
                        callback.onError(-1, t.getMessage()));
            }
        });
    }
}
```

### 3.4 Room 访问

- DAO 方法不使用 `suspend`；在 `AppExecutors.diskIo()` 中调用
- 结果通过 `AppExecutors.mainThread()` 回传 UI

```java
AppExecutors.get().diskIo(() -> {
    List<VideoEntity> list = videoDao.getFeed(offset, size);
    AppExecutors.get().mainThread(() -> adapter.submitList(list));
});
```

---

## 四、数据模型

### 4.1 Room Entity 示例（Java）

```java
@Entity(tableName = "users")
public class UserEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "password_hash")
    public String passwordHash;

    @ColumnInfo(name = "nickname")
    public String nickname;

    @ColumnInfo(name = "avatar_path")
    public String avatarPath;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
```

### 4.2 API DTO（Gson POJO）

```java
public class UserDto {
    public long id;
    public String username;
    public String nickname;
    public String avatarUrl;
    public long createdAt;
}

public class VideoDto {
    public long id;
    public String videoUrl;
    public String coverUrl;
    public UserDto author;
    public String description;
    public int likeCount;
    public int commentCount;
    public boolean isLiked;
}

public class CommentDto {
    public long id;
    public long videoId;
    public UserDto user;
    public String content;
    public long createdAt;
}
```

### 4.3 泛型响应包装

```java
public class ApiResponse<T> {
    public int code;
    public String message;
    public T data;
}
```

Gson 泛型解析（Retrofit）：

```java
public class ApiResponseTypeFactory {
    public static Type typeOf(Class<?> dataClass) {
        return TypeToken.getParameterized(ApiResponse.class, dataClass).getType();
    }
}
```

或使用 `Call<JsonObject>` 在 Interceptor 层已返回标准 JSON，Retrofit 接口直接声明具体类型 + `GsonConverterFactory`。

---

## 五、Retrofit 接口（Java）

```java
public interface DouyinApi {

    @POST("api/auth/register")
    Call<ApiResponse<LoginResult>> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<ApiResponse<LoginResult>> login(@Body LoginRequest body);

    @GET("api/users/me")
    Call<ApiResponse<UserDto>> getMe();

    @GET("api/videos/feed")
    Call<ApiResponse<FeedPage>> getFeed(
            @Query("page") int page,
            @Query("size") int size);

    @Multipart
    @POST("api/videos")
    Call<ApiResponse<VideoDto>> publishVideo(
            @Part MultipartBody.Part video,
            @Part("description") RequestBody description);

    @POST("api/videos/{id}/like")
    Call<ApiResponse<LikeResult>> toggleLike(@Path("id") long videoId);

    @GET("api/videos/{id}/comments")
    Call<ApiResponse<CommentPage>> getComments(
            @Path("id") long videoId,
            @Query("page") int page,
            @Query("size") int size);

    @POST("api/videos/{id}/comments")
    Call<ApiResponse<CommentDto>> postComment(
            @Path("id") long videoId,
            @Body PostCommentRequest body);

    @GET("api/users/{id}/videos")
    Call<ApiResponse<FeedPage>> getUserVideos(
            @Path("id") long userId,
            @Query("page") int page,
            @Query("size") int size);
}
```

```java
public final class RetrofitClient {

    private static DouyinApi api;

    public static DouyinApi getApi(Context context) {
        if (api == null) {
            synchronized (RetrofitClient.class) {
                if (api == null) {
                    api = buildApi(context.getApplicationContext());
                }
            }
        }
        return api;
    }

    private static DouyinApi buildApi(Context appContext) {
        Gson gson = new GsonBuilder().create();
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthHeaderInterceptor(TokenStore.get(appContext)))
                .addInterceptor(new LocalApiInterceptor(
                        LocalServices.auth(appContext),
                        LocalServices.video(appContext),
                        LocalServices.comment(appContext),
                        TokenStore.get(appContext),
                        gson))
                .build();

        return new Retrofit.Builder()
                .baseUrl("https://app.local/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(DouyinApi.class);
    }
}
```

---

## 六、本地 Service 职责

（逻辑不变，全部实现为 `.java` 类）

| Service | 核心方法 |
|---------|----------|
| `LocalAuthService` | `register()` / `login()` / `resolveUserId()` / `getMe()` |
| `LocalVideoService` | `getFeed()` / `publishVideo()` / `toggleLike()` / `getUserVideos()` |
| `LocalCommentService` | `getComments()` / `postComment()` |
| `SeedDataInitializer` | 首次启动复制 assets 样例视频 |

密码哈希：Java `MessageDigest.getInstance("SHA-256")` + salt（不引入 bcrypt 库）。

---

## 七、分阶段实施计划

| 阶段 | 内容 | 预估 |
|------|------|------|
| Phase 0 | Java 工程迁移 + 壳 UI + Splash | 0.5 天 |
| Phase 1 | Room + LocalService + Retrofit/Interceptor | 1.5 天 |
| Phase 2 | 登录/注册 | 1 天 |
| Phase 3 | Feed + 播放 + 点赞 | 2 天 |
| Phase 4 | 评论 BottomSheet | 1 天 |
| Phase 5 | Camera2 拍摄 | 2 天 |
| Phase 6 | VideoTrimmer 剪辑 | 1.5 天 |
| Phase 7 | 发布 + 个人页 | 1 天 |
| Phase 8 | 联调优化 | 1 天 |
| **合计** | | **~11.5 天** |

### Phase 0 补充（Java 迁移）

- [ ] 删除 `MainActivity.kt`，新建 `MainActivity.java`
- [ ] `app/build.gradle.kts` 移除 Kotlin 相关依赖（`core-ktx`、`activity-ktx`）
- [ ] 改用 `androidx.core:core`（非 ktx）或纯 Java API
- [ ] 测试类改为 JUnit4 Java：`ExampleUnitTest.java`

---

## 八、依赖变更（Java 版）

`gradle/libs.versions.toml`：

```toml
[versions]
retrofit = "2.11.0"
okhttp = "4.12.0"
gson = "2.11.0"
room = "2.6.1"
viewpager2 = "1.1.0"
fragment = "1.8.5"
recyclerview = "1.3.2"
lifecycle = "2.8.7"

[libraries]
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-viewpager2 = { group = "androidx.viewpager2", name = "viewpager2", version.ref = "viewpager2" }
androidx-fragment = { group = "androidx.fragment", name = "fragment", version.ref = "fragment" }
androidx-recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
androidx-lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime", version.ref = "lifecycle" }
```

`app/build.gradle.kts`：

```kotlin
android {
    // ...
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

**移除：** `kotlin-android` 插件、`core-ktx`、`activity-ktx`、`moshi-kotlin`、`room-ktx`、`coroutines`、`ksp`。

**Room 注解处理器：** Java 项目使用 `annotationProcessor`；若 AGP 9+ 推荐 `kapt` 仅在混合 Kotlin 时需要，纯 Java 用 `annotationProcessor` 即可。

---

## 九、Java 与 Kotlin 差异对照

| 能力 | Kotlin 方案 | Java 方案 |
|------|-------------|-----------|
| 网络异步 | `suspend` + Retrofit | `Call.enqueue()` + `Callback` |
| 数据库 | `room-ktx` + suspend DAO | DAO 同步方法 + `AppExecutors` |
| JSON | Moshi `@JsonClass` | Gson POJO  public 字段 |
| 单例 | `object RetrofitClient` | 双重检查锁 `RetrofitClient` |
| Fragment 事务 | `commit {}` KTX | `getSupportFragmentManager().beginTransaction()...commit()` |
| 权限请求 | Activity Result KTX | `ActivityResultLauncher`（Java 1.3+ API 支持） |
| 空安全 | 语言级 | `@Nullable` / `@NonNull` + 显式判空 |

---

## 十、测试计划

| 类型 | 内容 |
|------|------|
| 单元测试（Java） | `LocalAuthServiceTest`、`LocalApiDispatcherTest` |
| 仪器测试 | `LoginFlowTest`：注册 → 登录 → Feed |
| 手工测试 | 拍摄/剪辑/发布、进程重启 |

---

## 十一、任务分解

| Task | 内容 |
|------|------|
| 0 | Kotlin → Java 迁移，Gradle 去 Kotlin 依赖 |
| 1 | Phase 0：主题 + Tab + Splash（Java） |
| 2 | Phase 1：Room + LocalService + Retrofit/Interceptor |
| 3 | Phase 2：Login/Register |
| 4 | Phase 3：Feed + 播放 + 点赞 |
| 5 | Phase 4：评论 BottomSheet |
| 6 | Phase 5：Camera2 拍摄 |
| 7 | Phase 6：VideoTrimmer 剪辑 |
| 8 | Phase 7：发布 + Profile |
| 9 | Phase 8：优化 + README |

---

## 十二、Spec 覆盖自检

| 需求 | 方案 |
|------|------|
| **实现语言 Java** | 全部 `.java`，无 Kotlin 源码 |
| 无独立后端 | LocalService + Room |
| Retrofit | `DouyinApi.java` + Gson |
| 登录/注册/评论/拍摄/剪辑 | 同前，Java 实现 |
| 原生 View | AppCompat + XML Layout |

全部覆盖。

---

## 十三、执行方式

Plan 已更新为 **Java** 实现。推荐顺序：**Task 0（迁移）→ Task 2（Room + Retrofit 骨架）**。

1. **Subagent-Driven** — 逐 Task 派发
2. **Inline Execution** — 当前会话连续实现

请选择执行方式。
