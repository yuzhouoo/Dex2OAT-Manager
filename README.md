# Dex2OAT Manager — APK + Root

v2.0.0-beta7-rootonly 完全移除独立 KernelSU 后台模块，APK 通过用户授予的 su 权限直接执行 Android ART 编译。

- 采用 Beta4 浅色界面风格，保留概览、应用、策略、记录与日志页面。
- 手动编译/重置通过 Android 前台服务和 Engine 执行，通知与页面显示进度，任务历史保留。
- 自动增量编译仍通过 WorkManager 周期触发，共用 Engine 互斥锁，不使用独立 daemon / 任务队列。
- 根权限来自设备已配置的 su 授权，不要求安装本项目 KernelSU 模块。KernelSU 作为已有 Root 管理器不受影响。
- ART 的实际编译过滤器由 Android 系统决定，命令 Success 不代表最终 ART 模式达标。
- 升级 APK 不需要重启设备。历史数据保存在 APK 自己的应用数据中。

## 构建
GitHub Actions 使用 JDK 17 / Android SDK 36 执行 :app:assembleDebug，仅交付 APK，不再打包 KernelSU ZIP。

## 注意
如果手机中曾安装 dex2oat_backend KernelSU 模块，可在确认无遗留编译任务后自行于 KernelSU 管理器禁用，待方便时再卸载。新版 APK 完全不调用该模块。
