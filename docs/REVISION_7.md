# 第七版：避开系统副屏桌面与内屏手势的冲突路径

## 有效实机对照

第六版两项桌面能力强制关闭时，用户确认独立外屏应用与内屏手势正常；改DPI后失效。助手在立即重读任务表后，仅移除了display2的系统home根任务21（唯一活动为com.miui.home/.launcher.SecondaryDisplayLauncher），保留外屏应用根任务20和手机桌面进程。com.miui.home PID前后均6562。用户反馈手势恢复，随后DPI往返和缩放变更也正常；快照确认SecondaryDisplayLauncher不存在。该对照明显指向副屏系统桌面与内屏桌面共进程的配置路径，但没有对厂商原生代码作完整证明。

## 固化行为

- 在popsicle / OS4.0.5.0.XPBCNXM的system_server，Hook本机实际ActivityStartController.startHomeActivity(Intent,ActivityInfo,String,TaskDisplayArea,boolean)。总开关开启时，仅阻止目标为com.miui.home.launcher.SecondaryDisplayLauncher且TaskDisplayArea属于真实物理外屏的启动。内屏/背屏、其他Activity、其他包、镜像本身以及其他固件不在拦截范围内。关闭总开关放行。
- 通过既有控制线程，在启用、切换独立模式及外屏连接后检查已有根任务。仅匹配外屏ID、topActivity精确为上述组件、numActivities=1的正数根任务ID，调用系统任务移除API；多Activity根任务和普通外屏应用不处理。不是清数据、禁用包、强停或重启桌面。
- 不新增显示事件过滤或补发，不改显示参数，不扩大LSPosed作用域，不新增权限。控制入口仍为signature保护广播，外屏连接时的清理从显示构造回调转投既有Handler线程，不在WMS锁内调用任务移除。
- 外屏以独立应用为主，无系统副屏桌面；外屏应用退出后可能只剩空白背景，需要从Extend再次选择应用。此行为符合本工作区不启动Dock的协同分工，不代表完整系统桌面支持。
- 保留第六版的按进程诊断开关，默认关闭。本次手动规避在该状态已通过用户对照，但防启动与自动清理代码本身仍待重启后实测。程序不会恢复此前被移除的具体桌面任务；关闭总开关后重新连接走系统原生路径。

## 验证边界

本机手动移除对应任务后的DPI/缩放对照通过，不等于本版自动化逻辑已实机通过。自动启动拦截、既有任务清理、外屏应用退出及重连需针对性确认；其他测试仍SKIPPED。设置页topology-fix缺失和高刷限制仍未解决。

版本1.12.8-extend-cn.7 (245)，Extend不变。覆盖后用户手动重启，确保前版状态退出并加载新Hook。原始日志、截图和固件文件只留本地。

本地验证：JDK17/Gradle8.9 assembleDebug、lintDebug成功（0错误/5警告）；53组组件/显示器/任务边界检查、资源47条检查、职责与门控检查通过。自动化设备回归待重启后进行。
