# Jetpack MVVM + ExoPlayer 改造设计

**日期：** 2026-09-11  
**状态：** 已审阅通过  
**前置：** 现有仿抖音 Java 客户端（Retrofit 本地拦截器 + Room + MediaPlayer）

**语言说明：** 本轮坚持纯 Java。ViewModel / LiveData / ViewBinding / Lifecycle / Room / Media3 在 Java 下均为一等支持；DataStore 通过内存缓存规避拦截器阻塞。Kotlin / Coroutines / Compose 留作后续独立演进，不纳入本设计。

## 1. 背景与目标

在现有工程上引入典型 Jetpack 能力，完善架构与播放体验，同时控制改动面。

**目标：**

1. 核心页面改为 **MVVM**（ViewModel + LiveData + ViewBinding）
2. 视频播放由 **MediaPlayer** 升级为 **Media3 ExoPlayer**
3. 鉴权 Token 存储由 SharedPreferences 迁移为 **Preferences DataStore**
4. 播放器与页面生命周期通过 **DefaultLifecycleObserver** 对齐
5. 保持 **纯 Java**、现有本地 API / Room / 拍摄链路可用

**非目标（本轮明确不做）：**

- Navigation Component
- Hilt / Dagger
- CameraX
- Paging
- Kotlin / Coroutines / Compose
- 独立后端或 API 契约大改

## 2. 约束与决策摘要

| 决策点 | 选择 |
|--------|------|
| 主目标 | 架构现代化 + ExoPlayer |
| 语言 | 纯 Java 11 |
| 导航 | 保留手动 Fragment / Activity 切换 |
| 依赖组装 | 手动 `new Repository` + `ViewModelProvider.Factory` |
| 范围 | MVVM + ExoPlayer + DataStore + LifecycleObserver |
| 落地顺序 | 先基建，再按页面竖切（方案 1） |

## 3. 目标架构

```
Activity / Fragment (ViewBinding)
        │  observe LiveData
        ▼
   XxxViewModel  ← ViewModelProvider + 手动 Factory
        │
        ▼
   XxxRepository（保留；ApiCallback 结果由 ViewModel 转为 LiveData）
        │
        ▼
 Retrofit / LocalApi / Room / DataStore / MediaCache
```

### 3.1 职责边界

| 单元 | 职责 | 禁止 |
|------|------|------|
| Fragment / Activity | 绑视图、手势、observe 状态、注册 Player 生命周期 | 直接 `enqueue` 业务 API；持有长期业务状态 |
| ViewModel | `LiveData` 状态；调用 Repository；映射错误文案 | 持有 `View`；持有 ExoPlayer；持有非 Application `Context` |
| VideoPlayerController | Media3 ExoPlayer 封装；`DefaultLifecycleObserver`；Surface / 变换 | 进入 ViewModel；做列表分页 |
| Repository | 数据入口；现有 Retrofit 回调模型 | 依赖具体 Fragment |
| TokenStore | DataStore 持久化 + 内存缓存供同步读 | 在 OkHttp 拦截器中阻塞读 DataStore |

### 3.2 播放器边界（关键）

- `VideoPageFragment` **拥有** `VideoPlayerController`，与 ViewPager2 页面生命周期绑定。
- `FeedViewModel` 只负责 feed 列表、加载/错误、点赞等数据状态。
- **不**把 Player 放入 ViewModel，避免横滑个人页、离屏回收时生命周期错乱。

### 3.3 导航

- `MainActivity` 底部 Tab、Splash / Login / 发布等 Intent 跳转 **保持现状**。

## 4. 数据流与状态模型

### 4.1 UI 状态

页面使用不可变状态对象，例如：

```java
public final class FeedUiState {
    public final boolean loading;
    public final String errorMessage;   // null = 无错误
    public final List<VideoDto> videos; // 空列表 = 空态
}
```

预期 ViewModel：

- `FeedViewModel`：`LiveData<FeedUiState>`；点赞后更新对应 item
- `LoginViewModel` / `RegisterViewModel`：loading、错误文案、成功导航事件
- `CommentViewModel`：评论列表 + 发送中状态
- `ProfileViewModel`：用户资料 + 作品列表

一次性事件（Toast、跳转登录）使用 **SingleLiveEvent**（或带消费标记的 `MutableLiveData`），避免配置变更后重复触发。

### 4.2 调用链

1. Fragment `observe` → 渲染 loading / empty / error / content  
2. 用户操作 → `viewModel.loadFeed()` / `toggleLike(id)` 等  
3. ViewModel 调用 Repository 现有 `ApiCallback`  
4. 回调中 `postValue` 新状态（线程模型保持：Retrofit 回调 + 必要时 `AppExecutors`）

Repository **对外签名本轮尽量不变**，降低回归风险。

### 4.3 错误处理

- 网络 / 业务错误 → `UiState.errorMessage`，UI 展示可点重试  
- 播放失败 → 由 `VideoPlayerController` / 页面局部处理（可选 Toast），**不**塞进 `FeedViewModel`

## 5. DataStore（TokenStore）

- 底层改为 Preferences DataStore（名：`douyin_auth`）
- **纯 Java 接入：** 使用 `datastore-preferences-rxjava3` + `RxPreferenceDataStoreBuilder`（不引入 Kotlin 源码）；读写在后台完成，对外仍提供同步 API
- 对外 API 保持：`saveToken` / `getToken` / `getUserId` / `isLoggedIn` / `clear`
- **同步读路径：** 进程内内存缓存；写入时同步更新缓存
- `AuthHeaderInterceptor` 与快速路径 **只读内存缓存**，禁止在拦截器线程 `blockingGet` DataStore
- 启动时：`DouyinApp` 异步 hydrate；`SplashActivity` 在 hydrate 完成后再分流
- `SeedDataInitializer` 使用的 SharedPreferences **本轮可保留**（与鉴权无关）
- 一次性迁移：若旧 SharedPreferences `douyin_auth` 仍有数据，hydrate 前读入并写入 DataStore，然后清除旧 prefs（避免双源）

## 6. ExoPlayer（Media3）

- 依赖：`media3-exoplayer`（及按需 `media3-datasource`）
- `VideoPlayerController` 对外 API 保持：`setVideoUrl` / `play` / `pause` / `togglePlayPause` / `release` / `isPlaying`
- 内部：`ExoPlayer` + `Player.Listener`；循环播放；画面继续使用 `TextureView` + 现有 `VideoTransformHelper`
- 实现 `DefaultLifecycleObserver`：`onPause` → pause，`onDestroy` → release；由 `VideoPageFragment` 对 `getViewLifecycleOwner()` 注册
- 缓存：优先继续使用 `MediaCacheManager` 提供的可播放 URL；本轮以「能播 + 生命周期正确」为准，不强行引入第二套 Media3 Cache 与现有方案并行
- ViewPager2：未选中页 pause；选中且 `playbackReady` 才 play（对齐现逻辑）
- 多实例：注意音频焦点策略，避免多页同时出声（与现行为一致即可）

## 7. 改造分期

| 阶段 | 内容 | 可验证结果 |
|------|------|------------|
| P0 基建 | ViewBinding；lifecycle-viewmodel / livedata；Media3；DataStore；按需 Factory 基元 | 工程编译通过 |
| P1 播放器 | `VideoPlayerController` → ExoPlayer + LifecycleObserver；`VideoPageFragment` ViewBinding 接入 | Feed 滑动播放 / 暂停 / 循环正常 |
| P2 DataStore | `TokenStore` 迁移；Splash hydrate；拦截器读缓存 | 冷启动登录态正确 |
| P3 Feed MVVM | `FeedViewModel` + `FeedUiState`；`FeedFragment` 瘦身 | 加载 / 错误 / 点赞；旋转后列表仍在 |
| P4 Auth MVVM | Login / Register ViewModel + ViewBinding | 登录注册主路径不变且可单测 |
| P5 Comment / Profile | 对应 ViewModel；BottomSheet / Profile 观察状态 | 评论与个人页行为不变 |
| P6 Publish（轻量） | ViewBinding；发布结果状态可控；不重构 Camera2 | 发布成功仍可回刷 Feed |

每阶段结束应可安装运行。

## 8. 文件清单

### 新建

- `app/src/main/java/com/example/douyin/feed/FeedViewModel.java`
- `app/src/main/java/com/example/douyin/feed/FeedUiState.java`
- `app/src/main/java/com/example/douyin/auth/LoginViewModel.java`
- `app/src/main/java/com/example/douyin/auth/LoginUiState.java`
- `app/src/main/java/com/example/douyin/auth/RegisterViewModel.java`
- `app/src/main/java/com/example/douyin/auth/RegisterUiState.java`
- `app/src/main/java/com/example/douyin/comment/CommentViewModel.java`
- `app/src/main/java/com/example/douyin/comment/CommentUiState.java`
- `app/src/main/java/com/example/douyin/profile/ProfileViewModel.java`
- `app/src/main/java/com/example/douyin/profile/ProfileUiState.java`
- `app/src/main/java/com/example/douyin/util/SingleLiveEvent.java`
- 按需：`app/src/main/java/com/example/douyin/viewmodel/DouyinViewModelFactory.java`
- 单测：`FeedViewModelTest`、`TokenStoreTest`（路径按现有 `app/src/test` 约定）

### 修改

- `gradle/libs.versions.toml`、`app/build.gradle.kts`
- `player/VideoPlayerController.java`
- `feed/FeedFragment.java`、`feed/VideoPageFragment.java`
- `network/TokenStore.java`、`DouyinApp.java`、`auth/SplashActivity.java`
- `auth/LoginActivity.java`、`auth/RegisterActivity.java`
- `comment/CommentBottomSheet.java`
- `profile/ProfileFragment.java`
- 对应 layout 随 ViewBinding 逐页迁移

### 基本不动

- `LocalApiInterceptor`、Room DAO / Entity
- `CameraRecorder` 与拍摄主流程
- `MainActivity` 底部 Tab 手动切换逻辑
- `MediaCacheManager`（除非接 ExoPlayer 时必须微调 URL 交付）

## 9. 依赖增量（预期）

在现有 `lifecycle = 2.8.7` 等基础上增加：

- `androidx.lifecycle:lifecycle-viewmodel`
- `androidx.lifecycle:lifecycle-livedata`
- `androidx.lifecycle:lifecycle-common`（若 Observer 需要）
- ViewBinding（`buildFeatures.viewBinding = true`）
- `androidx.media3:media3-exoplayer`（版本在实现计划中钉死）
- `androidx.datastore:datastore-preferences` + `datastore-preferences-rxjava3`（纯 Java 读写）

测试：`androidx.arch.core:core-testing`（InstantTaskExecutorRule）按需加入。

## 10. 测试策略

- **JVM：** `FeedViewModel`（loading → success / error）、`TokenStore`（save / clear / 缓存可读）
- **手动 / 仪器：** ExoPlayer 滑动切换、前后台暂停、登录冷启动、发布回刷
- 本轮不强制重 UI 自动化覆盖播放器

## 11. 成功标准

1. 纯 Java；手动导航；手动 Factory 组装  
2. 已迁移页面无业务向 `findViewById`（改用 ViewBinding）  
3. Feed / Auth / Comment / Profile 业务状态位于 ViewModel + LiveData  
4. 视频播放为 Media3 ExoPlayer，并随页面 Lifecycle 暂停 / 释放  
5. Token 存 DataStore；拦截器不阻塞；杀进程重进登录态正确  
6. 主路径可用：浏览 → 点赞 → 评论 → 登录 → 发布  
7. 至少 `FeedViewModel` + `TokenStore` 具备 JVM 单测  

## 12. 风险与对策

| 风险 | 对策 |
|------|------|
| DataStore 异步 vs 拦截器需同步读 | 内存缓存 + 启动 hydrate |
| ExoPlayer + TextureView + 自定义 fit | 保留 `VideoTransformHelper`，先对齐现表现 |
| ViewPager2 多 Player 实例 | 严格 offscreen pause；音频焦点策略与现行为一致 |
| 同一页面改两轮 | 坚持 P0→P1 基建后再竖切，避免 Feed 反复返工 |

## 13. 后续（本设计范围外）

若本轮完成后仍要增强，可另开 spec：Navigation、Hilt、Paging、CameraX、Kotlin 渐进迁移、Media3 Cache 与现有缓存统一等。
