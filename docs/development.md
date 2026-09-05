# 开发、构建与 Catalog 维护

## 工程结构

```text
CycleLens/
├── app/                         Android application
│   └── src/main/assets/         本地 Catalog 和缩略图
├── cycle-core/                  纯 Kotlin/JVM 牌序领域层
├── docs/                        架构、开发和验收文档
├── tools/update-card-catalog/   开发期 Catalog 生成与审核工具
└── gradle/wrapper/              锁定的 Gradle Wrapper
```

## 工具链

| 项目 | 当前值 |
| --- | --- |
| Gradle Wrapper | 9.7.1 |
| Android Gradle Plugin | 9.4.0 |
| Kotlin / Compose compiler plugin | 2.4.10 |
| Java / Kotlin target | 17 |
| `compileSdk` | 37.2 |
| `targetSdk` | 37 |
| `minSdk` | 26 |
| Compose BOM | 2026.08.00 |
| applicationId / namespace | `com.eureka.cyclelens` |

`local.properties` 仅供本机 Android SDK 定位，已在 `.gitignore` 中。不要把 Fedora 上的 JDK 25 路径或任何本机绝对路径写入可提交配置。

## 验证命令

全部单元测试（Core、App JVM 和 Python updater）：

```bash
./gradlew test
```

分层运行：

```bash
./gradlew :cycle-core:test
./gradlew :app:testDebugUnitTest
python3 -m unittest discover -s tools/update-card-catalog/tests -p 'test_*.py'
```

Android 静态检查与 APK：

```bash
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
git diff --check
```

对于纯文档修改，至少运行 `git diff --check`并检查 Markdown 中引用的仓库路径。任何代码、资产或 Gradle 改动都应执行上面的完整验证组。

## 连接设备

```bash
adb devices -l
./gradlew :app:installDebug
adb shell am start -n com.eureka.cyclelens/.MainActivity
```

常用诊断：

```bash
adb shell dumpsys activity services com.eureka.cyclelens
adb shell dumpsys window
adb shell pidof com.eureka.cyclelens
```

Bash 中限定当前进程日志：

```bash
pid="$(adb shell pidof com.eureka.cyclelens)"
adb logcat --pid="$pid"
```

fish 中的等价写法：

```fish
set pid (adb shell pidof com.eureka.cyclelens)
adb logcat --pid=$pid
```

检查 Capture 日志时可过滤 tag `CycleLensCapture`，但不要在过滤后日志为空时就认为完成了整个资源泄漏审计。

## 权限与前台服务

| 权限/类型 | 用途 |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | 由用户启动的手动记牌 Overlay |
| `FOREGROUND_SERVICE` | 前台服务基础权限 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | OverlayService 的 `specialUse` 类型 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | CaptureService 的 `mediaProjection` 类型 |
| `POST_NOTIFICATIONS` | Android 13+ 前台服务通知体验 |

Manifest **不声明** `INTERNET`、AccessibilityService、开机启动 receiver 或其他自动化权限。Overlay 和 Capture 都必须由当前可见 Activity 内的用户操作启动。

## 维护 Card Catalog

运行时静态数据：

```text
app/src/main/assets/cards.json
app/src/main/assets/card-icons/<supercellId>.png
```

`cards.json` 包含 canonical cards 和 visual forms。`tools/update-card-catalog/card-overrides.json` 是 CycleLens 语义的审核源，负责稳定 ID、short name、类型、cycle eligibility、排除项与视觉形态映射。上游 API 只提供上游身份、展示名和 artwork URL，不能静默覆盖本地语义。

默认 dry-run：

```bash
CLASH_ROYALE_API_TOKEN='<temporary-token>' \
  python3 tools/update-card-catalog/catalog_updater.py
```

审查所有 unknown、stale、unmapped 和 display-name drift 后才显式写入：

```bash
CLASH_ROYALE_API_TOKEN='<temporary-token>' \
  python3 tools/update-card-catalog/catalog_updater.py --update
```

也可对已审计且未含凭证的 API response 离线运行：

```bash
python3 tools/update-card-catalog/catalog_updater.py \
  --response-file path/to/cards-response.json
```

凭证安全规则：

- 只从环境变量读取 `CLASH_ROYALE_API_TOKEN`，并尽量避免把真值留在 shell history。
- 不把 token 写入 README、源码、Gradle、`local.properties`、测试 fixture 或命令输出。
- 如果 token 曾经被粘贴到聊天、issue 或日志，应在开发者面板撤销它并创建新 token。
- API 允许 IP 填写执行 updater 机器的对外公网 IP，不是手机局域网 IP、`127.0.0.1` 或 Android 设备 IP。公网 IP 变化后需要重新配置。

更新器的完整失败条件和文件职责见 [`tools/update-card-catalog/README.md`](../tools/update-card-catalog/README.md)。

## 资产自检

当前基线数量可用以下命令复核：

```bash
jq '{
  canonicalCards: (.cards | length),
  visualForms: (.visualForms | length),
  cycleEligible: ([.cards[] | select(.cycleEligible == true)] | length),
  towerTroops: ([.cards[] | select(.type == "TOWER_TROOP")] | length)
}' app/src/main/assets/cards.json

find app/src/main/assets/card-icons -type f -name '*.png' | wc -l
```

数量检查不能替代 `CardAssetIntegrityTest`；单元测试还会检查唯一性、映射和资产可读性。
