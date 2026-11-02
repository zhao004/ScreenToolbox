# ScreenToolbox

ScreenToolbox 是一个面向 Android 8.0（API 26）及以上设备的原生悬浮窗工具箱。
应用使用 Kotlin、Jetpack Compose、MVVM + Repository、Hilt、Room 和 DataStore
实现，不依赖 Root、Shizuku 或云端后端。除用户主动配置的 GitHub Releases
更新检查外，应用不上传使用数据。

## 项目预览

![ScreenToolbox 项目预览](assets/images/img01.jpg)

## 快速开始

### 环境要求

- JDK 17
- Android SDK Platform 36
- Android 8.0（API 26）及以上模拟器或设备

项目当前使用 `compileSdk = 36`、`targetSdk = 36` 和 `minSdk = 26`。可以使用
Android Studio 导入项目，也可以通过仓库自带的 Gradle Wrapper 执行构建与测试。

### 常用命令

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

macOS 或 Linux 将 `.\gradlew.bat` 替换为 `./gradlew`。Debug APK 由 Android
Gradle Plugin 生成到 `app/build/outputs/apk/debug/`；设备测试需要先启动模拟器
或连接已开启调试的设备。

## 功能概览

| 模块 | 能力 |
| --- | --- |
| 防烧屏 | 通过无障碍服务承载完全不透明、可触摸穿透的防烧屏遮罩。 |
| 防误触 | 创建透明触摸拦截区域，支持编辑、边界提示和 30 秒临时解除。 |
| 悬浮窗编辑 | 防烧屏与防误触共用可拖动、可最小化、自动吸边和归一化坐标恢复的编辑面板。 |
| 全局水印 | 支持单条或平铺、颜色、字体、透明度、旋转和位置配置。 |
| 应用规则 | 从应用配置页为指定应用或全局应用添加防烧屏、防误触和水印规则。 |
| 服务控制 | 前台通知、紧急关闭、重复窗口防护、权限撤销处理和服务恢复。 |
| 本地数据 | Room 保存规则与配置，DataStore 保存运行设置和更新状态。 |
| 自动更新 | Release 构建按 24 小时间隔检查 GitHub Releases，并交由 Android 系统安装器确认安装。 |

## 架构与运行机制

### 代码分层

| 目录 | 职责 |
| --- | --- |
| `ui/` | Jetpack Compose 应用界面、ViewModel、导航、主题与更新交互。 |
| `overlay/` | 悬浮窗服务、窗口管理、无障碍窗口宿主以及 Android View 悬浮组件。 |
| `domain/`、`data/` | 规则解析、Repository 接口、Room、DataStore 与更新数据源。 |
| `system/` | 权限、UsageStats、通知、开机恢复和 APK 安装等系统能力。 |
| `di/` | Hilt 依赖注入及接口绑定。 |

### 状态与数据流

- 应用内界面使用 Jetpack Compose；系统悬浮窗使用 Android View 和
  `WindowManager`，两者职责不混用。
- ViewModel 通过 `StateFlow` 暴露界面状态，Composable 不直接执行业务逻辑。
- 数据访问遵循 `UI -> ViewModel -> Repository -> Room/DataStore`，界面层不直接
  访问 DAO 或 DataStore。
- Hilt 负责 Application、Service、ViewModel 和 Repository 的依赖装配。

### 窗口所有权

- `OverlayService` 是悬浮窗运行态的唯一所有者，负责观察配置、解析前台应用规则、
  更新通知并协调临时解除等运行状态。
- `OverlayManager` 是所有系统悬浮窗的统一入口，负责创建、更新、移除和重算窗口；
  其他业务模块不得绕过该入口直接操作 `WindowManager`。
- 普通悬浮窗使用应用悬浮层；防烧屏遮罩使用无障碍服务提供的受信任窗口宿主。
- `OverlayAccessibilityService` 只提供窗口宿主，配置为
  `canRetrieveWindowContent="false"`，不读取窗口内容，也不处理业务事件。

### 规则与兼容性

- 全局应用规则始终参与解析，匹配的普通应用规则随后叠加，相同配置按 ID 去重。
- 规则页面只展示当前应用或全局应用已绑定的配置；新增配置绑定失败时会回滚未完成
  数据。
- 旧版本中未绑定任何规则的配置会保留在数据库中，但不再参与运行时窗口解析。
- Room 数据库变更必须提供迁移并通过迁移测试；DataStore 配置变更需保持已有用户
  数据的可读取性。

### 更新流程

- 自动更新只在 Release 构建中启用，Debug 构建不检查更新。
- 应用从最新 GitHub Release 的 `SHA256SUMS.txt` 选择与设备首选 ABI 匹配的 APK。
- 下载完成后校验 SHA-256、版本信息和应用签名，再交由 Android 系统安装器确认；
  不执行静默安装。
- 检查过程不依赖 GitHub API Token，也不上传设备或使用数据。

## 权限与系统限制

### 权限

| 权限或能力 | 用途 |
| --- | --- |
| 悬浮窗（`SYSTEM_ALERT_WINDOW`） | 显示防误触、水印和编辑面板等普通系统悬浮窗。 |
| 无障碍服务（`BIND_ACCESSIBILITY_SERVICE`） | 仅承载完全不透明且可触摸穿透的防烧屏遮罩，不读取窗口内容。 |
| 使用情况访问（`PACKAGE_USAGE_STATS`） | 识别前台应用并匹配应用规则，不上传使用记录。 |
| 前台服务（`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`） | 在通知可见的前提下维持悬浮窗运行。 |
| 通知（`POST_NOTIFICATIONS`） | Android 13 及以上显示前台服务状态和紧急操作。 |
| 安装未知应用（`REQUEST_INSTALL_PACKAGES`） | 用户确认更新后打开 Android 系统安装确认页。 |
| 开机广播（`RECEIVE_BOOT_COMPLETED`） | 仅在用户开启“开机自动恢复”后尝试恢复服务。 |
| 网络（`INTERNET`） | 仅在 Release 构建中访问 GitHub Releases 检查更新。 |

### 平台限制

- Android 12 及以上的不受信任触摸策略会限制非触摸悬浮窗的有效不透明度。
- 防烧屏遮罩依赖用户手动启用无障碍服务；服务关闭或异常断开时遮罩会自动移除。
- Android 15 及以上可能限制后台启动前台服务，开机恢复和厂商后台策略不保证成功。
- UsageStats 在多窗口、折叠屏和部分厂商系统上可能延迟或误判。
- 系统设置等受保护页面可能启用隐藏第三方悬浮窗的安全策略，此时编辑面板不会覆盖
  该页面。
- 普通应用无法任意更改其他应用的底层触摸分发，防误触只保证所创建透明窗口覆盖的
  区域。

## 发布

发布工作流位于 [`android-release.yml`](.github/workflows/android-release.yml)，支持
推送 `vX.Y.Z` 标签或手动指定既有标签。工作流使用 JDK 17，先运行单元测试与
Lint，再生成 `arm64-v8a` 和 `armeabi-v7a` 两个独立 APK，不生成通用 APK 或 AAB。

首次发布前需在 GitHub 仓库中创建 `android_build` Environment，并配置以下
Environment Secrets：

| Secret | 用途 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Release keystore 文件的 Base64 内容。 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码。 |
| `ANDROID_KEY_ALIAS` | 签名密钥别名。 |
| `ANDROID_KEY_PASSWORD` | 签名密钥密码。 |

工作流会校验两个 APK 的签名，生成 `SHA256SUMS.txt`，并发布以下文件：

- `ScreenToolbox-<tag>-arm64-v8a.apk`
- `ScreenToolbox-<tag>-armeabi-v7a.apk`
- `SHA256SUMS.txt`

本地验证分 ABI Release 构建时，需要同时提供 `ANDROID_KEYSTORE_PATH`、
`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS` 和 `ANDROID_KEY_PASSWORD`
环境变量：

```powershell
.\gradlew.bat :app:assembleRelease -PandroidAbiSplit=true
```

签名变量必须全部配置；部分配置或密钥路径无效时构建会直接失败，不会回退到 Debug
签名。`RELEASE_VERSION_NAME` 和 `RELEASE_VERSION_CODE` 可由构建环境覆盖；未设置
时分别使用 `1.0.0` 和 `1`。GitHub Actions 会使用发布标签中的版本名和递增的运行
编号覆盖这两个值。

自动更新要求设备能够访问 GitHub，新包必须与应用使用相同的签名密钥。系统设置等
受保护页面或厂商安全策略仍可能阻止第三方悬浮窗显示。
