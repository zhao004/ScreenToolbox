# ScreenToolbox 工程约束

- 仅使用 Kotlin 和 Jetpack Compose 实现应用界面，不新增 Java 业务代码。
- 所有系统悬浮窗必须由 `OverlayManager` 统一创建、更新和销毁。
- 业务逻辑不得写入 Composable，界面状态通过 ViewModel 的 `StateFlow` 暴露。
- 数据库访问必须经过 Repository，配置存储使用 DataStore。
- 不引入 Root、Shizuku、无障碍服务或云端依赖。
- 修改后至少执行一项相关验证，优先使用单元测试、Lint 或 Debug 构建。
- 临时验证产物统一存放在项目根目录 `.cache/`，不得提交。
- 处理权限撤销、服务销毁、窗口异常和配置变化时必须清理资源。
