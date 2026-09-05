# 测试与真机验收记录

本文档把可重复的自动验证与需要设备/人工判断的结论分开。“通过”只代表表中描述的具体场景，不扩展为未测的视觉识别或长时游戏稳定性。

## 自动验证基线

当前测试总数为 134：

- 120 个 Kotlin/JVM 测试（`cycle-core` + Android app 的 JVM 可测状态/映射逻辑）；
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

最近一次 Stage 6B 完整验收中，上述命令全部成功，Debug APK 为 31,404,040 bytes。WindowManager 本身和 MediaProjection 实际系统行为没有用 JVM mock 作为替代，而是在真机上验收。

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

- 单 App 捕获时 Overlay 不进入像素：system state 的 `RECORD_CONTENT_TASK` 和 `mIsRecordingOverlay=false` 是强证据，但当前代码不查看/保存像素，所以没有直接图像证明。
- “整个屏幕”授权模式已确认 system state 为 `RECORD_CONTENT_DISPLAY`，但 Overlay 是否出现在所得像素中未直接验证。
- Android 系统投屏 chip 的 Stop 流程未单独人工验收；通知 Stop 和锁屏 stop 已验证。
- 已在 Clash Royale lobby/前台内容上验证帧获取，但还没有完成长时实战的主观流畅度、发热和耗电验收。
- 没有 OCR、CV、模型推理或自动 `observe()` 路径，因此不存在识别准确率验收结果。

## 下次真机验收清单

1. 执行完整冷启动，确认手动记牌、Overlay 和 Capture 独立可用。
2. 分别选择单 App 和整屏授权，记录 `dumpsys media_projection`。
3. 在横竖屏间往返两次，检查尺寸恢复、frame count 继续增长且 logcat 没有 BufferQueue/IllegalStateException。
4. 从 Capture 通知、系统投屏 chip 和锁屏分别停止，确认最终都返回 Idle 并清理 service。
5. 进行至少一局完整对战，记录温度、电量、CPU/PSS 和 Overlay 触控感受。
6. 只有在未来引入受控的像素验证工具后，才将 Overlay 是否出现在捕获内容中从 system-state 推断升级为直接证明。
