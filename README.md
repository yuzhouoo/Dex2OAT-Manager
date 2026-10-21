# Dex2OAT Manager v1.3.0

Android 16 / KernelSU 专用 ART 编译管理器源码。项目保留 v1.2 的四页布局。

## 新增

- 启动和返回前台自动 `su -c id` 检测 Root。
- APP 页面展示目标模式及 `pm art dump <package>` 解析的实际编译模式；长按应用查看 ART 原始详情。
- 应用页面选中后可批量 `pm compile --reset`。
- 任务详情可勾选包名、全选后进行编译重置。
- 自定义分组可切换用户应用 / 系统应用、搜索、全选当前筛选结果，实时显示选中数。跨组冲突仍被禁止。
- 概览分别统计历史成功数与当前已达标数，ART 未知不算已达标。
- 自动增量编译读取系统 ART 状态后判断；Profile 回退 verify 时可暂缓重试。
- 命令输出保存在历史记录，所有执行操作都经 KernelSU `su`。

## 操作注意

- Android 16 `speed`, `speed-profile`, `verify` 为默认提供的三种编译策略。未添加 `everything`。
- `pm compile --reset` 将优化产物重置至安装时状态，**不会**清空应用数据，但仍有系统稳定性风险。优先测试普通应用。
- ART 状态是按包解析多 dex 的输出，如果返回多种编译状态会显示为“混合”，不会误认为已达标；无法解析显示“未知”。
- 定时任务使用 WorkManager，受 Android / ColorOS 后台限制。
- 真实设备上的 `pm art dump` 输出和 KernelSU 授权行为需要在真机验证。

## 构建

GitHub Actions: `.github/workflows/build.yml`，使用 JDK 17，Gradle 8.13，Android SDK 36。项目未携带 Gradle Wrapper，CI 使用 setup-gradle。

> 此环境不存在完整 Android SDK / Gradle，未运行 `assembleDebug`，所以 **不保证本版本已编译成功**。使用 GitHub Actions 验证后以真实构建结果为准。


## v1.4.0 UI 和分组操作

- 概览：独立任务状态卡片、停止按钮、双列快捷操作与达标统计。
- 应用：用户/系统应用双 Tab、紧凑分层卡片、双列批量操作，移除全局“包含系统应用”开关。
- 策略：全局模式与定时周期均为单行选择+保存；分组标题右侧新增按钮。
- 分组：固定 380dp 高滚动区，分组卡片展示数量/确认不匹配数/未知数，四个并排按钮：刷新、编译、编辑、删除。
- 分组编译：刷新后仅处理 ART 状态**已确认不匹配**的包，状态未知不会强制编译；最后仍由引擎执行增量校验。
- 点击反馈：按钮使用 Ripple 和短时缩放动画，耗时操作附 Toast；任务是否运行在概览显示。

注意：本交付是 Android Studio 源码工程；当前生成环境没有 Android SDK，未能运行 assembleDebug。建议在 GitHub Actions 编译并以构建日志确认。
