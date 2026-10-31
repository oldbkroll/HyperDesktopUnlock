package io.maurizio.hyperdesktop;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** The stock secondary home shares MiuiHome's phone gesture state on this firmware.
 * The companion profile launches apps directly and does not require that activity.
 */
final class SecondaryHomeHooks {
    private static boolean supported() {
        return "popsicle".equals(Build.DEVICE)
                && "OS4.0.5.0.XPBCNXM".equals(Build.VERSION.INCREMENTAL);
    }

    static void hook(LoadPackageParam lp) {
        if (!supported()) return;
        XposedHelpers.findAndHookMethod("com.android.server.wm.ActivityStartController",
                lp.classLoader, "startHomeActivity", Intent.class, ActivityInfo.class,
                String.class, "com.android.server.wm.TaskDisplayArea", boolean.class,
                new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!ModuleState.enabled()) return;
                try {
                    ActivityInfo info = (ActivityInfo) p.args[1];
                    if (info == null || p.args[3] == null) return;
                    Object dc = XposedHelpers.callMethod(p.args[3], "getDisplayContent");
                    int display = ExternalDisplayGate.displayIdOf(dc);
                    if (!SecondaryHomePolicy.matches(ModuleState.enabled(), ExternalDisplayGate.isExternal(dc),
                            display, info.packageName, info.name)) return;
                    p.setResult(null);
                    XLog.i("[secondary-home] skipped stock external home on display " + display);
                } catch (Throwable t) { XLog.e("[secondary-home] start inspection failed", t); }
            }
        });
        Diag.mark("secondary-home", true);
    }

    /** Called only from the existing control handler, never from a WMS locked hook.
     * Remove only a single-activity external root whose top is the exact stock
     * SecondaryDisplayLauncher; leave the phone launcher process and app roots alone.
     */
    static void cleanup(Context context) {
        if (!supported() || !ModuleState.enabled() || !ExternalDisplayGate.present) return;
        int display = ExternalDisplayGate.externalId;
        if (display <= 0) return;
        try {
            Object atm = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityTaskManager", null), "getService");
            List<?> roots = (List<?>) XposedHelpers.callMethod(atm, "getAllRootTaskInfosOnDisplay", display);
            if (roots == null) return;
            for (Object root : roots) {
                int actualDisplay = XposedHelpers.getIntField(root, "displayId");
                if (actualDisplay != display) continue;
                ComponentName top = (ComponentName) XposedHelpers.getObjectField(root, "topActivity");
                if (top == null) continue;
                int count = XposedHelpers.getIntField(root, "numActivities");
                if (!SecondaryHomePolicy.removable(ModuleState.enabled(), true, actualDisplay,
                        top.getPackageName(), top.getClassName(), count)) continue;
                int id = XposedHelpers.getIntField(root, "taskId");
                if (id <= 0) continue;
                Object removed = XposedHelpers.callMethod(atm, "removeTask", id);
                XLog.i("[secondary-home] existing stock external root " + id + " removed=" + removed);
            }
        } catch (Throwable t) { XLog.e("[secondary-home] existing task cleanup failed", t); }
    }
    private SecondaryHomeHooks() {}
}
