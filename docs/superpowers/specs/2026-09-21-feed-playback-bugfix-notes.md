# Feed 发布 / 播放闪退 排查与修复记录

**日期：** 2026-09-21  
**分支：** `20260921-exoplayer`  
**关联：** ExoPlayer 流式缓存改造后的真机验收问题  
**状态：** 已修复（本地未合入前需重新安装验证）

---

## 背景

在完成「MediaPlayer 整文件缓存 → ExoPlayer + SimpleCache + 2MB 预取」后，真机验收出现两类严重问题：

1. **录制视频过程中 / 结束后闪退**
2. **发布成功回到 Feed：新视频不出现，旧视频像重复，随后闪退**

日志先后出现 `NullPointerException` 与 `OutOfMemoryError`，表面像「发布链路坏了」，实际是 **Activity/Fragment 生命周期** 与 **多 ExoPlayer 并发解码** 叠加。

---

## Bug 1：录制返回后 NPE 闪退

### 现象

- 从拍摄 / 发布流程回到 `MainActivity` 时进程崩溃
- 日志关键帧：

```
java.lang.NullPointerException:
  Attempt to invoke virtual method
  'void androidx.viewpager2.widget.ViewPager2.setVisibility(int)'
  on a null object reference
    at FeedFragment.showLoading(...)
    at FeedFragment.loadFeed(...)
    at FeedFragment.refreshFeed(...)
    at MainActivity.refreshFeedIfVisible(...)
    at BottomNavigationView.onItemReselected
    at MainActivity.onCreate → setSelectedItemId(...)
```

同时可见进程曾被系统杀掉（`process died`），随后冷启动恢复。

### 根因

录制期间进程被杀 → `MainActivity` 带 `savedInstanceState` 重建：

1. `onCreate` 调用 `bottomNav.setSelectedItemId(首页)`
2. 若当前已是首页，触发 **`onItemReselected`** → `refreshFeedIfVisible()`
3. `FragmentManager` 能 `findFragmentByTag` 到 `FeedFragment`（`isAdded()==true`）
4. 但此时 **View 尚未 `onViewCreated`**，`viewPager == null`
5. `refreshFeed()` → `showLoading()` 直接 NPE

与 ExoPlayer 改造无直接关系；是「恢复时过早刷新 Feed」的既有隐患，在进程重启路径上被放大。

### 修复

| 文件 | 改动 |
|------|------|
| `FeedFragment` | `refreshFeed()` 增加 `getView() != null && viewPager != null` 守卫；`onDestroyView` 清空 View 引用 |
| `MainActivity` | 恢复时若 Fragment 丢失则补 `showFragmentForNavItem`；不依赖 reselect 做首屏挂载 |

### 教训

- 对外暴露的 `refreshXxx()` **不能只判断 `isAdded()`**，必须确认 View 已就绪。
- `BottomNavigationView.setSelectedItemId` 在恢复路径上可能触发 **reselect**，不要假设只会走 select → 挂 Fragment。

---

## Bug 2：发布后内容错乱 + OOM 闪退

### 现象

- 发布成功回到 Feed，**新视频不在列表首位（或看不到）**，旧内容像「重复」
- 快速滑动时卡顿，随后崩溃
- 日志关键信息：

```
OutOfMemoryError: Failed to allocate ~1.2MB ... growth limit 268435456
  at ParsableByteArray.<init>
  at Mp4Extractor.readAtomHeader
  at ProgressiveMediaPeriod$ExtractingLoadable.load

Heap: 255MB/256MB
同时存在多路：avcD_757 / avcD_758 / avcD_759 / hvcD_760 + 多路 AudioTrack
AidlBufferPool: 单路解码器缓冲约 30MB+ 量级
```

### 根因 A：多路 ExoPlayer 同时 prepare（OOM）

改造后每个 `VideoPageFragment` 在 `bindVideoPage` 里立刻：

```text
new VideoPlayerController → setVideoUrl → ExoPlayer.prepare()
```

`ViewPager2` `offscreenPageLimit=1` 时，当前 ±1 页都会创建并 **prepare**，离屏页虽未必 `RESUMED`，但解码器 / 缓冲已占内存。快速滑动时瞬时多路 AVC/HEVC，256MB 堆顶满 → `Mp4Extractor` 再分配失败。

旧 `MediaPlayer` 相对轻，问题不突出；ExoPlayer + 硬件解码缓冲后成为硬瓶颈。

### 根因 B：Adapter 用 position 当稳定 ID（内容错乱）

`FeedPagerAdapter`：

- `createFragment(position)` → `VideoPageFragment.newInstance(position)`
- 未重写 `getItemId` / `containsItem`（默认 ID = position）
- Fragment 在 `onCreate` 缓存 `pagePosition`，并在首次绑定后 `videoBound=true` 不再换数据

发布后 `refreshFeed` → `submitList` → `notifyDataSetChanged()`：

- 同一 position 仍对应同一 Fragment 实例
- 页面仍展示 **旧 video**，表现为「重复 / 没有新稿」
- 库内数据按 `created_at DESC` 实际已包含新视频，是 **UI 层 ID 策略错误**

### 修复

| 文件 | 改动 |
|------|------|
| `VideoPageFragment` | 参数改为 `videoId`；**仅 `onResume` 且当前页** 才 create/prepare；`onPause` **完整 release**（不只 pause） |
| `FeedPagerAdapter` | `getItemId` / `containsItem` 使用 `video.id`；`createFragment` 传 `videoId` |
| `FeedFragment` | `isActiveVideo(videoId)` / `getVideoById`；刷新成功后 `setCurrentItem(0)` |
| `VideoPlayerController` | 收紧 `DefaultLoadControl` 缓冲窗口，降低单实例内存 |

目标行为：**同一时刻最多一路解码**；列表变更按视频 ID 绑定 Fragment，发布刷新后第一条为最新稿。

### 教训

- Feed + ExoPlayer：**离屏页只做 UI，不要 prepare**；离开可见态应 `release()` 释放 MediaCodec。
- `FragmentStateAdapter` 使用 `notifyDataSetChanged()` 时，**必须** 用业务主键实现 `getItemId` + `containsItem`，不要用 position。
- 页面参数用 **稳定业务 ID**（`videoId`），不要用会随刷新漂移的 position。
- OOM 日志里若同时出现多路 `MediaCodec` id，优先怀疑「多 Player 生命周期」，而不是单纯「视频文件太大」。

---

## 时间线（简）

| 顺序 | 事件 |
|------|------|
| 1 | ExoPlayer 流式缓存方案 B 落地 |
| 2 | 录制闪退 → 定位 NPE（恢复时 refresh 过早）→ 修 `FeedFragment` / `MainActivity` |
| 3 | 发布后错乱 + 滑动闪退 → 日志见 OOM + 多解码器 |
| 4 | 定位离屏 prepare + position ID → 修播放器生命周期与 Adapter ID |

---

## 验证建议

重新安装 Debug 包后：

1. 录制 → 发布 → 回 Feed：新视频应在第一条，文案/画面正确  
2. 快速上下滑 10+ 条：不应再因多解码器 OOM 闪退  
3. 录制中切后台 / 低内存机型：回首页不应再因 `viewPager==null` NPE  
4. 左右滑作者页再回视频：暂停 / 续播正常  

---

## 相关文件

- `feed/FeedFragment.java`
- `feed/FeedPagerAdapter.java`
- `feed/VideoPageFragment.java`
- `player/VideoPlayerController.java`
- `MainActivity.java`
- 设计说明：`docs/superpowers/specs/2026-09-21-exoplayer-streaming-cache-design.md`
- 实现计划：`docs/superpowers/plans/2026-09-21-exoplayer-streaming-cache.md`
