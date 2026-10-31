# 第五版兼容候选：桌面外屏事件过滤

第四版往返回归FAIL。用户报告150→200好像正常、返回150后失效；日志显示桌面手势区域从1200×2608变为1440×2560，后续还有1067×1896，补发代码没有执行日志。不能认定首次方向稳定通过。原始现场保存在工作区，不上传仓库。

只读固件检查确认原生libhyper_os_app_private.dylib.so确实实现IDisplayManagerCallback.onDisplayEvent，系统CallbackRecord中也存在桌面UID。第四版Hook的transmitDisplayEvents只有6个DEX code units，可能被AOT内联，故改Hook其上层notifyDisplayEventAsync(int displayId,int eventMask)；该方法710 code units。内联原因尚未由运行时deopt证明，不作为已确认根因。

本版不再补发虚拟display0通知。在总开关开启、mirror=0、当前物理外屏、接收者UID等于PackageManager确认的com.miui.home UID、含BASIC_CHANGED=4且不含任何连接生命周期位时，移除投递给该桌面客户端的BASIC_CHANGED位，再运行原方法；0掩码由原方法正常返回0。保留其他事件位、连接/断开/添加/移除通知和其他客户端全部通知，不修改尺寸/密度，不重启桌面、不扩大作用域。关闭开关/镜像/其他固件均放行。适用范围仍严格限定popsicle/OS4.0.5.0.XPBCNXM。

风险与边界：系统桌面进程也管理SecondaryDisplayLauncher，过滤事件可能使副屏桌面的DPI/尺寸缓存不更新；第三方外屏应用的WindowManager配置路径未屏蔽，但仍需用户验证实际显示。这个候选通过阻断错误输入规避手势污染，不是修复系统桌面原生实现。尚未实机通过，不再使用“Hook安装成功”代替执行/效果验证。

保留第三版的系统连接弹窗显式镜像补写分支，仍待单独验证。Extend维持0.0.27-zh-cn.2。其他测试仍SKIPPED。版本1.12.8-extend-cn.5 (243)，覆盖后由用户手动重启。已验证恢复方式仍是关闭总开关后重插外屏。

本地检查：assembleDebug/lintDebug成功（0错误、5警告），31组路由检查、资源及职责边界检查通过；实机验证待进行。
