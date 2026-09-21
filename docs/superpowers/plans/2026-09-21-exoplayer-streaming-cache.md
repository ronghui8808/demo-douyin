# ExoPlayer 流式 Cache + 2MB 预取 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Feed 视频从「MediaPlayer + 整文件预下载」改为「ExoPlayer + SimpleCache 流式缓存」，并对当前/相邻条目预取前 2MB。

**Architecture:** 共享 `ExoMediaCache`（`SimpleCache` + `CacheDataSource.Factory`）供播放与预取共用；`VideoPlayerController` 改为 ExoPlayer，远程 URL 走 CacheDataSource，本地 `file://` 走 DefaultDataSource；`FeedFragment` 用 `CacheWriter` 对 position±1 预取前 2MB；`MediaCacheManager` 仅保留封面图缓存。

**Tech Stack:** Java 11、AndroidX Media3 1.5.1（exoplayer / database / datasource）、现有 `AppExecutors`、JUnit4 单测

## Global Constraints

- 语言：Java（不引入 Kotlin）
- Media3 版本锁定：`1.5.1`
- 预取窗口：当前 position、position+1、position-1（与现逻辑一致）
- 预取字节数：固定 `2L * 1024L * 1024L`（2MB），不整文件下载
- 本地路径（`file://` 或非 http(s)）不进入 SimpleCache，不预取
- 封面图继续用 `MediaCacheManager` / `CoverImageLoader`，本计划不改图片链路
- 不做播放器池、不清迁移旧 `oss_media/video`（可自然闲置；可选后续清理）
- 不改 Publish 预览 `VideoView`

---

## File Structure

| 路径 | 职责 |
|------|------|
| `gradle/libs.versions.toml` | Media3 版本与 library 别名 |
| `app/build.gradle.kts` | 引入 media3 依赖 |
| `player/MediaUrlHelper.java` | URL 规范化 / 是否可预取 / `PREFETCH_BYTES` |
| `cache/ExoMediaCache.java` | SimpleCache 单例、CacheDataSource.Factory、2MB CacheWriter 预取 |
| `player/VideoPlayerController.java` | MediaPlayer → ExoPlayer，TextureView + FitWidth |
| `feed/FeedFragment.java` | `prefetchVideo` → `ExoMediaCache.prefetch` |
| `feed/VideoPageFragment.java` | 去掉 `resolveVideoForPlayback`，直接设 URL |
| `DouyinApp.java` | 启动时初始化 `ExoMediaCache` |
| `cache/MediaCacheManager.java` | 删除视频相关 API，仅保留图片 |
| `test/.../MediaUrlHelperTest.java` | URL / 预取常量单测 |

---

### Task 1: 添加 Media3 依赖

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: Gradle 可解析 `libs.media3.exoplayer` / `libs.media3.database` / `libs.media3.datasource`
- Consumes: 无

- [ ] **Step 1: 在 version catalog 增加 media3**

在 `[versions]` 增加：

```toml
media3 = "1.5.1"
```

在 `[libraries]` 增加：

```toml
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-database = { group = "androidx.media3", name = "media3-database", version.ref = "media3" }
media3-datasource = { group = "androidx.media3", name = "media3-datasource", version.ref = "media3" }
```

- [ ] **Step 2: 在 app 模块声明依赖**

在 `app/build.gradle.kts` 的 `dependencies` 中、`implementation(libs.aliyun.oss.android.sdk)` 之后加入：

```kotlin
implementation(libs.media3.exoplayer)
implementation(libs.media3.database)
implementation(libs.media3.datasource)
```

- [ ] **Step 3: 同步并确认依赖可解析**

Run:

```bash
.\gradlew.bat :app:dependencies --configuration debugCompileClasspath
```

Expected: 输出中出现 `androidx.media3:media3-exoplayer:1.5.1`（及 database / datasource）

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "$(cat <<'EOF'
chore: add Media3 dependencies for streaming video cache

EOF
)"
```

---

### Task 2: MediaUrlHelper（可预取规则 + 2MB 常量）

**Files:**
- Create: `app/src/test/java/com/example/douyin/player/MediaUrlHelperTest.java`
- Create: `app/src/main/java/com/example/douyin/player/MediaUrlHelper.java`

**Interfaces:**
- Produces:
  - `public static final long PREFETCH_BYTES = 2L * 1024L * 1024L;`
  - `public static String normalize(String url)`
  - `public static boolean isRemoteHttpUrl(String url)`
  - `public static boolean shouldPrefetch(String url)` — 等价于 `isRemoteHttpUrl(normalize(url))`
- Consumes: 无

- [ ] **Step 1: 写失败单测**

```java
package com.example.douyin.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MediaUrlHelperTest {

    @Test
    public void remoteHttpUrls_arePrefetchable() {
        assertTrue(MediaUrlHelper.shouldPrefetch("https://cdn.example.com/a.mp4"));
        assertTrue(MediaUrlHelper.shouldPrefetch("http://cdn.example.com/a.mp4"));
        assertTrue(MediaUrlHelper.shouldPrefetch("  https://cdn.example.com/a.mp4  "));
    }

    @Test
    public void localUrls_areNotPrefetchable() {
        assertFalse(MediaUrlHelper.shouldPrefetch("file:///data/local/tmp/a.mp4"));
        assertFalse(MediaUrlHelper.shouldPrefetch("/data/user/0/com.example/files/a.mp4"));
        assertFalse(MediaUrlHelper.shouldPrefetch(""));
        assertFalse(MediaUrlHelper.shouldPrefetch(null));
    }

    @Test
    public void normalize_trimsWhitespace() {
        assertEquals("https://cdn.example.com/a.mp4",
                MediaUrlHelper.normalize("  https://cdn.example.com/a.mp4  "));
        assertEquals("", MediaUrlHelper.normalize(null));
    }

    @Test
    public void prefetchBytes_isTwoMegabytes() {
        assertEquals(2L * 1024L * 1024L, MediaUrlHelper.PREFETCH_BYTES);
    }
}
```

- [ ] **Step 2: 跑测确认失败**

Run:

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.example.douyin.player.MediaUrlHelperTest
```

Expected: FAIL（`MediaUrlHelper` 类不存在 / 编译失败）

- [ ] **Step 3: 最小实现**

```java
package com.example.douyin.player;

import android.text.TextUtils;

public final class MediaUrlHelper {

    public static final long PREFETCH_BYTES = 2L * 1024L * 1024L;

    private MediaUrlHelper() {
    }

    public static String normalize(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        return url.trim();
    }

    public static boolean isRemoteHttpUrl(String url) {
        String normalized = normalize(url);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    public static boolean shouldPrefetch(String url) {
        return isRemoteHttpUrl(url);
    }
}
```

- [ ] **Step 4: 跑测确认通过**

Run:

```bash
.\gradlew.bat :app:testDebugUnitTest --tests com.example.douyin.player.MediaUrlHelperTest
```

Expected: BUILD SUCCESSFUL，全部 PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/douyin/player/MediaUrlHelper.java \
  app/src/test/java/com/example/douyin/player/MediaUrlHelperTest.java
git commit -m "$(cat <<'EOF'
feat: add MediaUrlHelper for remote video prefetch rules

EOF
)"
```

---

### Task 3: ExoMediaCache（SimpleCache + 2MB CacheWriter 预取）

**Files:**
- Create: `app/src/main/java/com/example/douyin/cache/ExoMediaCache.java`
- Modify: `app/src/main/java/com/example/douyin/DouyinApp.java`（仅加一行初始化；播放接线在后续 Task）

**Interfaces:**
- Consumes: `MediaUrlHelper.shouldPrefetch` / `MediaUrlHelper.normalize` / `MediaUrlHelper.PREFETCH_BYTES`；`AppExecutors.get().network(...)`
- Produces:
  - `public static synchronized ExoMediaCache get(Context context)`
  - `public CacheDataSource.Factory getCacheDataSourceFactory()`
  - `public void prefetch(String remoteUrl)` — 异步预取前 `PREFETCH_BYTES`；同一 URL 去重；本地 URL no-op
  - 缓存目录：`context.getCacheDir()/exo_video`
  - 驱逐：`LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L)`
  - Database：`StandaloneDatabaseProvider`

- [ ] **Step 1: 实现 ExoMediaCache**

```java
package com.example.douyin.cache;

import android.content.Context;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import com.example.douyin.player.MediaUrlHelper;
import com.example.douyin.util.AppExecutors;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ExoMediaCache {

    private static final long MAX_CACHE_BYTES = 512L * 1024L * 1024L;

    private static ExoMediaCache instance;

    private final SimpleCache simpleCache;
    private final CacheDataSource.Factory cacheDataSourceFactory;
    private final Map<String, Boolean> inflight = new ConcurrentHashMap<>();

    private ExoMediaCache(Context context) {
        File cacheDir = new File(context.getCacheDir(), "exo_video");
        StandaloneDatabaseProvider databaseProvider = new StandaloneDatabaseProvider(context);
        simpleCache = new SimpleCache(
                cacheDir,
                new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                databaseProvider
        );
        DefaultHttpDataSource.Factory upstreamFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);
        cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(simpleCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    public static synchronized ExoMediaCache get(Context context) {
        if (instance == null) {
            instance = new ExoMediaCache(context.getApplicationContext());
        }
        return instance;
    }

    public CacheDataSource.Factory getCacheDataSourceFactory() {
        return cacheDataSourceFactory;
    }

    public void prefetch(String remoteUrl) {
        if (!MediaUrlHelper.shouldPrefetch(remoteUrl)) {
            return;
        }
        String url = MediaUrlHelper.normalize(remoteUrl);
        if (inflight.putIfAbsent(url, Boolean.TRUE) != null) {
            return;
        }
        AppExecutors.get().network(() -> {
            try {
                DataSpec dataSpec = new DataSpec.Builder(android.net.Uri.parse(url))
                        .setLength(MediaUrlHelper.PREFETCH_BYTES)
                        .build();
                CacheDataSource dataSource = cacheDataSourceFactory.createDataSource();
                new CacheWriter(dataSource, dataSpec, /* temporaryBuffer= */ null, /* progressListener= */ null)
                        .cache();
            } catch (Exception ignored) {
                // 预取失败不阻断播放；播放时由 ExoPlayer 自行拉流
            } finally {
                inflight.remove(url);
            }
        });
    }
}
```

注意：若 IDE 提示 `CacheWriter` 构造签名在 1.5.1 有差异，以 Media3 1.5.1 实际 API 为准（`CacheWriter(CacheDataSource, DataSpec, byte[], ProgressListener)`）。

- [ ] **Step 2: 在 DouyinApp 预热单例**

将 `DouyinApp.onCreate` 改为同时初始化图片缓存与 Exo 缓存：

```java
MediaCacheManager.get(this);
ExoMediaCache.get(this);
```

完整 import：

```java
import com.example.douyin.cache.ExoMediaCache;
import com.example.douyin.cache.MediaCacheManager;
```

- [ ] **Step 3: 编译**

Run:

```bash
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/douyin/cache/ExoMediaCache.java \
  app/src/main/java/com/example/douyin/DouyinApp.java
git commit -m "$(cat <<'EOF'
feat: add ExoMediaCache with 2MB streaming prefetch

EOF
)"
```

---

### Task 4: 重写 VideoPlayerController 为 ExoPlayer

**Files:**
- Modify: `app/src/main/java/com/example/douyin/player/VideoPlayerController.java`（整文件替换）

**Interfaces:**
- Consumes: `ExoMediaCache.get(...).getCacheDataSourceFactory()`；`MediaUrlHelper.isRemoteHttpUrl`；`VideoTransformHelper.applyFitWidth`
- Produces: 对外 API 保持不变，供 `VideoPageFragment` 继续调用：
  - `VideoPlayerController(TextureView textureView)`
  - `void setVideoUrl(String url)`
  - `void play()` / `void pause()` / `boolean isPlaying()` / `void togglePlayPause()` / `void release()`

行为要求：
- `playWhenReady` 语义与现实现一致（未 prepared/ready 时先记标志，ready 后自动 start）
- 循环播放：`Player.REPEAT_MODE_ONE`
- 远程：`ProgressiveMediaSource` + CacheDataSource
- 本地：`ProgressiveMediaSource` + `DefaultDataSource.Factory(context)`（不写 SimpleCache）
- `TextureView`：`player.setVideoTextureView(textureView)`
- 视频尺寸变化时调用 `VideoTransformHelper.applyFitWidth`

- [ ] **Step 1: 整文件替换为 ExoPlayer 实现**

```java
package com.example.douyin.player;

import android.content.Context;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import com.example.douyin.cache.ExoMediaCache;

public class VideoPlayerController {

    private final TextureView textureView;
    private final Context appContext;
    @Nullable private ExoPlayer player;
    @Nullable private String videoUrl;
    private boolean playWhenReady;
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
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_READY && playWhenReady && player != null) {
                player.play();
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            // 保持与旧 MediaPlayer.onError 类似：吞掉错误，避免崩溃
        }
    };

    public VideoPlayerController(TextureView textureView) {
        this.textureView = textureView;
        this.appContext = textureView.getContext().getApplicationContext();
        textureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyVideoTransform();
            }
        });
    }

    public void setVideoUrl(String url) {
        this.videoUrl = url;
        preparePlayer();
    }

    public void play() {
        playWhenReady = true;
        if (player != null) {
            player.play();
        }
    }

    public void pause() {
        playWhenReady = false;
        if (player != null) {
            player.pause();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
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
        releaseInternal();
    }

    private void preparePlayer() {
        if (videoUrl == null) {
            return;
        }
        releaseInternal();

        player = new ExoPlayer.Builder(appContext).build();
        player.addListener(playerListener);
        player.setVideoTextureView(textureView);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setMediaSource(buildMediaSource(videoUrl));
        player.prepare();
        player.setPlayWhenReady(playWhenReady);
    }

    private ProgressiveMediaSource buildMediaSource(String url) {
        MediaItem mediaItem = MediaItem.fromUri(url);
        if (MediaUrlHelper.isRemoteHttpUrl(url)) {
            return new ProgressiveMediaSource.Factory(
                    ExoMediaCache.get(appContext).getCacheDataSourceFactory()
            ).createMediaSource(mediaItem);
        }
        return new ProgressiveMediaSource.Factory(
                new DefaultDataSource.Factory(appContext)
        ).createMediaSource(mediaItem);
    }

    private void releaseInternal() {
        if (player != null) {
            player.removeListener(playerListener);
            player.clearVideoTextureView(textureView);
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

Run:

```bash
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL（此时 Feed 仍走旧 `resolveVideoForPlayback`，下一 Task 接线）

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/douyin/player/VideoPlayerController.java
git commit -m "$(cat <<'EOF'
feat: switch VideoPlayerController to ExoPlayer with cache datasource

EOF
)"
```

---

### Task 5: 接线 Feed 预取与播放入口

**Files:**
- Modify: `app/src/main/java/com/example/douyin/feed/FeedFragment.java`
- Modify: `app/src/main/java/com/example/douyin/feed/VideoPageFragment.java`

**Interfaces:**
- Consumes: `ExoMediaCache.prefetch(String)`；`VideoPlayerController.setVideoUrl/play/pause/release`
- Produces: Feed 滑动仍调用 `prefetchAround`；播放页直接设原始 `video.videoUrl`，不再等待整文件下载回调

- [ ] **Step 1: 改 FeedFragment 预取到 ExoMediaCache**

将 import：

```java
import com.example.douyin.cache.MediaCacheManager;
```

改为：

```java
import com.example.douyin.cache.ExoMediaCache;
import com.example.douyin.cache.MediaCacheManager;
```

将 `prefetchAround` / `prefetchAt` 改为：

```java
private void prefetchAround(int position) {
    ExoMediaCache exoCache = ExoMediaCache.get(requireContext());
    MediaCacheManager imageCache = MediaCacheManager.get(requireContext());
    prefetchAt(exoCache, imageCache, position);
    prefetchAt(exoCache, imageCache, position + 1);
    prefetchAt(exoCache, imageCache, position - 1);
}

private void prefetchAt(ExoMediaCache exoCache, MediaCacheManager imageCache, int position) {
    VideoDto video = pagerAdapter.getVideo(position);
    if (video == null) {
        return;
    }
    exoCache.prefetch(video.videoUrl);
    if (video.coverUrl != null && !video.coverUrl.isEmpty()) {
        imageCache.prefetchImage(video.coverUrl);
    }
}
```

其余 `onPageSelected` / `loadFeed` 成功后调用 `prefetchAround` 的位置不变。

- [ ] **Step 2: 简化 VideoPageFragment.preparePlayback**

删除对 `MediaCacheManager` / `CacheCallback` 的依赖。将 `preparePlayback` 替换为：

```java
private void preparePlayback() {
    if (playerController == null || video == null) {
        return;
    }
    playbackReady = true;
    playerController.setVideoUrl(video.videoUrl);
    if (isPageActive() && isResumed() && isShowingVideo() && !userPaused) {
        playerController.play();
    }
    updatePauseIndicator();
}
```

删除：

```java
import com.example.douyin.cache.MediaCacheManager;
```

同步更新注释中「MediaPlayer」措辞为 ExoPlayer / preparing（`togglePlayPauseByTap` / `updatePauseIndicator` 旁注释即可）：

```java
// Drive from user intent, not Player.isPlaying(): prepare/start is async.
```

```java
// true while ExoPlayer is still buffering / before playback starts.
```

- [ ] **Step 3: 编译**

Run:

```bash
.\gradlew.bat :app:compileDebugJavaWithJavac
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/douyin/feed/FeedFragment.java \
  app/src/main/java/com/example/douyin/feed/VideoPageFragment.java
git commit -m "$(cat <<'EOF'
feat: wire feed prefetch and playback to ExoMediaCache

EOF
)"
```

---

### Task 6: 精简 MediaCacheManager（仅图片）

**Files:**
- Modify: `app/src/main/java/com/example/douyin/cache/MediaCacheManager.java`

**Interfaces:**
- Consumes: 仍被 `CoverImageLoader` / `FeedFragment.prefetchImage` / `DouyinApp` 使用
- Produces: 保留
  - `get(Context)`
  - `prefetchImage(String)`
  - `getCachedImageFile(String)`
  - 删除公共 API：`prefetchVideo`、`resolveVideoForPlayback`、`CacheCallback`（若无其它引用）
  - 删除视频目录 / `MAX_VIDEO_CACHE_BYTES` / `videoTasks` / `CacheType.VIDEO` 分支

- [ ] **Step 1: 确认无残留引用**

Run（仓库根目录）：

```bash
rg "prefetchVideo|resolveVideoForPlayback|CacheCallback" app/src
```

Expected: 仅 `MediaCacheManager.java` 自身（或零命中，若已删）

- [ ] **Step 2: 将 MediaCacheManager 收敛为图片专用**

目标结构要点（完整实现时按此精简，勿留死代码）：

```java
public final class MediaCacheManager {
    private static final long MAX_IMAGE_CACHE_BYTES = 64L * 1024L * 1024L;

    // 删除 CacheCallback 接口

    public void prefetchImage(String remoteUrl) { /* 现有逻辑，仅 IMAGE */ }

    public File getCachedImageFile(String remoteUrl) { /* 现有逻辑 */ }

    // enqueueDownload / downloadToCache / trimCache 仅处理 imageCacheDir
    // 删除 videoCacheDir、videoTasks、CacheType 枚举（或仅 IMAGE 常量路径）
}
```

保留：OkHttp 下载、`.tmp` rename、按 `lastModified` 淘汰、MD5 key、`AppExecutors.network`。

- [ ] **Step 3: 编译 + 单测**

Run:

```bash
.\gradlew.bat :app:compileDebugJavaWithJavac :app:testDebugUnitTest --tests com.example.douyin.player.MediaUrlHelperTest
```

Expected: BUILD SUCCESSFUL，MediaUrlHelperTest PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/douyin/cache/MediaCacheManager.java
git commit -m "$(cat <<'EOF'
refactor: keep MediaCacheManager for image cache only

EOF
)"
```

---

### Task 7: 真机/模拟器验收清单

**Files:** 无代码变更（验收）

**Interfaces:** 无

- [ ] **Step 1: 安装 Debug 包**

Run:

```bash
.\gradlew.bat :app:installDebug
```

Expected: 安装成功

- [ ] **Step 2: 手动验收（勾选）**

| # | 场景 | 期望 |
|---|------|------|
| 1 | 打开 Feed 第一条远程视频 | 可边缓冲边播，无需等整文件下完 |
| 2 | 快速上滑到下一条 | 首屏开播更快（±1 已预取约 2MB） |
| 3 | 返回已看过的远程视频 | 命中 SimpleCache，起播更快 |
| 4 | 本地种子/file 视频（若有） | 正常播放，不走预取 |
| 5 | 点击暂停/继续、左右滑到作者页再回来 | 暂停指示与播放状态正确 |
| 6 | 循环播放 | 单条视频循环无黑屏卡死 |
| 7 | 封面图 | 列表/页面封面仍正常显示 |

- [ ] **Step 3: 若验收通过，无需额外 commit；若修 bug，单独 commit 并注明场景编号**

---

## Self-Review

1. **Spec coverage**
   - ExoPlayer 流式 Cache：Task 3 + 4
   - ±1 预取：Task 5（FeedFragment）
   - 前 2MB：Task 2 常量 + Task 3 CacheWriter `setLength`
   - 保留封面图缓存：Task 5 仍调 `prefetchImage`；Task 6 精简视频 API
   - 本地 URL 不缓存：Task 2 + Task 3/4 分支
   - 不做播放器池：全计划未引入

2. **Placeholder scan：** 无 TBD /「类似 Task N」；关键步骤含完整代码与命令

3. **Type consistency：** `ExoMediaCache.get` / `prefetch` / `getCacheDataSourceFactory`；`MediaUrlHelper.PREFETCH_BYTES` / `shouldPrefetch` / `isRemoteHttpUrl`；`VideoPlayerController` 对外方法名与现 Feed 调用一致

---

## Out of Scope（明确不做）

- ExoPlayer 播放器实例池 / 预创建下一页 Player
- 旧目录 `cacheDir/oss_media/video` 自动清理
- 自适应码率 / HLS / DASH（当前 Progressive MP4）
- 替换封面图加载为 Coil/Glide
