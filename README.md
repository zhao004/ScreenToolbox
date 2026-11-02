# ScreenToolbox

屏幕工具箱是 Android 8.0 及以上版本可运行的原生悬浮窗工具。应用使用 Kotlin、
Jetpack Compose、MVVM + Repository、Hilt、Room 和 DataStore 实现，不依赖 Root、
Shizuku 或云端后端。可选的自动更新检查只会访问 GitHub Releases，不上传使用数据。

## 项目预览

![ScreenToolbox 项目预览](assets/images/img01.jpg)

## 功能

- 防烧屏与防误触：新建和编辑共用可拖动、可最小化并自动吸附屏幕侧边的系统悬浮面板，支持宽高进度条、区域拖动和归一化坐标恢复。
- 防烧屏遮罩：通过无障碍服务创建受信任悬浮层，支持完全不透明显示和触摸穿透。
- 防误触：透明触摸拦截区域、边界提示、编辑手势和 30 秒临时解除。
- 全局水印：单条或平铺、颜色、字体、透明度、旋转和位置配置。
- 应用规则：从应用配置页直接添加防烧屏、防误触或水印配置，并按应用启用。
- 全局应用：配置始终叠加到应用专属规则上的全局规则，同类共享配置自动去重。
- 统一悬浮窗服务：前台通知、紧急关闭、拒绝重复窗口、权限撤销处理和服务恢复。
- 本地持久化：Room 保存配置与规则，DataStore 保存全局运行设置。
- 自动更新：启动时按 24 小时间隔检查 GitHub Releases，确认后由系统下载并校验
  SHA-256、版本和应用签名，再交给 Android 系统安装器。

## 权限

- 悬浮窗权限：在系统设置中授权，用于显示所有系统窗口。
- 无障碍服务：用于承载完全不透明且允许触摸穿透的防烧屏遮罩，不读取窗口内容。
- 使用情况访问：用于识别当前前台应用，不上传使用记录。
- 通知权限：Android 13 及以上用于显示前台服务状态和紧急操作。
- 安装未知应用：用户确认下载更新后，用于打开 Android 系统安装确认页。
- 开机广播：仅在用户开启“开机自动恢复”后尝试恢复服务。

## 构建

环境要求：

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleRelease
```

Debug APK 由 Android Gradle Plugin 的标准输出目录生成。Release 构建已验证，但
正式发布前仍需配置用户自己的签名密钥。

### 发布构建

发布工作流位于 `.github/workflows/android-release.yml`，支持推送 `vX.Y.Z`
标签或手动指定既有标签。工作流使用 JDK 17，先运行单元测试与 Lint，再生成
`arm64-v8a` 和 `armeabi-v7a` 两个独立 APK，不生成通用 APK 或 AAB。

首次发布前需在 GitHub 仓库中创建 `android_build` Environment，并配置以下
Environment Secrets：

- `ANDROID_KEYSTORE_BASE64`：Release keystore 文件的 Base64 内容。
- `ANDROID_KEYSTORE_PASSWORD`：keystore 密码。
- `ANDROID_KEY_ALIAS`：签名密钥别名。
- `ANDROID_KEY_PASSWORD`：签名密钥密码。

工作流会校验两个 APK 的签名，生成 `SHA256SUMS.txt`，并把以下文件发布到
GitHub Release：

- `ScreenToolbox-<tag>-arm64-v8a.apk`
- `ScreenToolbox-<tag>-armeabi-v7a.apk`
- `SHA256SUMS.txt`

本地需要验证分 ABI 构建时，可通过环境变量提供签名配置后执行：

```powershell
.\gradlew.bat :app:assembleRelease -PandroidAbiSplit=true
```

`RELEASE_VERSION_NAME` 和 `RELEASE_VERSION_CODE` 可由构建环境覆盖，未设置时
分别使用 `1.0.0` 和 `1`。签名环境变量必须同时配置；部分配置或密钥路径无效时
构建会直接失败，不会回退到 Debug 签名。

自动更新会读取最新 Release 的 `SHA256SUMS.txt`，并根据设备首选 ABI 匹配
`arm64-v8a` 或 `armeabi-v7a` APK。检查过程不使用 GitHub API Token，也不会
静默安装；下载完成后仍需用户通过系统安装器确认。Debug 构建不检查更新。

## 架构边界

- Compose 只负责应用内 UI，System Overlay 使用 Android View + WindowManager。
- 防烧屏遮罩由 `AccessibilityService` 提供窗口宿主，其他悬浮窗使用普通应用悬浮层。
- `OverlayService` 是窗口运行态唯一所有者，`OverlayManager` 统一创建、更新、移除窗口。
- ViewModel 通过 `StateFlow` 暴露状态，不在 Composable 中执行业务逻辑。
- 数据库访问均经过 Repository；全局应用规则始终参与解析，匹配的普通应用规则随后叠加。
- 规则页面只展示当前应用或全局应用已绑定的配置，新增配置绑定失败时会回滚未完成数据。
- 旧版本未绑定任何规则的配置会保留在数据库中，但不再参与运行时窗口解析。

## 系统限制

- Android 12 及以上的不受信任触摸策略会限制非触摸悬浮窗的有效不透明度。
- 防烧屏遮罩依赖用户手动启用无障碍服务；服务关闭或异常断开时遮罩会自动移除。
- Android 15 及以上可能限制后台启动前台服务，开机恢复和厂商后台策略不保证成功。
- UsageStats 在多窗口、折叠屏和部分厂商系统上可能延迟或误判。
- 自动更新需要设备能够访问 GitHub；更新安装要求新包使用与应用相同的签名密钥。
- 系统设置等受保护页面可能启用隐藏第三方悬浮窗的安全策略，此时编辑面板不会覆盖该页面。
- 普通应用无法任意更改其他应用的底层触摸分发，防误触只保证所创建透明窗口覆盖区域。
