> 实机往返回归FAIL：150→200用户称“好像没事”，200→150后失效；没有出现DMS补发执行日志，不能将首次方向归因于补丁。第五版改用上层方法并过滤桌面外屏BASIC_CHANGED。

# 第四版兼容候选：DisplayManager事件路径

第三版实机回归 FAIL：用户在独立模式按要求修改DPI后，上滑导航仍失效。诊断只显示安装成功，没有MiuiHome监听器登记/补发记录；原先选择的IDisplayWindowListener路径未触达实际桌面处理。第三版不得标为已修复，本版移除该窗口监听实现。

本地只读检查固件services.jar，确认DisplayManagerService.CallbackRecord具有mPackageName/mUid/mPid及transmitDisplayEvents(int,int)方法；失败现场dumpsys display中已有com.miui.home对应PID/UID回调记录。framework.jar确认Android17使用事件位掩码：BASIC_CHANGED=4，CONNECTED=1，ADDED=2，REMOVED=256，DISCONNECTED=512。不能沿用旧版事件编号2作为CHANGED。

本版在实际transmitDisplayEvents之后，只对mUid匹配PackageManager查询的com.miui.home UID、事件含BASIC_CHANGED且不含连接/添加/移除/断开位的物理外屏事件补发display0 BASIC_CHANGED。总开关必须开启、mirror必须为0、displayId须匹配当前物理外屏。原始外屏通知和返回值不修改，其他应用无额外回调。原生注册可能省略包名，因此不信任可空的mPackageName，按系统记录的UID匹配。不再依赖窗口监听器登记，不伪造窗口Configuration，不修改内屏尺寸/密度。调用原始方法绕过Hook递归；调用失败隔离并打印日志。

候选仍只对popsicle/OS4.0.5.0.XPBCNXM安装，事件常量必须匹配本地分析结果。保留第三版的显式镜像选择补写分支。新增日志能区分“目标桌面实际收到外屏DMS事件”和“display0补发已执行”，不能只凭安装成功认定修复。

风险：客户端可能丢弃重复DisplayInfo，补发未必恢复手势；桌面同进程也管理副屏，额外事件的影响仍需回归。此候选尚未通过实机。已验证临时恢复仍为关闭总开关后重插外屏。Extend保持第二版，其他实机测试仍SKIPPED。

版本1.12.8-extend-cn.4 (242)，需覆盖后由用户手动重启。用户操作，助手只安装和抓取日志，不自动重启或注入触摸。源码不包含设备原始日志/APK。

本地验证：JDK17/Gradle8.9 assembleDebug、lintDebug成功；0错误/5警告，31组主机Java路由边界检查和源代码投递边界检查通过。实机结果待重启后验证。
