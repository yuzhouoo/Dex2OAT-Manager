# Dex2OAT 编译管理器 v1.1.0

针对 Android 10+（重点为 Android 16 + KernelSU）开发的 Java 原生 Android Studio 项目。

## 功能
- **全局编译模式**：`speed-profile`（推荐）、`speed`、`verify`。
- **自定义分组**：可新增、修改和删除多个分组；不同分组各自选择编译过滤器；已被其他分组选中的应用禁止再次选择，并在保存时二次验证。
- **优先级**：自定义分组 > 全局模式。按应用包名执行 `cmd package compile -m <filter> -f <package>`。
- **统计**：可见范围的 APP 总数；版本、更新日期与当前编译模式均匹配的成功编译 APP 数量；其余待处理数。
- **历史**：本地保存最近 100 次编译任务的时间、来源（手动/增量）、成功/失败/跳过数量、耗时、取消标记、逐应用输出。
- **周期增量任务**：通过 AndroidX WorkManager 唯一周期任务，每 1/3/6/12/24/48/72/168 小时进行检查，可关闭。按 `versionCode:lastUpdateTime|mode` 判定是否需要重编；**只有编译成功才更新标记**，失败下次重试。扫描范围与“包含系统应用”设置一致。
- **手动操作**：所选 APP 编译、全部范围编译、立即增量扫描、停止当前编译、Root 检测、搜索、刷新。

## 构建
将项目上传至 GitHub，在 Actions 中运行 `Build APK` 工作流，下载 `Dex2OAT-Manager-Debug-APK` 构建产物。也可在 Android Studio 中选择 Build APK。

## 运行注意
1. 安装 APK，在 KernelSU 授予该包 Root 权限（包名 `com.example.dex2oat`）。
2. 首次设置全局模式及自动编译选项；预设不开启自动增量，也不包括系统应用。
3. 任务历史和成功标记保存在应用私有 SharedPreferences 中；卸载或清除数据后会消失。
4. 本应用只统计由其自身发起且成功的编译，不等同于系统所有 APP 的 ART 最终编译状态。系统自动优化/OTA 可能重新调整优化结果。
5. WorkManager 并非精确计时器，休眠、省电策略、系统强杀及电池限制可能延后执行。后台任务单批主动限制在约 7 分钟；剩余应用将在下次扫描继续。后台 Root 授权需事先完成。
6. 由于编译依赖系统命令，不同 ROM 输出可能不同。当前以退出码 0 且输出包含 `Success` 判定成功。ColorOS 16 需要在真机验证。
7. `speed` 可能带来较大的存储和发热开销；`speed-profile` 不保证一定提升速度，取决于配置文件与系统 ART 策略。

## 实现文件
- `MainActivity.java` UI、分组编辑与任务查看
- `Store.java` 配置、任务记录和编译成功标记
- `Engine.java` KernelSU 执行及编译循环
- `IncrementalWorker.java` 周期调度

## 未完成验证
当前环境未安装 Android SDK/Gradle，项目未通过实际 APK 构建或一加 12 真机测试。建议第一次只选一个普通应用运行，检查 KernelSU 授权、命令返回格式及任务记录。
