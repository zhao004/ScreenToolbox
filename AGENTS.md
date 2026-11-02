# ScreenToolbox 工程约束

本文件只记录 ScreenToolbox 的项目特有约束。通用 Git、文档、安全和编码规范不需要
在此重复；无法从当前任务和仓库可靠判断且会影响结果的选择，应先确认。

## 项目范围

- 项目是原生 Android 应用，当前 `minSdk = 26`、`compileSdk = 36`、
  `targetSdk = 36`，使用 JDK 17 构建。
- 仅使用 Kotlin 实现业务代码，不新增 Java 业务代码。应用内界面使用 Jetpack
  Compose；系统悬浮窗可在 `overlay/` 边界内使用 Android View。
- 不引入 Root、Shizuku、云端后端或远程配置。现有 GitHub Releases 更新链路是唯一
  允许的对外更新集成。
- 优先复用现有 Hilt、Repository、Room、DataStore、窗口控制和系统服务封装；未明确
  需要时，不扩展平台、公共依赖、权限、协议或部署方式。

## 架构不变量

- `OverlayService` 持有悬浮窗运行态并统一协调配置、前台应用、临时解除和资源清理。
- `OverlayManager` 是所有系统悬浮窗创建、更新和销毁的唯一入口。UI 和业务模块不得
  直接调用 `WindowManager.addView`、`updateViewLayout` 或 `removeView`。
- `OverlayManager` 的公开窗口操作应在主线程调用；服务销毁、宿主断开、窗口异常、
  权限撤销和配置变化后必须移除对应窗口并释放监听器、协程和系统资源。
- `OverlayAccessibilityService` 仅作为防烧屏遮罩的受信任窗口宿主。必须保持
  `canRetrieveWindowContent = false`，不得读取窗口内容、在 `onAccessibilityEvent`
  中处理业务，或新增其他无障碍能力。
- 业务逻辑不得写入 Composable。界面状态通过 ViewModel 的 `StateFlow` 暴露，并使用
  `collectAsStateWithLifecycle` 消费。
- 数据访问必须经过 Repository。UI 和普通业务模块不得直接访问 Room DAO 或
  DataStore；数据库与配置存储分别使用 Room 和 DataStore 的现有实现。
- 规则解析必须保持“全局规则始终参与、匹配的普通应用规则随后叠加、按配置 ID 去重”
  的语义。未绑定规则的旧配置可以保留，但不得重新参与运行时解析。

## 高风险契约

- 修改 Manifest、运行时权限、前台服务类型、悬浮窗类型或无障碍服务语义前，必须说明
  用户影响、系统限制和资源清理方案。
- Room Schema 变更必须提供显式迁移，更新受版本控制的 Schema 快照，并通过迁移测试；
  禁止使用破坏性迁移绕过兼容问题。DataStore 键、序列化格式和默认值变更必须处理已有
  用户数据。
- 更新协议必须保持标签、ABI、制品命名、`SHA256SUMS.txt`、版本校验、签名校验和
  GitHub Actions 工作流一致。不得加入静默安装、数据上传或未经处理的下载来源。
- Release 签名信息只能通过环境变量或 GitHub Environment Secrets 提供，不得写入
  源码、测试夹具、文档示例、日志或临时产物。
- 临时验证产物统一存放在项目根目录 `.cache/`，不得提交。不得手工修改生成目录、
  构建产物或受限依赖目录。

## 验证与交付

| 变更范围 | 最低验证 |
| --- | --- |
| 纯文档 | 检查标题层级、代码围栏、相对链接、命令和项目事实是否一致。 |
| Compose、ViewModel、规则或数据逻辑 | 运行相关单元测试，并视风险执行 Lint 或 Debug 构建。 |
| 悬浮窗、Manifest、权限、Service 或 Android View | 运行相关单元测试、Lint 和 Debug 构建；有设备时补充对应的仪器测试。 |
| Room、DataStore 或发布配置 | 运行针对性测试，并按影响范围扩大至 Lint、Debug 构建或 Release 构建验证。 |

Windows 下的常用验证命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

至少执行一项与变更直接相关的验证。交付时说明实际修改、验证命令与结果，以及未执行
的检查和剩余风险；不得用启动开发环境或编辑器无报错代替测试和构建验证。
