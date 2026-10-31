> 第四版路径修正候选见 [REVISION_4.md](REVISION_4.md)；第三版实机失败，尚未确认修复。

> 第三版固件兼容候选见 [REVISION_3.md](REVISION_3.md)，尚待手势/镜像针对性实机验证。

> 当前更新：第二版见 [REVISION_2.md](REVISION_2.md)。下文为第一版交付时的历史快照；剩余实机验证已按用户要求全部标记 SKIPPED。第二版构建与检查见该修订记录。

# Extend 协同精简版

版本：`1.12.8-extend-cn.1`（239）；包名 `io.github.the_marox.hyperdesktop`。本 Fork 的交付分支为 `codex/zh-cn-extend-companion`。

基线为上游 1.12.8，commit `397fa1a78d77a159b2ba6d518093ad0c9900922f`。默认英文、简体中文和原有意大利文资源保留；精简后有 40 条应用字符串。源码扫描路线及实施前分析见 [ARCHITECTURE_AND_PLAN.md](ARCHITECTURE_AND_PLAN.md)。

## 当前职责

- 保留 Computer 按钮、桌面能力判断、强制镜像阻止、必要设置保护和 HyperOS 设置页拓扑兼容修复。`ConnectDialogHooks`、`DesktopModeHooks`、`SettingsGuardHooks`、`MirrorPolicyHooks`、`SettingsHooks` 五个文件与基线一致。
- `LauncherHooks` 仅保留 Computer 选择后的 `mirror_built_in_display=0` 兼容写入。没有启动 Dock、额外应用或等待无障碍服务的行为。
- `ExternalDisplayGate` 仅跟踪物理外屏，不再管理会话或输入法。沿用上游单 externalId 模型，多物理外屏尚待验证。
- 应用内按钮和 Monitor 快捷磁贴可手动切换独立/镜像；应用会在连接变化和返回前台时刷新状态。已加载模块版本不匹配时禁用应用内切换按钮并提示重启。
- 保留定向、签名权限保护的 status/report/mirror/desktop 广播。删除 APPLY_SETTINGS 接收路径、touchpad 命令、show_ime_off 命令和共享偏好功能。权限名沿用原来的 `.permission.APPLY_SETTINGS` 以保护 COMMAND，名称不表示仍有会话设置功能。
- 诊断对 System Framework 和 System UI 分别收集信息，缺少 SystemUI 响应会报告 `systemui-report`；Settings 进程的拓扑 Hook 仍需查看 LSPosed 日志，不由双进程报告证明。

## 已移除

12 个类：AltTabHooks、DesktopShortcuts、SwitcherOverlay、DockAccessibility、PhoneScreen、Touchpad、RawTouch、VirtualMouse、TouchpadTile、DesktopSession、MonitorInsets、ShotProvider。对应 UI、字符串、触控板图标、服务及截图 provider 同步删除。

因此模块不再修改外屏分辨率/DPI/刷新率/旋转、内屏电源、输入设备关联、Dock 无障碍或唤醒锁；不拦截键盘快捷键，不读 `/dev/input`、不创建 `/dev/uhid`。移除了自动更新检查及 INTERNET 权限。诊断反馈链接指向本 Fork，浏览器由用户决定是否提交问题。

旧模块留下的显示覆盖、Dock 无障碍授权及应用设置不会被新版本自动清理；升级前应先用旧模块退出会话并手动核对。手机面板关闭和触控板均由 Extend 管理。

## 验证

2026-10-31：JDK 17、Gradle 8.9（独立、SHA-256 校验过的发行包）、AGP 8.6.1、SDK 35，`assembleDebug` 与 `lintDebug` 成功。lint 为 0 错误、5 警告（接收器 API 标记、冗余 label、系统资源反射、备份规则提示）。没有增加权限以消除 lint。

`python scripts/check_locales.py`：40 条中文资源、格式/导航参数及 XML 通过。`python scripts/check_companion.py`：5 个核心文件一致，职责边界和签名权限通过。APK 层检查确认只声明自有签名权限、仅保留 MonitorTile，无截图 provider、无已删除扩展类，包含 `zh-rCN` 资源，APK v2 签名验证通过。

仅测试源码、构建与 APK 结构。未执行安装、重启、系统设置写入或任何实机测试。软件编译成功不能证明小米 17 Pro Max 当前固件已适配。

## 使用与恢复

1. 自编译 APK 使用本机 debug 签名。与上游签名不同，不能直接覆盖同包名原版。先记录原版版本、来源、作用域及设置并保留原 APK。
2. 在旧模块仍正常运行时恢复手机内屏，切换镜像，断开外屏；核对 Dock 无障碍服务是否已停用。在 LSPosed 停用旧模块并由用户手动重启，再根据签名情况卸载/安装。卸载会清除该应用自己的设置，请提前记录。
3. 安装本版，在 LSPosed 勾选系统框架、系统界面、设置；由用户手动重启。等待约一分钟后检查诊断。不要同时启用旧包 `io.maurizio.hyperdesktop` 与本包。
4. 连接外屏并选择 Computer，再到 Extend 选择该物理显示器。先验证普通应用和返回/主屏幕，再尝试 DPI、分辨率、输入和内屏控制。
5. HyperOS 如果仍使用手机比例，可在 Extend 中将逻辑分辨率显式设置为外屏的原生宽×高，再设置 DPI。清除覆盖是否回到手机比例取决于固件，未做实机确认。不要强制打开 freeform 或多窗口系统开关。
6. 卸载或回退前，先由 Extend 恢复内屏并停止触控板，恢复镜像并断线；停用 LSPosed 模块，由用户手动重启后再卸载/换回原签名 APK。卸载 APK 不会立即卸载已注入 system_server 的代码。
7. 手动镜像入口不可用时，可由已授权 ADB 的用户手动执行 `adb shell settings put secure mirror_built_in_display 1`，再用 `adb shell settings get secure mirror_built_in_display` 检查；本次未执行这些命令。若模块导致系统界面不可用，应通过现有 Root/LSPosed 环境的官方安全模式停用模块，不猜测不同框架的恢复按键。

推送仅限用户 Fork 的开发分支；没有发布 Release、合并 main 或向上游提交 PR。
