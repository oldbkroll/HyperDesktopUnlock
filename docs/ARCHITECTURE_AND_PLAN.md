# 架构审计与实施计划（2026-10-31，实施前）

基线：`397fa1a78d77a159b2ba6d518093ad0c9900922f`，1.12.8 / 238，最近标签 v1.12.8。Fork 为 `oldbkroll/HyperDesktopUnlock`，开发分支 `codex/zh-cn-extend-companion`。先建立 AGENTS.md，再审计；下列计划在本地化和精简代码之前记录。

## 注册点和依赖

`assets/xposed_init` → `MainHook.handleLoadPackage`；manifest 建议作用域是 android、com.android.systemui、com.android.settings。

| 进程 / 注册 | 目标及行为 | 处理 |
| --- | --- | --- |
| system_server / ExternalDisplayGate | DisplayContent 构造与 remove/removeImmediately；仅 TYPE_EXTERNAL；触发 PhoneScreen、DockAccessibility、DesktopSession；额外 getImePolicy / computeImeDisplayId* 为触控板路由输入法 | 保留只读物理显示跟踪，解耦全部会话副作用及 IME Hook |
| system_server / SettingsGuardHooks | Settings.Secure/Global 的 putString*/putInt*，阻止 mirror=1、force_desktop=0、freeform=0；ThreadLocal 只放行手动切换的写入 | 核心逻辑保留；不主动启用 freeform |
| system_server / MirrorPolicyHooks | SecondaryDisplayPolicy.forceEnableMirrorBuiltInDisplaySettingIfNeeded / enableMirrorBuiltInDisplaySetting → no-op | 原样保留 |
| 两进程 / DesktopModeHooks | DesktopModeStatus、DesktopState、DesktopModeFlags；接口实现类在本进程 APK 用 DexFile 查找；DesktopModeHelper 仅 per-display，内屏不强制 | 原样保留，不改全局危险 gate |
| SystemUI / ConnectDialogHooks | ExternalDisplayConnectionDialogDelegate.onCreate → 显示 Computer 按钮，绑定原监听器 | 原样保留 |
| SystemUI / LauncherHooks | ConnectingDisplayViewModel.applyConnectionChoice；选 Computer 后补写 mirror=0，再等待 Dock 无障碍就绪并启动 SECONDARY_HOME/HOME 及额外应用 | **补写 mirror=0 属于兼容核心，必须保留**；只删启动/轮询部分 |
| Settings / SettingsHooks | DisplayTopologyPreferenceController.getAvailabilityStatus → UNSUPPORTED_ON_DEVICE，避开 HyperOS 未初始化布局崩溃 | 保留 |
| system_server / MonitorInsets | DisplayPolicy.adjustWindowParamsLw 修改外屏 app insets，偏好默认关闭 | 移除实验性交互扩展 |
| system_server / AltTabHooks | InputManagerService 或 PhoneWindowManager.interceptKeyBeforeQueueing；AltTab、DesktopShortcuts、SwitcherOverlay | 全部移除输入拦截与桌面快捷键 |
| system_server + SystemUI / ModuleControl | 开机后注册 / Instrumentation.callApplicationOnCreate 注册，签名权限 ordered broadcast 返回状态及诊断，mirror/desktop 等命令 | 保留 status/report/mirror/desktop；移除 touchpad 与键盘设置命令 |

PhoneScreen 独占系统线程，以 SurfaceControl 关内屏或显示黑色 Touchpad；监听屏幕、解锁和来电恢复。Touchpad → RawTouch（读取 InputReader / `/dev/input/eventN`）与 VirtualMouse（`/dev/uhid`、输入设备显示关联），并读取窗口/输入法状态。DockAccessibility 会在连接、断开和开机时修改 enabled_accessibility_services。DesktopSession 经 AltTab 线程修改强制分辨率、密度、模式和唤醒锁。它们均有交叉依赖，不能只把 UI 默认值设为 false 后宣称已删除。

## 权限、隐私和敏感行为

- 控制广播由 `${applicationId}.permission.APPLY_SETTINGS`（signature）保护；ModuleClient 定向发送到 android，再到 SystemUI，普通应用无法合法调用。保留该边界和线程异常隔离。
- ShotProvider 是带 INTERACT_ACROSS_USERS_FULL 的导出 provider，接受系统截图数据，保存到 MediaStore；失败时只删除本次创建的未完成图片，不是清除用户文件。随截图快捷键一起删除 provider 和类。
- SettingsActivity 自动向 GitHub releases/latest 发 HTTPS 请求，只检查 tag、不下载/执行 APK；精简版移除此检查及 INTERNET，避免引导覆盖成上游完整版本。Issue 链接改到用户 Fork；仅打开浏览器，不自动发消息。
- `getprop` 只读取固定的公开 HyperOS 版本键。没有发现 rm/格式化、删除用户目录、刷写系统分区、读写 TEE/Gatekeeper/KeyMint/Keystore 的调用。
- DexFile 用于查找本机已安装 SystemUI 的 gate 实现，不是从网络加载代码。Xposed API 是 compileOnly，由 LSPosed 提供。
- 诊断会包含设备/固件、近期日志，以及用户主动生成报告时的外屏窗口包名和标题。保留明确披露及手动复制/分享；原始技术日志/协议字段不翻译，以便排障。本报告是源码审查，不能作为第三方依赖或固件的安全认证。

## 后续实施顺序

1. 独立提交原生 `values-zh-rCN` 本地化，并将用户可见硬编码报告文字外置；默认英文不变。构建和 lint 检查资源。
2. 独立提交精简：保留上述核心，解耦会话回调；删除 Dock 管理、触控板/鼠标、快捷键、显示参数和内屏管理及失效 UI/接口/资源。保留诊断、旧包提醒、Monitor 磁贴，并在应用内提供独立/镜像手动按钮。
3. 模块控制不写 DPI、size、refresh、rotation、power、accessibility 或输入关联。Extend 独占这些功能；不引入私有互调接口，不合并 APK。
4. 用版本后缀区分精简构建，保留 applicationId / LSPosed scope。APK 为自编译签名，不能声称可覆盖上游签名版本。
5. 再次构建、lint、资源/权限/依赖回归；记录两个仓库的可回退提交、APK 哈希与待实机矩阵。只推送用户 Fork 开发分支，不发布 Release 或上游 PR。

## 边界

不执行安装、卸载、重启或任何设备设置写入。当前只能做源码与本机构建验证。拔插、旋转、分辨率变化、独立启动/镜像恢复、触控板输入和内屏恢复均须在小米 17 Pro Max 实机验证。多物理外屏下的原始单 externalId 跟踪行为也需单独核对，不能由单屏源码推断已支持。
