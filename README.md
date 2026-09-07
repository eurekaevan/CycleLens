# CycleLens

CycleLens 是一个本地优先的 Android 皇室战争手动牌序辅助工具。玩家点击对手刚打出的卡牌，应用会记录首次发现顺序，并显示关键牌距离再次可用还差几张。

> 当前是开发阶段。Screen Capture 已完成 profile、采样、坐标映射和有界 arena pixel pipeline，**没有卡牌识别，也不会自动记牌**。

## 已实现

- 纯 Kotlin/JVM `cycle-core`：观察、Undo、Reset、首次发现顺序和四卡循环距离。
- Compose 手动记牌界面：本地卡牌搜索、Opponent Deck 和 8 张卡牌上限。
- 手动实战 Overlay：4×2 稳定卡槽、点击记牌、Undo、新卡 Picker、拖动和折叠。
- Overlay 可调整尺寸、详细度和背景不透明度；默认是 `COMFORTABLE + MINIMAL + 50%`。
- 离线 Canonical Card Catalog：126 个规范卡牌、122 张可记录卡牌、4 个 Tower Troop、184 个视觉形态和 126 张本地缩略图。
- MediaProjection 帧入口：Native、Balanced、Eco 输出 profile，分别将 accepted analysis frame 限制为 30、15、10 FPS。
- 新安装默认选择 Balanced；Native 保留为 correctness/benchmark 模式。
- Capture geometry：区分 source/output 尺寸，并统一提供 source、output、normalized 坐标映射。
- Accepted frame 只把 arena 同步复制进 3 个复用的 direct buffer；容量 1 的 latest-frame queue 把数据交给单消费者，Android `Image` 不离开 callback。
- Debug build 可由用户主动保存一张 cache PNG，并可用 0/20/50/100 ms analysis delay 验证背压；Release 不暴露这些控制。

## 快速开始

环境要求：

- JDK 17
- Android SDK（`compileSdk 37.2`，`minSdk 26`，`targetSdk 37`）
- Android 设备或模拟器（仅安装/真机验收需要）
- Python 3（仅卡牌目录更新器和其测试需要）

```bash
./gradlew test
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

连接设备后安装 Debug APK：

```bash
./gradlew :app:installDebug
```

APK 输出位于 `app/build/outputs/apk/debug/app-debug.apk`。工程使用 Gradle Wrapper 9.7.1、Android Gradle Plugin 9.4.0、Kotlin 2.4.10 和 Java/Kotlin 17 target，不需要把本机 JDK 路径写入工程。

## 使用流程

1. 在主界面从本地 Catalog 选择对手打出的卡牌。
2. 卡牌首次出现后会固定在 Opponent Deck 的一个位置；之后直接点击该卡即可再次记录。
3. 要在其他 App 上方操作，先授予“显示在其他应用上层”权限，再从可见 Activity 中点击 `START OVERLAY`。
4. Capture Idle 时选择 profile，再点击 `START CAPTURE`；Android 会在每个 session 开始前显示屏幕捕获授权页。
5. Debug build 的 `SAVE DEBUG FRAME` 会武装一次性快照；切回目标 App 后只保存一帧到私有 cache，可在界面中删除。

Overlay 和 Screen Capture 是两个独立的前台服务。停止任何一个都不会自动 Reset 本局牌序，也不会停止另一个服务。

## 模块与边界

```text
Compose Activity ─┐
OverlayService  ─┼─→ MatchSession ─→ cycle-core/CycleTracker
                  │
Local Catalog  ──┘

Activity ─→ CaptureSessionStateStore ← CaptureService
                                      ├─→ SamplingGate → arena copy → bounded analysis worker → discard
                                      └─→ debug-only one-shot cache PNG
```

- `cycle-core/` 不依赖 Android，也不知道卡名、图标、Overlay 或屏幕捕获。
- `MatchSession` 是 Application process 内唯一的比赛状态所有者，Activity 和 Overlay 共用同一份不可变快照。
- Catalog 是展示元数据和视觉形态映射的唯一来源，不进入 `cycle-core`。
- Capture 与 MatchSession 没有连线；帧到达不会调用 `observe()`。

详细说明见 [架构文档](docs/architecture.md)。

## 卡牌目录

Android 运行时只读取打包在 App 内的 `app/src/main/assets/cards.json` 和 `card-icons/`，Manifest 没有声明 `INTERNET` 权限。

官方 API 只在开发机上用于生成和审核静态资产。更新方法、干跑默认和 review gate 见 [Catalog updater 文档](tools/update-card-catalog/README.md)。`CLASH_ROYALE_API_TOKEN` 不得写入源码、`local.properties`、Gradle 配置或提交记录。

## 隐私与非目标

- 不使用 AccessibilityService、Root、Hook、游戏进程读取、网络抓包、自动点击或手势模拟。
- 不做 OCR/CV/模型推理；`VisualDetection` 目前只是一个未接入的边界 DTO。
- Screen Capture 只在 SamplingGate 接受后把 arena 复制进有界的进程内 buffer，不保存这些帧；只有 Debug build、用户主动点击后才额外保存一帧到私有 cache，不连续保存、不公开到相册，也不上传。
- 没有对局持久化。Android 终止 CycleLens process 后，当前 MatchSession 和 Capture 状态会丢失。
- 当前的“可用”是标准四卡循环距离，不声称精确恢复对手实时四张手牌。

更完整的依赖边界、资源释放规则和验收记录见：

- [架构与状态所有权](docs/architecture.md)
- [开发、构建与 Catalog 维护](docs/development.md)
- [测试与真机验收记录](docs/validation.md)

## 非官方声明

CycleLens 是非官方 fan content，未获得 Supercell 认可。卡牌名称和缩略图的使用应继续遵守 [Supercell Fan Content Policy](https://supercell.com/en/fan-content-policy/)。
