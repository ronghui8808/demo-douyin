# ExoPlayer 流式 Cache + 2MB 预取 设计说明

**日期：** 2026-09-21  
**状态：** 已确认（已实现于分支 `20260921-exoplayer`）  
**实现计划：** `docs/superpowers/plans/2026-09-21-exoplayer-streaming-cache.md`  
**范围：** Feed 竖滑视频改为 ExoPlayer + SimpleCache 边下边播；当前/相邻条目预取前 2MB。封面图仍走原图片缓存。

## 背景

改造前 Feed 视频链路为：

1. `FeedFragment` 对 position ±1 调用 `MediaCacheManager.prefetchVideo`，**整文件**经 OkHttp 下载到 `cacheDir/oss_media/video/`
2. `VideoPageFragment` 经 `resolveVideoForPlayback` 等待本地文件就绪后，再把 `file://` 交给 `MediaPlayer`
3. 未下完不能开播；流量与磁盘占用大；与「短视频快速滑过」场景不匹配

目标改为行业常见的 **流式缓存**：播放器边拉边播，磁盘只保留可复用的分片/前缀；预取只拉起播所需的前若干字节。

## 目标

1. 远程视频可边缓冲边播，无需等整文件下载完成。
2. Feed 滑动时对 **当前、上一条、下一条** 预取远程视频前 **2MB**，缩短下一条首屏等待。
3. 再次进入已缓存片段时命中 `SimpleCache`，起播更快。
4. 本地路径（`file://` / 非 http(s)，含种子视频）正常播放，**不**进入 SimpleCache、**不**预取。
5. 封面图加载与预取行为保持不变。
6. `VideoPlayerController` 对外 API 不变，降低 Fragment 改动面。

## 非目标

- ExoPlayer 播放器实例池 / 预创建下一页 Player
- HLS / DASH / 自适应码率（当前 Progressive MP4）
- 迁移或自动清理旧目录 `oss_media/video`（可自然闲置）
- 替换封面图方案（Coil/Glide 等）
- 改 Publish 预览用的 `VideoView`
- 播放失败的用户可见重试 UI（与旧 MediaPlayer 吞错 parity，可后续增强）

## 方案选型

采用 **方案 B：ExoPlayer + Media3 SimpleCache + CacheWriter 预取前 2MB**。

| 方案 | 说明 | 结论 |
|------|------|------|
| A. 仅换 ExoPlayer + Cache，不做 ±1 预取 | 改动最小，上下滑起播仍偏慢 | 未采用 |
| **B. Exo + Cache + ±1 预取 2MB** | 接近原预热窗口，省流量 | **采用** |
| C. 播放器池 + 预创建 | 体验更好，但超出现阶段范围 | 明确不做 |

备选（未采用）：继续整文件预下载（保留 MediaPlayer）——首播等待长；自研 Range 分片缓存——与 Exo Cache 重复且维护成本高。

## 架构

```
FeedFragment.prefetchAround(pos)
        │
        ├─ ExoMediaCache.prefetch(url)     // CacheWriter，前 2MB
        └─ MediaCacheManager.prefetchImage // 封面，不变

VideoPageFragment.preparePlayback()
        │
        └─ VideoPlayerController.setVideoUrl(原始 url)
                │
                ├─ http(s) → ProgressiveMediaSource(CacheDataSource.Factory)
                └─ 本地     → ProgressiveMediaSource(DefaultDataSource.Factory)
```

- **共享缓存：** `ExoMediaCache` 单例持有 `SimpleCache` 与 `CacheDataSource.Factory`；预取与播放写同一缓存。
- **应用启动：** `DouyinApp` 预热 `ExoMediaCache.get(this)`（与 `MediaCacheManager` 并列）。

## 关键约束

| 项 | 值 |
|----|-----|
| 语言 | Java（不引入 Kotlin） |
| Media3 | `1.5.1`（exoplayer / database / datasource） |
| 预取窗口 | `position`、`position+1`、`position-1` |
| 预取字节 | `MediaUrlHelper.PREFETCH_BYTES = 2L * 1024L * 1024L` |
| 视频缓存上限 | `512L * 1024L * 1024L`，LRU（`LeastRecentlyUsedCacheEvictor`） |
| 视频缓存目录 | `context.getCacheDir()/exo_video` |
| 图片缓存上限 | 仍为 64MB（`MediaCacheManager`） |
| 可预取 URL | 仅 `http://` / `https://`（trim 后） |

## 模块职责

### MediaUrlHelper

- `normalize` / `isRemoteHttpUrl` / `shouldPrefetch`
- `PREFETCH_BYTES` 常量唯一来源
- 纯逻辑，JVM 单测覆盖（`MediaUrlHelperTest`）

### ExoMediaCache

- `get(Context)` 单例；`StandaloneDatabaseProvider` + `SimpleCache`
- `getCacheDataSourceFactory()`：上游 `DefaultHttpDataSource`，`FLAG_IGNORE_CACHE_ON_ERROR`
- `prefetch(url)`：
  - 非远程 → no-op
  - 同 URL `inflight` 去重
  - `AppExecutors.network` 上 `CacheWriter` + `DataSpec.setLength(PREFETCH_BYTES)`
  - 失败吞掉，不阻断播放

### VideoPlayerController

对外保持：

- `VideoPlayerController(TextureView)`
- `setVideoUrl` / `play` / `pause` / `isPlaying` / `togglePlayPause` / `release`

行为：

- `ExoPlayer`，`REPEAT_MODE_ONE`，`setVideoTextureView`
- `playWhenReady`：未 ready 时记标志，ready 后可自动播
- 远程走 CacheDataSource；本地走 DefaultDataSource
- 尺寸变化仍用 `VideoTransformHelper.applyFitWidth`
- `onPlayerError` 吞错（对齐旧 MediaPlayer）

### FeedFragment

- `prefetchAround`：视频 → `ExoMediaCache.prefetch`；封面 → `MediaCacheManager.prefetchImage`
- 触发点不变：列表加载成功、`onPageSelected`

### VideoPageFragment

- `preparePlayback`：直接 `setVideoUrl(video.videoUrl)`，不再 `resolveVideoForPlayback`
- 暂停指示仍以 `userPaused` 为准，不依赖 `isPlaying()`（异步准备阶段）

### MediaCacheManager（改造后）

- **仅图片：** `prefetchImage` / `getCachedImageFile`
- 删除：`prefetchVideo`、`resolveVideoForPlayback`、`CacheCallback`、视频目录与 VIDEO 分支

## 行为规则

### 播放

1. Fragment 传入原始 `video.videoUrl`（远程或本地）。
2. 远程：Exo 经 CacheDataSource 读/写 `exo_video`；已有分片则优先用缓存。
3. 本地：不经 SimpleCache。
4. 循环：单条 `REPEAT_MODE_ONE`。

### 预取

1. 仅对远程 URL；本地与空 URL 跳过。
2. 窗口为当前 ±1，与 `ViewPager2` offscreen≈1 对齐。
3. 每次最多约 2MB；内容短于 2MB 则下完即止。
4. 预取与播放并发同一 URL 时，靠 cache 层与 `inflight` 去重，不重复整段任务。

### 封面

- 逻辑与改造前一致；不依赖 Exo。

## 验收标准

| # | 场景 | 期望 |
|---|------|------|
| 1 | Feed 第一条远程视频 | 可边缓冲边播，无需等整文件下完 |
| 2 | 快速上滑下一条 | 首屏开播更快（±1 已预取约 2MB） |
| 3 | 返回已看过的远程视频 | 命中 SimpleCache，起播更快 |
| 4 | 本地种子 / `file://` 视频 | 正常播放，不走预取 |
| 5 | 暂停/继续、滑到作者页再回 | 暂停指示与播放状态正确 |
| 6 | 循环播放 | 单条循环无黑屏卡死 |
| 7 | 封面图 | 列表/页面封面仍正常 |

## 风险与后续

| 项 | 说明 | 建议 |
|----|------|------|
| 真机验收 | 实现期无连接设备时无法完成 Task 7 | 合并前人工 `installDebug` + 上表 |
| 主线程初始化 SimpleCache | `DouyinApp.onCreate` 打开 DB | 缓存变大后可迁后台 |
| 预取/播放失败静默 | 与旧行为一致 | 需要时可加 debug 日志或 Toast |
| 旧 `oss_media/video` | 不再写入/淘汰 | 可忽略或用户清应用缓存 |
| `setVideoUrl` 未 normalize | `isRemoteHttpUrl` 会 trim，MediaItem 用原串 | 极端空白 URL 可统一 normalize |

## 主要文件

| 路径 | 职责 |
|------|------|
| `player/MediaUrlHelper.java` | URL 规则与 2MB 常量 |
| `cache/ExoMediaCache.java` | SimpleCache + 预取 |
| `player/VideoPlayerController.java` | ExoPlayer 封装 |
| `feed/FeedFragment.java` | ±1 预取接线 |
| `feed/VideoPageFragment.java` | 直接设 URL 播放 |
| `cache/MediaCacheManager.java` | 仅图片缓存 |
| `DouyinApp.java` | 启动预热 |
| `test/.../MediaUrlHelperTest.java` | URL / 常量单测 |
