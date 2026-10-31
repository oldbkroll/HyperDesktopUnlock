> 实机：两项能力关闭可独立运行应用，改DPI仍FAIL；仅移除SecondaryDisplayLauncher后手势恢复、DPI往返与缩放正常，详见REVISION_7.md。

# 第六版诊断构建：隔离桌面能力强制

第五版实机FAIL：DPI 150→200、200→150均报告上滑失效，锁屏/解锁也不能恢复。与第三/四版不同，第五版已确认实际执行：接屏mask52→48，随后以及DPI变更mask4→0。说明“仅过滤BASIC_CHANGED”不足以解决问题；不能再把失败归于补丁未触达。现有证据未证明应继续扩大事件拦截。

本版移除GestureConfigHooks/Policy及其入口，完整恢复框架DisplayManager通知路径，不再补发或过滤显示事件。失败实现可从Git历史和旧交付包回看。

增加两项运行时诊断开关，分别控制DesktopModeHooks在system_server和SystemUI的桌面能力覆盖（含全局/枚举及per-display两种回调）。默认都关闭；保留原Hook安装，只在执行点检查进程门控。总开关关闭时全部放行；总开关开启但两项诊断开关关闭时，仍保留连接弹窗、镜像策略保护、显式Computer/Mirror补写、应用内独立/镜像按钮、只读外屏跟踪及诊断，不强制桌面能力。

目的：尝试用镜像解除支持独立应用，隔离完整桌面能力强制对固件手势的影响。这是待验证诊断构建，不是已修复版本。某些固件可能因此无法通过系统弹窗进入Computer，需使用Unlock应用内独立模式按钮；若仍无法独立启动，则按进程一次开启一个诊断开关并重连对照，不同时改变多个设置。

运行时开关由既有signature广播在system_server写固定Secure键hyperdesktop_flags_system、hyperdesktop_flags_systemui。两进程ContentObserver更新缓存，Hook稳定路径不读设置或做IPC；不新增应用权限。诊断回显两项状态。系统可能缓存能力判断，重连未刷新时仍可能需要重启，不能保证所有缓存都动态撤销。

本轮只读和安装由助手负责，重启、触摸、显示参数测试由用户操作。原有其余测试仍SKIPPED；总开关关闭+重插仍是已证实的恢复路径。Extend维持第二版。

版本1.12.8-extend-cn.6 (244)，debug签名，覆盖后需重启一次卸载第五版旧Hook。源码不包含设备日志/固件副本。

本地验证：assembleDebug/lintDebug成功，lint 0错误/5警告；47条资源及职责/门控/失败Hook移除检查通过，git diff --check通过。未标记实机修复。
