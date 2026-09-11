# Jetpack MVVM + ExoPlayer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在纯 Java 仿抖音工程上引入 ViewModel + LiveData + ViewBinding、Media3 ExoPlayer、Preferences DataStore（Rx 桥接），完成 Feed/Auth/Comment/Profile 的 MVVM 改造，且不引入 Navigation / Hilt / CameraX / Paging / Kotlin。

**Architecture:** UI（ViewBinding）observe ViewModel 的 LiveData；Repository 签名保持 `ApiCallback`；`VideoPlayerController` 留在 `VideoPageFragment` 并由 `DefaultLifecycleObserver` 绑定 `viewLifecycleOwner`；`TokenStore` 用 DataStore 持久化 + 内存缓存供拦截器同步读。

**Tech Stack:** Java 11、AndroidX Lifecycle 2.8.7、ViewBinding、Media3 1.11.0、DataStore Preferences 1.2.1 + RxJava3 桥接、既有 Retrofit/Room/ViewPager2。

**Spec:** `docs/superpowers/specs/2026-09-11-jetpack-mvvm-exoplayer-design.md`

## Global Constraints

- 语言：纯 Java 11；不新增 `.kt` 源文件
- 不做：Navigation、Hilt、CameraX、Paging、Compose、Coroutines 业务代码
- 导航：保留手动 Fragment / Activity
- DI：手动 `new Repository` + `ViewModelProvider.Factory`
- Player：不进 ViewModel；由页面持有
- DataStore：拦截器禁止 `blockingGet`；只读内存缓存
- 依赖版本钉死：Media3 `1.11.0`；DataStore `1.2.1`；Lifecycle 沿用 `2.8.7`

---

## File Structure

**Create:**

- `app/src/main/java/com/example/douyin/util/SingleLiveEvent.java`
- `app/src/main/java/com/example/douyin/feed/FeedUiState.java`
- `app/src/main/java/com/example/douyin/feed/FeedViewModel.java`
- `app/src/main/java/com/example/douyin/auth/LoginUiState.java`
- `app/src/main/java/com/example/douyin/auth/LoginViewModel.java`
- `app/src/main/java/com/example/douyin/auth/RegisterUiState.java`
- `app/src/main/java/com/example/douyin/auth/RegisterViewModel.java`
- `app/src/main/java/com/example/douyin/comment/CommentUiState.java`
- `app/src/main/java/com/example/douyin/comment/CommentViewModel.java`
- `app/src/main/java/com/example/douyin/profile/ProfileUiState.java`
- `app/src/main/java/com/example/douyin/profile/ProfileViewModel.java`
- `app/src/test/java/com/example/douyin/feed/FeedViewModelTest.java`
- `app/src/test/java/com/example/douyin/network/TokenStoreMemoryCacheTest.java`

**Modify:**

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `player/VideoPlayerController.java`
- `feed/FeedFragment.java`、`feed/VideoPageFragment.java`
- `network/TokenStore.java`、`DouyinApp.java`、`auth/SplashActivity.java`
- `auth/LoginActivity.java`、`auth/RegisterActivity.java`
- `comment/CommentBottomSheet.java`
- `profile/ProfileFragment.java`（及按需 `UserProfileController.java`）
- `publish/PublishActivity.java`（ViewBinding 轻量）

---

### Task 1: P0 依赖与 ViewBinding

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**

- Produces: 工程可解析 `lifecycle-viewmodel` / `livedata` / Media3 / DataStore Rx / ViewBinding / `core-testing`

- [ ] **Step 1: 更新 version catalog**

在 `gradle/libs.versions.toml` 的 `[versions]` 增加：

```toml
media3 = "1.11.0"
datastore = "1.2.1"
archCore = "2.2.0"
```

在 `[libraries]` 增加（保留既有 `androidx-lifecycle-runtime`）：

```toml
androidx-lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel", version.ref = "lifecycle" }
androidx-lifecycle-livedata = { group = "androidx.lifecycle", name = "lifecycle-livedata", version.ref = "lifecycle" }
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-datastore-preferences-rxjava3 = { group = "androidx.datastore", name = "datastore-preferences-rxjava3", version.ref = "datastore" }
androidx-arch-core-testing = { group = "androidx.arch.core", name = "core-testing", version.ref = "archCore" }
```

- [ ] **Step 2: 启用 ViewBinding 并声明依赖**

`app/build.gradle.kts` 的 `buildFeatures`：

```kotlin
buildFeatures {
    buildConfig = true
    viewBinding = true
}
```

`dependencies` 增加：

```kotlin
implementation(libs.androidx.lifecycle.viewmodel)
implementation(libs.androidx.lifecycle.livedata)
implementation(libs.androidx.media3.exoplayer)
implementation(libs.androidx.datastore.preferences)
implementation(libs.androidx.datastore.preferences.rxjava3)
testImplementation(libs.androidx.arch.core.testing)
```

（若 catalog 访问名与生成 accessor 不一致，按 IDE 提示调整为实际 `libs.*` 名称。）

- [ ] **Step 3: 同步并编译**

Run: `./gradlew :app:compileDebugJavaWithJavac`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add Jetpack ViewModel, Media3, DataStore, ViewBinding"
```

---

### Task 2: SingleLiveEvent 工具类

**Files:**

- Create: `app/src/main/java/com/example/douyin/util/SingleLiveEvent.java`

**Interfaces:**

- Produces: `SingleLiveEvent<T>` — 仅新 observer 未消费的事件会再投递一次

- [ ] **Step 1: 实现 SingleLiveEvent**

```java
package com.example.douyin.util;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于 Toast / 导航等一次性事件，避免配置变更后重复触发。
 */
public final class SingleLiveEvent<T> extends MutableLiveData<T> {

    private final AtomicBoolean pending = new AtomicBoolean(false);

    @MainThread
    @Override
    public void observe(LifecycleOwner owner, final Observer<? super T> observer) {
        super.observe(owner, t -> {
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t);
            }
        });
    }

    @MainThread
    @Override
    public void setValue(@Nullable T value) {
        pending.set(true);
        super.setValue(value);
    }

    public void call() {
        setValue(null);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/douyin/util/SingleLiveEvent.java
git commit -m "feat: add SingleLiveEvent for one-shot UI events"
```

---

### Task 3: ExoPlayer 重写 VideoPlayerController

**Files:**

- Modify: `app/src/main/java/com/example/douyin/player/VideoPlayerController.java`
- Keep using: `app/src/main/java/com/example/douyin/player/VideoTransformHelper.java`

**Interfaces:**

- Consumes: Media3 `ExoPlayer`、`TextureView`、`LifecycleOwner`（由 Fragment 注册）
- Produces: 对外 API 保持 `setVideoUrl` / `play` / `pause` / `togglePlayPause` / `isPlaying` / `release`；并实现 `DefaultLifecycleObserver`

- [ ] **Step 1: 用 ExoPlayer 替换 MediaPlayer 实现**

完整替换 `VideoPlayerController.java` 为：

```java
package com.example.douyin.player;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

public class VideoPlayerController implements TextureView.SurfaceTextureListener,
        DefaultLifecycleObserver {

    private final Context appContext;
    private final TextureView textureView;
    @Nullable
    private ExoPlayer player;
    @Nullable
    private String videoUrl;
    private boolean playWhenReady;
    private boolean lifecyclePaused;
    private int videoWidth;
    private int videoHeight;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            videoWidth = videoSize.width;
            videoHeight = videoSize.height;
            applyVideoTransform();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            // 保持与旧实现一致：吞掉错误，由上层决定是否换源
        }
    };

    public VideoPlayerController(TextureView textureView) {
        this.textureView = textureView;
        this.appContext = textureView.getContext().getApplicationContext();
        textureView.setSurfaceTextureListener(this);
        textureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyVideoTransform();
            }
        });
    }

    public void setVideoUrl(String url) {
        this.videoUrl = url;
        if (textureView.isAvailable()) {
            preparePlayer(textureView.getSurfaceTexture());
        }
    }

    public void play() {
        playWhenReady = true;
        if (lifecyclePaused) {
            return;
        }
        ensurePlayer();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    public void pause() {
        playWhenReady = false;
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    public boolean isPlaying() {
        return player != null
                && player.getPlayWhenReady()
                && player.getPlaybackState() == Player.STATE_READY;
    }

    public void togglePlayPause() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void release() {
        playWhenReady = false;
        videoWidth = 0;
        videoHeight = 0;
        textureView.setSurfaceTextureListener(null);
        releaseInternal();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        lifecyclePaused = true;
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        lifecyclePaused = false;
        if (playWhenReady && player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        release();
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (videoUrl != null) {
            preparePlayer(surface);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        applyVideoTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        if (player != null) {
            player.clearVideoSurface();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    private void preparePlayer(@Nullable SurfaceTexture surfaceTexture) {
        if (videoUrl == null || surfaceTexture == null) {
            return;
        }
        ensurePlayer();
        if (player == null) {
            return;
        }
        player.setVideoSurface(new Surface(surfaceTexture));
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.prepare();
        player.setPlayWhenReady(playWhenReady && !lifecyclePaused);
    }

    private void ensurePlayer() {
        if (player != null) {
            return;
        }
        player = new ExoPlayer.Builder(appContext).build();
        player.addListener(playerListener);
    }

    private void releaseInternal() {
        if (player != null) {
            player.removeListener(playerListener);
            player.release();
            player = null;
        }
    }

    private void applyVideoTransform() {
        VideoTransformHelper.applyFitWidth(textureView, videoWidth, videoHeight);
    }
}
```

- [ ] **Step 2: 编译**

Run: `./gradlew :app:compileDebugJavaWithJavac`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/douyin/player/VideoPlayerController.java
git commit -m "feat: replace MediaPlayer with Media3 ExoPlayer"
```

---

### Task 4: VideoPageFragment 接入 LifecycleObserver + ViewBinding

**Files:**

- Modify: `app/src/main/java/com/example/douyin/feed/VideoPageFragment.java`
- Layout: `app/src/main/res/layout/fragment_video_page.xml`（及子 layout，若 binding 需要）

**Interfaces:**

- Consumes: `VideoPlayerController` 的 Lifecycle 回调
- Produces: 页面在 `onViewCreated` 注册 observer；`onDestroyView` 不再重复 `release`（由 observer `onDestroy` 或显式 remove 后 release 一次）

- [ ] **Step 1: 改用 ViewBinding 并注册 Lifecycle**

在 `onCreateView`：

```java
private FragmentVideoPageBinding binding;

@Nullable
@Override
public View onCreateView(@NonNull LayoutInflater inflater,
                         @Nullable ViewGroup container,
                         @Nullable Bundle savedInstanceState) {
    binding = FragmentVideoPageBinding.inflate(inflater, container, false);
    return binding.getRoot();
}
```

创建 `playerController` 后：

```java
playerController = new VideoPlayerController(textureView);
getViewLifecycleOwner().getLifecycle().addObserver(playerController);
```

调整生命周期方法，避免双重 release：

```java
@Override
public void onDestroyView() {
    if (playerController != null) {
        getViewLifecycleOwner().getLifecycle().removeObserver(playerController);
        playerController.release();
        playerController = null;
    }
    binding = null;
    super.onDestroyView();
}
```

说明：`DefaultLifecycleObserver.onPause/onResume` 与 Fragment 现有 `onPause`/`onResume` 中的 `play/pause` **保留其一即可**。推荐：**页面可见性逻辑仍由 Fragment 的 `isPageActive()` / `userPaused` 调用 `play()`/`pause()`**；LifecycleObserver 只负责 Activity 进后台时强制 pause、以及兜底。若出现「前后台回来不播」，检查 `playWhenReady` 与 `userPaused` 是否冲突，优先信任 Fragment 显式调用。

将 `findViewById` 逐步替换为 `binding.*`（至少 texture / like / comment 相关控件）。

- [ ] **Step 2: 真机/模拟器手动验证**

- Feed 上下滑：仅当前页有声播放  
- 单击暂停/继续  
- 按 Home 再回前台：暂停且不崩溃  
- 横滑个人页：视频暂停  

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/douyin/feed/VideoPageFragment.java
git commit -m "feat: bind ExoPlayer controller to view lifecycle"
```

---

### Task 5: TokenStore → DataStore + 内存缓存

**Files:**

- Modify: `app/src/main/java/com/example/douyin/network/TokenStore.java`
- Modify: `app/src/main/java/com/example/douyin/DouyinApp.java`
- Test: `app/src/test/java/com/example/douyin/network/TokenStoreMemoryCacheTest.java`

**Interfaces:**

- Consumes: `RxPreferenceDataStoreBuilder`、`PreferencesKeys`
- Produces: `hydrate(Runnable onReady)`；`getToken()` 等同步读仅命中内存；`saveToken`/`clear` 先改内存再异步落盘

- [ ] **Step 1: 写失败单测（内存缓存语义）**

因 DataStore 需 Android Context，JVM 单测只验证「可注入的缓存契约」。先抽包可见的简单逻辑或测 public API 的内存侧：在 `TokenStore` 增加 `@VisibleForTesting` 构造/包内方法不必要——改为测纯函数式 helper，或使用 Robolectric。为保持简单，本任务 JVM 测采用 **不依赖 Android 的缓存 holder**：

创建测试文件（验证设计约定文档化的行为；实现后用 Robolectric 可后续补）。最小可行：把缓存读写抽到 `TokenMemoryCache`：

Create `app/src/main/java/com/example/douyin/network/TokenMemoryCache.java`：

```java
package com.example.douyin.network;

import androidx.annotation.Nullable;

final class TokenMemoryCache {
    @Nullable String token;
    long userId = -1L;

    synchronized void set(@Nullable String token, long userId) {
        this.token = token;
        this.userId = userId;
    }

    synchronized void clear() {
        token = null;
        userId = -1L;
    }

    @Nullable
    synchronized String getToken() {
        return token;
    }

    synchronized long getUserId() {
        return userId;
    }

    synchronized boolean isLoggedIn() {
        return token != null && !token.isEmpty();
    }
}
```

Test:

```java
package com.example.douyin.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class TokenStoreMemoryCacheTest {
    @Test
    public void set_thenIsLoggedIn() {
        TokenMemoryCache cache = new TokenMemoryCache();
        cache.set("abc", 42L);
        assertTrue(cache.isLoggedIn());
        assertEquals("abc", cache.getToken());
        assertEquals(42L, cache.getUserId());
    }

    @Test
    public void clear_resetsState() {
        TokenMemoryCache cache = new TokenMemoryCache();
        cache.set("abc", 1L);
        cache.clear();
        assertFalse(cache.isLoggedIn());
        assertNull(cache.getToken());
        assertEquals(-1L, cache.getUserId());
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests com.example.douyin.network.TokenStoreMemoryCacheTest`

Expected: PASS

- [ ] **Step 2: 重写 TokenStore**

```java
package com.example.douyin.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.Single;

public final class TokenStore {

    private static final String STORE_NAME = "douyin_auth";
    private static final String LEGACY_PREFS = "douyin_auth";
    private static final Preferences.Key<String> KEY_TOKEN = PreferencesKeys.stringKey("token");
    private static final Preferences.Key<Long> KEY_USER_ID = PreferencesKeys.longKey("user_id");

    private static volatile TokenStore instance;

    private final Context appContext;
    private final RxDataStore<Preferences> dataStore;
    private final TokenMemoryCache cache = new TokenMemoryCache();
    private final AtomicBoolean hydrated = new AtomicBoolean(false);

    private TokenStore(Context context) {
        appContext = context.getApplicationContext();
        dataStore = new RxPreferenceDataStoreBuilder(appContext, STORE_NAME).build();
    }

    public static TokenStore get(Context context) {
        if (instance == null) {
            synchronized (TokenStore.class) {
                if (instance == null) {
                    instance = new TokenStore(context);
                }
            }
        }
        return instance;
    }

    /** 异步加载 DataStore（及旧 SharedPreferences 迁移）到内存缓存。 */
    public void hydrate(@Nullable Runnable onReady) {
        migrateLegacyIfNeeded();
        dataStore.data().firstOrError()
                .onErrorReturnItem(androidx.datastore.preferences.core.PreferencesFactory.createEmpty())
                .subscribe(prefs -> {
                    String token = prefs.get(KEY_TOKEN);
                    Long userId = prefs.get(KEY_USER_ID);
                    cache.set(token, userId != null ? userId : -1L);
                    hydrated.set(true);
                    if (onReady != null) {
                        onReady.run();
                    }
                }, error -> {
                    hydrated.set(true);
                    if (onReady != null) {
                        onReady.run();
                    }
                });
    }

    public boolean isHydrated() {
        return hydrated.get();
    }

    public void saveToken(String token, long userId) {
        cache.set(token, userId);
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(KEY_TOKEN, token);
            mutablePreferences.set(KEY_USER_ID, userId);
            return Single.just(mutablePreferences);
        }).subscribe(p -> {}, e -> {});
    }

    @Nullable
    public String getToken() {
        return cache.getToken();
    }

    public long getUserId() {
        return cache.getUserId();
    }

    public boolean isLoggedIn() {
        return cache.isLoggedIn();
    }

    public void clear() {
        cache.clear();
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.remove(KEY_TOKEN);
            mutablePreferences.remove(KEY_USER_ID);
            return Single.just(mutablePreferences);
        }).subscribe(p -> {}, e -> {});
    }

    private void migrateLegacyIfNeeded() {
        SharedPreferences legacy = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        String legacyToken = legacy.getString("token", null);
        if (TextUtils.isEmpty(legacyToken)) {
            return;
        }
        long legacyUserId = legacy.getLong("user_id", -1L);
        cache.set(legacyToken, legacyUserId);
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(KEY_TOKEN, legacyToken);
            mutablePreferences.set(KEY_USER_ID, legacyUserId);
            return Single.just(mutablePreferences);
        }).subscribe(p -> legacy.edit().clear().apply(), e -> {});
    }
}
```

注意：若 `PreferencesFactory.createEmpty()` 在所用版本不存在，改为 `dataStore.updateDataAsync` 读失败时直接 `cache.clear()` 并 `onReady`。以编译为准微调 import。

- [ ] **Step 3: DouyinApp 启动 hydrate**

```java
@Override
public void onCreate() {
    super.onCreate();
    AppDatabase.get(this);
    LocalServices.init(this);
    MediaCacheManager.get(this);
    TokenStore.get(this).hydrate(null);
    AppExecutors.get().diskIo(() -> SeedDataInitializer.init(this));
}
```

- [ ] **Step 4: 编译 + 单测**

Run:

```
./gradlew :app:compileDebugJavaWithJavac :app:testDebugUnitTest --tests com.example.douyin.network.TokenStoreMemoryCacheTest
```

Expected: SUCCESS / PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/douyin/network/TokenStore.java \
  app/src/main/java/com/example/douyin/network/TokenMemoryCache.java \
  app/src/main/java/com/example/douyin/DouyinApp.java \
  app/src/test/java/com/example/douyin/network/TokenStoreMemoryCacheTest.java
git commit -m "feat: migrate TokenStore to DataStore with memory cache"
```

---

### Task 6: Splash 等待 hydrate

**Files:**

- Modify: `app/src/main/java/com/example/douyin/auth/SplashActivity.java`

**Interfaces:**

- Consumes: `TokenStore.hydrate(Runnable)` / `isHydrated()`

- [ ] **Step 1: 路由前确保已 hydrate**

```java
private void routeNextScreen() {
    TokenStore store = TokenStore.get(this);
    if (store.isHydrated()) {
        navigate(store.isLoggedIn());
        return;
    }
    store.hydrate(() -> runOnUiThread(() -> navigate(store.isLoggedIn())));
}

private void navigate(boolean loggedIn) {
    AuthTrace.begin("auth_splash_route");
    try {
        Intent intent = loggedIn
                ? new Intent(this, MainActivity.class)
                : new Intent(this, LoginActivity.class);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    } finally {
        AuthTrace.end();
    }
}
```

- [ ] **Step 2: 手动验证**

登录 → 杀进程 → 冷启动应进 Main；退出登录后冷启动进 Login。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/douyin/auth/SplashActivity.java
git commit -m "fix: wait for TokenStore hydrate before splash routing"
```

---

### Task 7: FeedUiState + FeedViewModel（TDD）

**Files:**

- Create: `app/src/main/java/com/example/douyin/feed/FeedUiState.java`
- Create: `app/src/main/java/com/example/douyin/feed/FeedViewModel.java`
- Test: `app/src/test/java/com/example/douyin/feed/FeedViewModelTest.java`

**Interfaces:**

- Consumes: `VideoRepository.getFeed` / `toggleLike`（通过构造注入，便于单测假实现）
- Produces: `LiveData<FeedUiState> getUiState()`；`void loadFeed()`；`void toggleLike(long videoId, boolean loggedIn)`；`SingleLiveEvent<String> getLoginRequired()`；`void updateLike(long, boolean, int)`；`void updateCommentCount(long, int)`

- [ ] **Step 1: 写 FeedUiState**

```java
package com.example.douyin.feed;

import com.example.douyin.network.model.VideoDto;

import java.util.Collections;
import java.util.List;

public final class FeedUiState {
    public final boolean loading;
    public final String errorMessage;
    public final List<VideoDto> videos;

    private FeedUiState(boolean loading, String errorMessage, List<VideoDto> videos) {
        this.loading = loading;
        this.errorMessage = errorMessage;
        this.videos = videos;
    }

    public static FeedUiState loading() {
        return new FeedUiState(true, null, Collections.emptyList());
    }

    public static FeedUiState success(List<VideoDto> videos) {
        return new FeedUiState(false, null, videos != null ? videos : Collections.emptyList());
    }

    public static FeedUiState error(String message) {
        return new FeedUiState(false, message, Collections.emptyList());
    }

    public boolean isEmpty() {
        return !loading && errorMessage == null && videos.isEmpty();
    }
}
```

- [ ] **Step 2: 写失败单测（假 Repository）**

为可测性，`FeedViewModel` 构造接收 `VideoRepository` 与 `pageSize`。单测用手工 stub：若 `VideoRepository` 不易 mock，抽取接口 `FeedDataSource`：

```java
public interface FeedDataSource {
    void getFeed(int page, int size, ApiCallback<FeedPage> callback);
    void toggleLike(long videoId, ApiCallback<LikeResult> callback);
}
```

`VideoRepository` 已有同名方法——可让 `FeedViewModel` 直接依赖 `VideoRepository`，测试里用匿名子类不可能（非接口）。**做法：** 增加包内接口 `FeedDataSource`，`VideoRepository` 不改；在 `FeedViewModel` 中依赖 `FeedDataSource`；`FeedFragment` 传入：

```java
videoRepository::getFeed // 不行，两方法
```

更干净：

```java
FeedDataSource source = new FeedDataSource() {
    @Override public void getFeed(int page, int size, ApiCallback<FeedPage> cb) {
        videoRepository.getFeed(page, size, cb);
    }
    @Override public void toggleLike(long videoId, ApiCallback<LikeResult> cb) {
        videoRepository.toggleLike(videoId, cb);
    }
};
```

或让 `FeedViewModel` 提供 `Factory` 接收 `VideoRepository`，测试用 Mockito。本工程尚无 Mockito——**用 `FeedDataSource` 接口 + 测试假实现**。

Create `app/src/main/java/com/example/douyin/feed/FeedDataSource.java` 如上接口。

测试：

```java
package com.example.douyin.feed;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.VideoDto;

import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class FeedViewModelTest {

    @Rule
    public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    @Test
    public void loadFeed_success_emitsVideos() {
        FeedDataSource source = new FeedDataSource() {
            @Override
            public void getFeed(int page, int size, ApiCallback<FeedPage> callback) {
                FeedPage pageData = new FeedPage();
                VideoDto v = new VideoDto();
                v.id = 1L;
                pageData.list = Collections.singletonList(v);
                callback.onSuccess(pageData);
            }

            @Override
            public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
            }
        };
        FeedViewModel vm = new FeedViewModel(source, 20);
        vm.loadFeed();
        FeedUiState state = vm.getUiState().getValue();
        assertNotNull(state);
        assertFalse(state.loading);
        assertEquals(1, state.videos.size());
    }

    @Test
    public void loadFeed_error_emitsMessage() {
        FeedDataSource source = new FeedDataSource() {
            @Override
            public void getFeed(int page, int size, ApiCallback<FeedPage> callback) {
                callback.onError(500, "boom");
            }

            @Override
            public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
            }
        };
        FeedViewModel vm = new FeedViewModel(source, 20);
        vm.loadFeed();
        assertEquals("boom", vm.getUiState().getValue().errorMessage);
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests com.example.douyin.feed.FeedViewModelTest`

Expected: FAIL（类不存在）

- [ ] **Step 3: 实现 FeedViewModel**

```java
package com.example.douyin.feed;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.VideoDto;
import com.example.douyin.repository.VideoRepository;
import com.example.douyin.util.SingleLiveEvent;

import java.util.ArrayList;
import java.util.List;

public class FeedViewModel extends ViewModel {

    private final FeedDataSource dataSource;
    private final int pageSize;
    private final MutableLiveData<FeedUiState> uiState = new MutableLiveData<>(FeedUiState.loading());
    private final SingleLiveEvent<String> loginRequired = new SingleLiveEvent<>();

    public FeedViewModel(FeedDataSource dataSource, int pageSize) {
        this.dataSource = dataSource;
        this.pageSize = pageSize;
    }

    public LiveData<FeedUiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getLoginRequired() {
        return loginRequired;
    }

    public void loadFeed() {
        uiState.setValue(FeedUiState.loading());
        dataSource.getFeed(0, pageSize, new ApiCallback<FeedPage>() {
            @Override
            public void onSuccess(FeedPage data) {
                List<VideoDto> list = data != null && data.list != null ? data.list : new ArrayList<>();
                uiState.postValue(FeedUiState.success(list));
            }

            @Override
            public void onError(int code, String message) {
                uiState.postValue(FeedUiState.error(message != null ? message : "加载失败"));
            }
        });
    }

    public void toggleLike(long videoId, boolean loggedIn) {
        if (!loggedIn) {
            loginRequired.setValue("need_login");
            return;
        }
        dataSource.toggleLike(videoId, new ApiCallback<LikeResult>() {
            @Override
            public void onSuccess(LikeResult data) {
                if (data != null) {
                    updateLike(videoId, data.isLiked, data.likeCount);
                }
            }

            @Override
            public void onError(int code, String message) {
                // 保持现行为：错误由页面 Toast；此处可扩展 error event
            }
        });
    }

    public void updateLike(long videoId, boolean isLiked, int likeCount) {
        FeedUiState current = uiState.getValue();
        if (current == null || current.videos.isEmpty()) {
            return;
        }
        List<VideoDto> copy = new ArrayList<>(current.videos);
        for (VideoDto v : copy) {
            if (v.id == videoId) {
                v.isLiked = isLiked;
                v.likeCount = likeCount;
                break;
            }
        }
        uiState.setValue(FeedUiState.success(copy));
    }

    public void updateCommentCount(long videoId, int commentCount) {
        FeedUiState current = uiState.getValue();
        if (current == null || current.videos.isEmpty()) {
            return;
        }
        List<VideoDto> copy = new ArrayList<>(current.videos);
        for (VideoDto v : copy) {
            if (v.id == videoId) {
                v.commentCount = commentCount;
                break;
            }
        }
        uiState.setValue(FeedUiState.success(copy));
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final VideoRepository repository;
        private final int pageSize;

        public Factory(VideoRepository repository, int pageSize) {
            this.repository = repository;
            this.pageSize = pageSize;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            FeedDataSource source = new FeedDataSource() {
                @Override
                public void getFeed(int page, int size, ApiCallback<FeedPage> callback) {
                    repository.getFeed(page, size, callback);
                }

                @Override
                public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
                    repository.toggleLike(videoId, callback);
                }
            };
            return (T) new FeedViewModel(source, pageSize);
        }
    }
}
```

核对 `LikeResult` / `VideoDto` 字段名（`liked` vs `isLiked` 等）与现有模型一致；不一致则以仓库现有字段为准修改本任务代码。

- [ ] **Step 4: 跑通单测**

Run: `./gradlew :app:testDebugUnitTest --tests com.example.douyin.feed.FeedViewModelTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/douyin/feed/FeedUiState.java \
  app/src/main/java/com/example/douyin/feed/FeedDataSource.java \
  app/src/main/java/com/example/douyin/feed/FeedViewModel.java \
  app/src/test/java/com/example/douyin/feed/FeedViewModelTest.java
git commit -m "feat: add FeedViewModel with LiveData ui state"
```

---

### Task 8: FeedFragment 迁 MVVM + ViewBinding

**Files:**

- Modify: `app/src/main/java/com/example/douyin/feed/FeedFragment.java`
- Modify: `app/src/main/java/com/example/douyin/feed/VideoPageFragment.java`（点赞改走 ViewModel，若当前经 FeedFragment 中转则保留中转但内部调 ViewModel）

**Interfaces:**

- Consumes: `FeedViewModel.Factory`、`FeedUiState`
- Produces: Fragment 无直接 `videoRepository.getFeed` 回调逻辑

- [ ] **Step 1: 改造 FeedFragment**

要点：

```java
private FragmentFeedBinding binding;
private FeedViewModel viewModel;

@Override
public View onCreateView(...) {
    binding = FragmentFeedBinding.inflate(inflater, container, false);
    return binding.getRoot();
}

@Override
public void onViewCreated(...) {
    VideoRepository videoRepository = new VideoRepository(requireContext());
    authRepository = new AuthRepository(requireContext());
    viewModel = new ViewModelProvider(this, new FeedViewModel.Factory(videoRepository, FEED_PAGE_SIZE))
            .get(FeedViewModel.class);

    // setup ViewPager2 with binding.viewPager 等

    viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
    viewModel.getLoginRequired().observe(getViewLifecycleOwner(), msg -> {
        Toast.makeText(requireContext(), R.string.login_required, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(requireContext(), LoginActivity.class));
    });

    binding.tvError.setOnClickListener(v -> viewModel.loadFeed());
    viewModel.loadFeed();
}

private void render(FeedUiState state) {
    if (state.loading) { /* show loading */ return; }
    if (state.errorMessage != null) { /* show error */ return; }
    if (state.isEmpty()) { /* show empty */ return; }
    pagerAdapter.submitList(state.videos);
    // show content + prefetchAround(0)
}

public void refreshFeed() {
    if (isAdded()) {
        viewModel.loadFeed();
    }
}

public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
    // 若 VideoPageFragment 仍传 callback，可：先 viewModel，再在 adapter 更新后 callback
    viewModel.toggleLike(videoId, authRepository.isLoggedIn());
}
```

将 `onVideoLikeChanged` / `onVideoCommentChanged` 改为调用 `viewModel.updateLike` / `updateCommentCount`，并由 `render` 刷新 adapter（或 adapter 仍局部更新以避免整表刷新闪烁——若整表刷新体验差，保留 `pagerAdapter.updateLike`，同时 `viewModel.updateLike` 同步状态）。

- [ ] **Step 2: 手动验证 Feed 加载 / 错误重试 / 旋转屏幕列表仍在**

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/douyin/feed/FeedFragment.java \
  app/src/main/java/com/example/douyin/feed/VideoPageFragment.java
git commit -m "refactor: move FeedFragment to MVVM with ViewBinding"
```

---

### Task 9: Login MVVM

**Files:**

- Create: `auth/LoginUiState.java`、`auth/LoginViewModel.java`
- Modify: `auth/LoginActivity.java`

**Interfaces:**

- Consumes: `AuthRepository.login`
- Produces: `LiveData<LoginUiState>`；`SingleLiveEvent<Void> navigateMain`

- [ ] **Step 1: LoginUiState + LoginViewModel**

```java
public final class LoginUiState {
    public final boolean loading;
    public final String usernameError;
    public final String passwordError;
    public final String toastMessage;

    public static LoginUiState idle() { return new LoginUiState(false, null, null, null); }
    public static LoginUiState loading() { return new LoginUiState(true, null, null, null); }
    // 构造器省略字段赋值
}
```

```java
public class LoginViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<LoginUiState> uiState = new MutableLiveData<>(LoginUiState.idle());
    private final SingleLiveEvent<Void> navigateMain = new SingleLiveEvent<>();

    public void login(String username, String password) {
        if (TextUtils.isEmpty(username)) {
            uiState.setValue(LoginUiState.usernameError(...));
            return;
        }
        // 同理 password
        uiState.setValue(LoginUiState.loading());
        authRepository.login(username, password, new ApiCallback<LoginResult>() {
            @Override public void onSuccess(LoginResult data) {
                uiState.postValue(LoginUiState.idle());
                navigateMain.postValue(null);
            }
            @Override public void onError(int code, String message) {
                uiState.postValue(LoginUiState.toast(message));
            }
        });
    }

    public static class Factory implements ViewModelProvider.Factory { /* AuthRepository */ }
}
```

（将 `LoginUiState` 工厂方法写全，与现有 `R.string.error_*` 文案对齐；Activity 只负责把 error 设到 `TextInputLayout`。）

- [ ] **Step 2: LoginActivity ViewBinding + observe**

`ActivityLoginBinding`；`ViewModelProvider`；按钮调用 `viewModel.login`；observe `navigateMain` → `goToMain()`。

DEBUG 跳过登录：仍调用现有 demo 账号逻辑，可放在 ViewModel `loginDemo()`。

- [ ] **Step 3: 手动验证登录成功 / 校验错误 / 已登录直达 Main**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/douyin/auth/LoginUiState.java \
  app/src/main/java/com/example/douyin/auth/LoginViewModel.java \
  app/src/main/java/com/example/douyin/auth/LoginActivity.java
git commit -m "refactor: move login screen to MVVM"
```

---

### Task 10: Register MVVM

**Files:**

- Create: `auth/RegisterUiState.java`、`auth/RegisterViewModel.java`
- Modify: `auth/RegisterActivity.java`

**Interfaces:** 同 Login 模式，成功事件导航到 Main 或 Login（保持现行为）。

- [ ] **Step 1–3:** 镜像 Task 9：抽出校验与 `authRepository.register`，Activity 改 ViewBinding + observe。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/douyin/auth/RegisterUiState.java \
  app/src/main/java/com/example/douyin/auth/RegisterViewModel.java \
  app/src/main/java/com/example/douyin/auth/RegisterActivity.java
git commit -m "refactor: move register screen to MVVM"
```

---

### Task 11: Comment MVVM

**Files:**

- Create: `comment/CommentUiState.java`、`comment/CommentViewModel.java`
- Modify: `comment/CommentBottomSheet.java`

**Interfaces:**

- Consumes: `CommentRepository`、`videoId`
- Produces: 列表 / loading / posting / empty；`postComment` 成功后更新 count 并回调 listener

- [ ] **Step 1: CommentViewModel**

状态包含：`List<CommentDto> comments`、`boolean loading`、`boolean loadingMore`、`boolean posting`、`boolean hasMore`、`String error`、`int commentCount`。

方法：`loadInitial()`、`loadMore()`、`send(String content, boolean loggedIn)`。

使用 `ViewModelProvider(this, factory)`，`Factory` 接收 `videoId`、`CommentRepository`。

- [ ] **Step 2: CommentBottomSheet**

ViewBinding；删除 Fragment 内 `loading`/`posting` 业务字段，改为 observe；未登录仍跳转 `LoginActivity`。

- [ ] **Step 3: 手动验证打开评论、发送、加载更多**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/douyin/comment/
git commit -m "refactor: move comments bottom sheet to MVVM"
```

---

### Task 12: Profile MVVM（轻量）

**Files:**

- Create: `profile/ProfileUiState.java`、`profile/ProfileViewModel.java`
- Modify: `profile/ProfileFragment.java`
- Modify（按需）: `profile/UserProfileController.java`

**Interfaces:**

- `ProfileViewModel`：`isLoggedIn`、`getUserId`、`logout()`；资料/作品加载若已在 `UserProfileController`，本轮最小改动为：ViewModel 管登录态与 logout，Controller 仍负责展示——**或** ViewModel 调 `UserRepository`/`VideoRepository` 加载后把 DTO 交给 Controller 只绑视图。

推荐最小闭环：

```java
public class ProfileViewModel extends ViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<ProfileUiState> uiState = new MutableLiveData<>();

    public void refreshSession() {
        if (!authRepository.isLoggedIn()) {
            uiState.setValue(ProfileUiState.needLogin());
        } else {
            uiState.setValue(ProfileUiState.ready(authRepository.getUserId()));
        }
    }

    public void logout() {
        authRepository.logout();
        uiState.setValue(ProfileUiState.needLogin());
    }
}
```

`ProfileFragment` observe：`needLogin` → 现有 `navigateToLogin()`；`ready` → 创建/刷新 `UserProfileController`。

- [ ] **Step 1–2:** 实现并改 Fragment 为 ViewBinding + ViewModel。

- [ ] **Step 3: 手动验证个人页 / 退出登录**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/douyin/profile/
git commit -m "refactor: add ProfileViewModel for session and logout"
```

---

### Task 13: PublishActivity ViewBinding（轻量）

**Files:**

- Modify: `app/src/main/java/com/example/douyin/publish/PublishActivity.java`

**Interfaces:** 不引入完整 PublishViewModel 亦可；若发布回调集中，可加 `PublishViewModel` 仅包装 `VideoRepository.publishVideo`。

- [ ] **Step 1:** `ActivityPublishBinding` 替换 `findViewById`；发布中按钮禁用逻辑保留。

- [ ] **Step 2:** 可选 `PublishViewModel`：`publish(File, File, String)` → `SingleLiveEvent` 成功/`UiState` 失败。若改动超过 1 小时，跳过 ViewModel，仅 ViewBinding。

- [ ] **Step 3: 手动验证发布成功回刷 Feed**

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/douyin/publish/PublishActivity.java
git commit -m "refactor: enable ViewBinding on publish screen"
```

---

### Task 14: 回归清单与收尾

**Files:** 无强制代码变更；修复回归 bug 时随问题提交。

- [ ] **Step 1: 主路径回归**

1. 冷启动 Splash → 登录态正确  
2. Feed 滑动播放（ExoPlayer）/ 点赞 / 评论  
3. 登录注册  
4. 个人页与退出  
5. 拍摄或导入发布 → 回 Feed  
6. 旋转屏幕：Feed 不丢列表（ViewModel 作用域）  

- [ ] **Step 2: 跑单测**

Run: `./gradlew :app:testDebugUnitTest`

Expected: 既有 `PasswordHasherTest` + 本计划新增测试全部 PASS

- [ ] **Step 3: 确认无业务页残留（已迁移页）大量 findViewById**

对已改页面 grep `findViewById`，仅允许合理遗留（如动态 add 的 view）。

- [ ] **Step 4: 最终 commit（若有修复）**

```bash
git commit -m "fix: address Jetpack migration regression issues"
```

---

## Self-Review（对照 spec）

| Spec 项 | 对应任务 |
|---------|----------|
| ViewBinding + lifecycle-viewmodel/livedata | Task 1 |
| ExoPlayer + LifecycleObserver | Task 3–4 |
| DataStore TokenStore + 内存缓存 + Splash hydrate | Task 5–6 |
| Feed / Auth / Comment / Profile MVVM | Task 7–12 |
| Publish 轻量 | Task 13 |
| 不做 Navigation/Hilt/CameraX/Paging/Kotlin | Global Constraints |
| 单测 FeedViewModel + Token 缓存 | Task 5、7 |
| Player 不进 ViewModel | Task 3–4、7 |

占位符扫描：LoginUiState 工厂在 Task 9 需实现时写全字段；`LikeResult` 字段名实现时与 `network/model/LikeResult.java` 对齐。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-11-jetpack-mvvm-exoplayer.md`.

**Two execution options:**

1. **Subagent-Driven（推荐）** — 每任务派生子代理，任务间审查，迭代快  
2. **Inline Execution** — 本会话按 `executing-plans` 批量执行并设检查点  

Which approach?
