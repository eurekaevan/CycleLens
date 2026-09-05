# CycleLens 架构

本文档描述当前已实现的 Stage 6C 边界。核心原则是：牌序、展示资料、Overlay、采集几何和帧获取各自只有一个权威数据源。

## 总览

```text
                            CycleLensApplication
                  ┌──────────┼──────────┐
                  │          │          │          │
             MatchSession   CardCatalog   Overlay config  Capture state
                  │          │          │          │
            CycleTracker    local assets  StateFlow       StateFlow
                  │          │          │          │
              cycle-core    Activity/Overlay presentation │
                                                          │
Activity ────────────────────────────────────────────┘
OverlayService ─→ MatchSession + Catalog + Overlay config
CaptureService ─→ Capture state + SamplingGate + AnalysisFrameDescriptor
```

`CycleLensApplication` 在进程生命周期内创建并持有这些共享对象，因此 Activity 配置变更不会创建新的 MatchSession。这不是磁盘持久化；进程重建后会得到新 session。

## `cycle-core`

`cycle-core` 是纯 Kotlin/JVM 模块，只包含：

- `CardId`
- `CycleRules` / `StandardCycleRules`
- `CycleTracker`

`CycleTracker` 把 observation 保存在内部 `MutableList<CardId>` 中。这使 Undo 可以通过删除最后一个 observation 完全回到上一状态，包括恢复某张重复卡更早的观察位置。查询时从这份 history 导出：

- observation 总数；
- 去重后的已发现卡牌；
- 首次发现顺序；
- 上次观察后已打出的卡数；
- 按 `cardsRequiredToCycle` 计算的剩余循环距离。

模块不依赖 Android SDK、Compose、coroutine、Catalog、卡牌缩略图或捕获 API。

## MatchSession 和手动记牌

`MatchSession` 是当前对局的唯一状态所有者：

```text
用户点击卡牌
   → Activity ViewModel 或 OverlayService
   → MatchSession.observe(CardId)
   → CycleTracker.observe(CardId)
   → 重新导出 MatchSnapshot
   → StateFlow 同步 Activity 和 Overlay
```

`MatchSnapshot` 只暴露不可变视图：observation 数、deck capacity、有序的 `TrackedCard` 列表和 `canUndo`。列表顺序直接来自 Core 的首次发现顺序，Android 层不维护第二份 deck 真相。

8 张上限由 MatchSession 在调用 Core 之前守护：已发现 8 张后会拒绝第 9 个新 `CardId`，但仍可继续观察这 8 张卡。Undo 或 Reset 后上限状态由 Core 快照自然重算。

## Catalog 和视觉形态

Catalog 存放在 Android App 资料层，不进入 `cycle-core`。`CardDefinition` 字段为：

```kotlin
data class CardDefinition(
    val id: CardId,
    val supercellId: Long?,
    val displayName: String,
    val shortName: String,
    val type: CardType?,
    val elixir: Int?,
    val cycleEligible: Boolean,
    val iconAssetPath: String?,
)
```

`CardId` 是 CycleLens 稳定规范 ID；`supercellId` 是上游对照，不用作核心牌序身份。`VisualFormDefinition` 把 Normal、Evolution 和 Hero 等视觉形态映射回一个 canonical `CardId`，为未来的视觉识别保留稳定输出边界。

Catalog loader 在 Application 启动时读取本地 JSON，并验证 ID、引用和资产完整性。运行时不访问 Supercell API。

## Overlay

`OverlayService` 是用户从可见 Activity 启动的 `specialUse` foreground service。它使用传统 Android View 生成窗口内容，因此不需要为 service-owned `ComposeView` 伪造 Lifecycle/SavedState owner。

WindowManager 关键参数：

```text
width/height: WRAP_CONTENT
type:         TYPE_APPLICATION_OVERLAY
flags:        FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL
format:       PixelFormat.TRANSLUCENT
```

实际 window bounds 跟随 UI 大小，Overlay 外部触摸可继续交给底层 App。拖动只从明确的 handle 开始，并用 touch slop 区分 tap 与 drag；移动和配置变更后都会 clamp 到屏幕边界。

Overlay presentation 由纯状态 mapper 生成，有 `EXPANDED` / `COLLAPSED` / `PICKER` 三种 panel。卡槽始终沿用 MatchSnapshot 顺序。默认 `MINIMAL` 只显示图标标识和循环状态，`AVAILABLE` 映射为 `✓`；背景不透明度只影响背景 surface，不降低状态文字本身的可读性。

## Capture profile 与 MediaProjection 输出

Capture 有独立于 MatchSession 的状态机：

```text
Idle → RequestingConsent → Starting → Running
  ↑                                      │
  └────── Stop / system onStop ──────┘
                         └─→ Error
```

关键安全性质：

1. 只能从 Activity 中由用户点击发起。
2. 每个 session 都使用 Android MediaProjection 授权结果创建一个 `MediaProjection` 和一个 `VirtualDisplay`，不缓存 consent token。
3. callback 在 `createVirtualDisplay()` 之前注册。
4. `CaptureProfile` 保留 Native，并从 source 的最长边按比例派生 Balanced（1560）和 Eco（1170）；1440×3120 对应 720×1560 和 540×1170。输出优先取偶数，不做 CPU Bitmap resize。
5. `ImageReader` 使用 `RGBA_8888`、`maxImages = 2` 和 `acquireLatestImage()`。每个 `Image` 都在 callback 的 `finally` 中关闭。
6. `FrameSamplingGate` 使用 `Image.timestamp` 的 monotonic nanoseconds，不 sleep、不排队。三档 accepted target 为 30/15/10 FPS；DROP 只记统计后立即随 callback 关闭。
7. 统计明确区分 received/accepted/dropped、incoming FPS 和 accepted FPS。未来 detector 只能接在 `AnalysisFrameDescriptor` 边界后。
8. API 30+ 对 CycleLens 自己的 ImageReader Surface 请求 30 FPS hint，调用失败会被捕获；是否有效只看实际 incoming FPS。

屏幕尺寸变化时，callback 的 width/height 始终视为 source content size，再按当前 profile 派生 output；不会因旋转恢复 Native。同一个 VirtualDisplay 会按 output `resize()` 并切换到新 ImageReader surface。旧 reader 先停止 listener，再延迟 250 ms 退役，避免 Surface 切换时的 BufferQueue abandoned 竞态。

## 坐标、区域与 analysis boundary

`CaptureGeometry` 同时保存 source/output 尺寸与 X/Y scale，并集中提供 source→output、output→source 和 normalized→output 映射。`NormalizedRect` 验证 `0 <= left < right <= 1` 与 `0 <= top < bottom <= 1`，再向外取整为 `PixelRect`，避免各模块散落设备像素常量。

分析区域枚举仅有 `FULL_FRAME`、`ARENA`、`CUSTOM_DEBUG`。初始 `ClashRoyaleCaptureLayout.arenaRegion` 来自 Samsung SM-S9260 的真实 720×1560 single-app 实战 PNG：`(0.000, 0.095, 1.000, 0.855)`。它保留完整上下战场上下文，排除顶部玩家/计时 UI 与底部 hand bar；没有任何 card-specific ROI。

accepted frame 当前只生成：

```kotlin
AnalysisFrameDescriptor(timestampNs, geometry, arenaRect)
```

没有 `Channel<Image>`，也没有 pixel buffer ownership；Android `Image` 不离开 callback。

## Debug-only 单帧快照

Debug UI 的按钮只在 Running 时可用。用户点击后武装一次请求，并给出 2 秒切回捕获目标的时间；下一张符合时间条件的 frame 才会逐行复制为一张 PNG。转换使用 plane `rowStride`/`pixelStride`，每行只复制可见的 `width * 4` bytes，因此 Samsung 的 5888-byte padded rows 不会污染下一行。

PNG 固定写入 app 私有 `cacheDir/cyclelens-debug-frame.png`，新快照覆盖旧文件；用户可显式删除，Application 下次启动也会清理。Release UI 不显示入口，service 还会检查 debuggable flag。它不连续保存、不写公共相册、不上传，也没有新增 INTERNET 权限。

CaptureService 使用 `mediaProjection` foreground service type 和 `START_NOT_STICKY`。通知中的 `STOP CAPTURE` 只停止 Capture，不影响 Overlay 和 MatchSession。

## 保持不变的扩展边界

未来引入视觉处理时，应继续遵守：

- 帧处理输出 canonical/visual form ID，不把牌名或 artwork 塞入 Core。
- 识别结果不得绕过明确的决策边界直接篡改 MatchSession。
- CycleTracker 继续是循环计算唯一来源，Android 层不复制计算。
- 为像素数据定义明确的所有权、背压和及时释放规则，不允许无界队列。
- 所有自动化能力都必须作为新阶段单独设计和验收，不属于当前帧获取实现。

Android 对应 API 可参考 [MediaProjection 概览](https://developer.android.com/media/grow/media-projection)、[MediaProjection API](https://developer.android.com/reference/android/media/projection/MediaProjection) 和 [ImageReader API](https://developer.android.com/reference/android/media/ImageReader)。
