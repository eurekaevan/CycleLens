# 测试与真机验收记录

本文档把可重复的自动验证与需要设备/人工判断的结论分开。“通过”只代表表中描述的具体场景，不扩展为未测的视觉识别或长时游戏稳定性。

## 自动验证基线

当前自动测试总数为 206：

- 192 个 Kotlin/JVM 测试（`cycle-core` + Android app 的 JVM 可测状态/映射逻辑）；
- 14 个 Python Catalog updater 测试。

完整验收命令：

```bash
./gradlew test
./gradlew :cycle-core:test
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
git diff --check
```

Stage 6D 的完整自动化命令、lint 和 Debug APK assemble/install 均已成功。WindowManager、MediaProjection 输出缩放和截图像素不以 JVM mock 替代真机结论。

## Stage 6E temporal event proposal

实现范围仅为低分辨率时序变化候选：4× center-sample luma、绝对差分、8×8 coarse grid、8 邻域连通区域、bounded candidate selection，以及 normalized-IoU START/UPDATE/END grouping。它不包含卡牌分类、artwork/template、OCR、ML 或 `MatchSession.observe()`。luma/difference/grid 工作区在同 geometry 内复用；pipeline 仍拥有并最终释放原 RGBA lease。

本阶段新增 22 个 JVM 测试，覆盖 luma/downsample 与 workspace reuse、threshold/difference、grid、global-change suppression、connected regions、deterministic truncation、portrait/landscape normalized bounds、IoU/cooldown/expiration/long-gap/reset，以及 analyzer 的 PROCESSING-only borrow、异常释放、完整 geometry 与 resize generation reset、candidate/event 聚合统计和 artificial-delay timing 分离。当前 192 个 JVM 测试与 14 个 updater 测试、lint 和 Debug APK assemble 均通过。

### Samsung SM-S9260 / Android 16 验收

最终 Debug APK 已安装。两轮均使用 single-app `RECORD_CONTENT_TASK`、Clash Royale target UID、Balanced `720×1560`、15 accepted FPS 和 0 ms artificial delay。系统状态确认 `mIsRecordingOverlay=false`。测试目标停留在游戏大厅动态画面；没有通过 ADB 自动出牌或操作对局。

第一轮约 3.5 分钟累计 3,152 copied / 3,152 processed，pool、queue、copy-format drop 全为 0。12 个相隔约 10 秒的 `top` 样本为 48.1–66.6%，平均约 59.7%，其中 6 个样本不低于 60%。第二轮最终计数 build 的 6 个样本平均约 58.0%；去掉启动首个 35.7% 样本后，其余 5 个稳态样本平均约 62.5%。这已触及 Stage 6E 的 60–70% 暂停线，因此不继续增加算法复杂度。

最终 session 统计：

| 项目 | Avg / max 或累计 |
| --- | ---: |
| Accepted/copied/processed | 1,397 / 1,397 / 1,397 |
| Pool / queue / copy-format drops | 0 / 0 / 0 |
| Copy | 9.88 / 17.36 ms |
| Queue latency | 0.40 / 4.43 ms |
| Luma/downsample | 12.93 / 30.05 ms |
| Difference | 0.71 / 35.22 ms |
| Grid aggregation | 0.81 / 16.50 ms |
| Candidate extraction | 0.09 / 10.13 ms |
| Temporal grouping | 0.11 / 0.58 ms |
| Total analyzer | 14.69 / 79.44 ms |
| Candidates/frame | 0.89 avg / 8 max |
| Frames with candidates | 831 / 1,397 (59.5%) |
| START / UPDATE / END | 380 / 857 / 408 |

最终可比 PSS 在 Clash Royale 前台、CycleLens Activity 隐藏时为 93,356 KB；Activity 前台渲染完整 Catalog UI 时的 173,404 KB 不作为 capture PSS。两轮 thermal status 均为 0。日志未发现 app PID 的 FATAL/OOM、ImageReader maxImages、BufferQueue abandoned 或 analysis cleanup timeout；显式 `dumpsys meminfo` 触发过一次 concurrent GC，不代表持续 per-frame allocation GC。正常 Stop 后 MediaProjection 为 null，CaptureService 消失。

大厅并非 calibrated battle arena，因此这些候选不能用于计算 deployment recall。但在该低交互、仍有 UI animation 的画面上，59.5% 帧有候选且约产生 4.1 个 START/秒，说明 naive temporal difference 对非战斗动画仍较敏感。它能把整帧变化压缩到平均少于 1 个 bounded region，但当前 CPU 和 false-start 证据都不支持直接进入 classification。

未验证项：真实 troop/building deployment、Fireball/Arrows/Zap/Log、现存单位移动、projectile、塔攻击、troop death，以及 Clash Royale 人工 frame-pacing。Stage 6E 明确禁止自动游戏操作，本轮没有用户手动对局输入，因此不以大厅数据替代这些结论。下一步应先降低 luma/downsample 和已有 arena copy 成本，并在用户手动对局下采样真实候选，再决定 event proposal 方法；当前不建议进入 candidate classification。

## Stage 6D analysis pipeline

实现边界：accepted frame 在 ImageReader callback 内只复制 normalized arena，随后 `Image` 仍由原有 `finally` 关闭。输出是 tightly-packed RGBA direct buffer，不带原始 row padding；固定 pool 容量 3、latest queue 容量 1、overflow 为 `DROP_OLDEST`、worker 为单线程。

Balanced arena 的静态内存预算：

```text
720 × 1186 × 4 = 3,415,680 bytes / buffer
3 buffers         = 10,247,040 bytes total (about 9.77 MiB)
queue capacity    = 1
```

纯 JVM 覆盖 pool exhaustion/reuse/stale lease/double release/dispose、queue overflow/drain/latest frame、padded row crop/exact bytes/invalid layout、worker success/exception/stop/resize、0/20/100 ms delay 与 pipeline timing/drop stats。真机 benchmark 数值只在完成实际 3–5 分钟采样后填写，不从单元测试推断。

本阶段新增 24 个 JVM 测试。Samsung SM-S9260 / Android 16 使用 single-app Clash Royale、Balanced `720×1560` 进行真机验证。最终 direct-buffer、checksum 最多 1 Hz 的 0 ms session 连续约 3 分 55 秒：末段 incoming 约 107–116 FPS，accepted/copied/processed 均 15.0 FPS，3,528 accepted 全部处理，pool/queue/copy-format drop 均为 0。该轮末端 PSS 样本为 83,812 KB，thermal status 0；logcat 只看到一次由显式内存检查触发的 concurrent GC，没有持续 allocation GC、ImageReader/maxImages、BufferQueue abandoned、OOM 或 crash。

真机 timing/pressure 数据来自同尺寸 direct pool。0/20/100 ms 是连续阶段，表内平均值用各阶段前后累计总数与累计平均反算；max 为该阶段观察到的新高。第一轮 debug build 曾每 processed frame 做稀疏 checksum，测得其约 0.43 ms/帧后，最终实现已限频为最多 1 Hz。

| Delay | Accepted/copied | Processed | Queue drop | Pool miss | Copy avg/max | Historical worker avg/max (included delay) | Queue avg/max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 ms | 4,561 | 4,561 | 0 | 0 | 11.04 / 26.90 ms | 0.43 / 4.27 ms | 0.41 / 3.48 ms |
| 20 ms | 4,615 | 4,615 | 0 | 0 | 11.86 / 26.90 ms | 20.77 / 24.11 ms | 0.39 / 11.28 ms |
| 100 ms | 1,489 | 986 | 503 | 0 | 11.87 / 26.90 ms | 100.74 / 103.03 ms | 32.87 / 76.88 ms |

100 ms 阶段约 99 秒内满足 `accepted = processed + queue drop`，queue capacity 始终为 1、pool in-use 不超过 2，最大 queue latency 76.88 ms，没有追赶数秒旧帧。正常 Stop 后 MediaProjection 为 null、CaptureService 消失，日志没有 analysis cleanup timeout。

性能门槛尚未达到。最终 direct/0 ms build 的 Android `cpuinfo` 动态窗口约 38% app CPU，相对 Stage 6C capture-only 约 15% 明显过高；相同固定容量 heap 对照用 `/proc/<pid>/stat` 在 29.26 秒内测得约 39.3%，未改善。copy wall time 约 11–12 ms/accepted frame，是当前主要调查方向。PSS 与 thermal 达标；Stage 6E 的纯实现后来被明确授权继续，但仍需在进入 classification 前补做真机性能和候选有效性验收。没有进行 SurfaceFlinger frame timeline 或人工对局操作，因此本阶段不把“Clash Royale 无掉帧”标为已证明。

## Samsung Android 16 真机基线

设备：Samsung SM-S9260，Android 16。

| 场景 | 结果 | 证据边界 |
| --- | --- | --- |
| Debug APK 安装和冷启动 | 通过 | 安装成功，Activity 可启动 |
| 单 App 内容授权 | 通过 | system state 显示 `RECORD_CONTENT_TASK`，目标为 Clash Royale task |
| 帧入口 | 通过 | 1440×3120、pixel stride 4、row stride 5888、1 plane |
| 前台目标帧率 | 通过 | Clash Royale 前台时观察到稳定约 118–120 FPS |
| captured content visibility | 通过 | CycleLens Activity 前台时 visibility=false/FPS=0，回到游戏后恢复 |
| Overlay 与 Capture 并存 | 通过 | 同时存在 mediaProjection FGS id 1002 和 specialUse FGS id 1001 |
| Capture 通知 Stop | 通过 | 只停止 Capture，Overlay 保留 |
| 锁屏/system stop | 通过 | MediaProjection `onStop()` 触发资源清理 |
| 重复 Start/Stop | 通过 | 5 个独立 session 都重新授权并创建唯一 VirtualDisplay |
| 旋转/尺寸变化 | 通过 | 1440×3120 → 3120×1440 → 1440×3120 |
| FGS / Window / lifecycle 异常 | 未观察到 | 该轮测试 logcat 无 FGS 类型、WindowLeaked 或 lifecycle crash |

## Stage 6C profile benchmark

设备为同一台 Samsung SM-S9260 / Android 16。各 profile 在 Clash Royale 前台连续采样约 2 分钟；CPU 是 `top` 的波动区间/典型值，PSS 是该轮结束点样本，不是硬断言。三轮在同一 app process 中顺序执行，因此 PSS 会包含前一轮的已提交堆页。

| Profile | Source → output | Incoming | Accepted | App CPU | End PSS | Thermal |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Native | 1440×3120 → 1440×3120 | 117.9–119.0 FPS | 29.9–30.0 FPS | 约 51%（47.8–52.2%） | 96,305 KB | status 0 |
| Balanced | 1440×3120 → 720×1560 | 117.9–118.9 FPS | 15.0 FPS | 约 15%（14.6–15.4%） | 97,901 KB | status 0 |
| Eco | 1440×3120 → 540×1170 | 117.9–119.0 FPS | 10.0 FPS | 约 12.8%（多数 12.6–13.2%） | 98,614 KB | status 0 |

`Surface.setFrameRate(30)` 调用未抛异常，但三档动态游戏画面的 incoming 都仍接近 120 FPS，因此该 hint 对此 MediaProjection 路径无效。Balanced 的 output 降采样把 app CPU 从约 51% 降至约 15%；sampling gate 与 capture resolution 是独立优化轴。

三轮 thermal status 均为 0，app PID 没有出现 acquire/maxImages、BufferQueue abandoned、FGS、ImageReader leak 或 crash。系统 SystemUI PID 在投屏建立/切换点曾输出 `ImageReader_JNI maxImages` warning，未出现在 CycleLens PID 且没有持续增长或 callback backlog。实战期间未观察到明显停顿，但没有进行 SurfaceFlinger 级游戏 frame-pacing 测量；一次游戏网络“连接中断”弹窗不属于 Capture crash。

## Snapshot 与 arena calibration

| 场景 | 直接 PNG 结果 |
| --- | --- |
| Single-app + Overlay | Overlay 不在像素中；无 letterbox、无 unexpected crop |
| Entire-display + Overlay | Overlay 明确在像素中 |
| Entire-display system UI | Clash Royale 为 immersive fullscreen，该帧未显示 status bar 或 navigation UI；因此只能记录为“该测试帧中不存在”，不能推断系统永远排除它们 |

Single-app 系统状态为 `RECORD_CONTENT_TASK`、`mIsRecordingOverlay=false`；entire-display 为 `RECORD_CONTENT_DISPLAY`。实战 single-app 720×1560 PNG 人工测得 arena normalized rect：

```text
left=0.000, top=0.095, right=1.000, bottom=0.855
```

对应 Balanced pixel rect 为 `(0, 148) .. (720, 1334)`。它只定义 arena，不定义卡牌、spell 或 deployment ROI。

旋转测试早期曾观察到 BufferQueue abandoned，原因是切换 surface 后立即关闭旧 ImageReader。实现改为先取消 listener、再延迟 250 ms 释放旧 output。修复后双向 resize 没有再出现该错误。

## 资源观测

以下是单次 ADB 样本，不是长时 benchmark：

| 指标 | 未捕获 | 捕获中 | 观察差值 |
| --- | ---: | ---: | ---: |
| App PSS | 76,628 KB | 84,525 KB | 约 +7.9 MB |
| CPU | idle snapshot 0% | capture snapshot 40.7% | 不可视为长时平均 |
| Thermal status | 0 | 0 | 该短时窗未观察到热限制 |

这些数值会受设备温度、屏幕刷新率、前台内容、采样时刻和系统服务影响。不应用它们预测其他设备或实战长时温升。

## 仍未完成的真机证明

- Android 系统投屏 chip 的 Stop 流程未单独人工验收；通知 Stop 和锁屏 stop 已验证。
- Stage 6C profile 数值来自约 2 分钟/档的前台动态画面与一段实战，不等同于多局连续耗电或 SurfaceFlinger frame-pacing 验收。
- 没有 OCR、CV、模型推理或自动 `observe()` 路径，因此不存在识别准确率验收结果。

## 下次真机验收清单

1. 用独立冷进程重测三档 PSS，消除本轮顺序执行与 debug Bitmap 已提交堆页的影响。
2. 在横竖屏间往返两次，确认 Balanced/Eco resize 后仍保持 profile-derived output。
3. 从 Android 系统投屏 chip 停止，确认最终返回 Idle 并清理 service。
4. 进行多局连续实战，并用 SurfaceFlinger/frame timeline 工具补充主观流畅度证据。
5. Stage 6D 后续真机验收 0/20/100 ms pressure，确认 queue latency 有界、最终 queue=0，且没有 pool/ImageReader leak。
