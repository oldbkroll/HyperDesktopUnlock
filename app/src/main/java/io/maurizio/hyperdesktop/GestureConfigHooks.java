package io.maurizio.hyperdesktop;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Candidate for the observed native MiuiHome DisplayManager callback regression.
 * Original external events remain intact; refresh only the same home callback
 * with display 0 BASIC_CHANGED. No window configuration, size or density writes.
 */
final class GestureConfigHooks {
    private static volatile int homeUid = -1;
    static void hook(LoadPackageParam lp) {
        if (!"popsicle".equals(Build.DEVICE)
                || !"OS4.0.5.0.XPBCNXM".equals(Build.VERSION.INCREMENTAL)) {
            XLog.i("[gesture-config] skipped: firmware not validated for this candidate");
            return;
        }
        Class<?> record = XposedHelpers.findClass(
                "com.android.server.display.DisplayManagerService$CallbackRecord", lp.classLoader);
        XposedHelpers.findField(record, "mUid");
        Class<?> global = XposedHelpers.findClass("android.hardware.display.DisplayManagerGlobal", lp.classLoader);
        final int changedMask = XposedHelpers.getStaticIntField(global, "EVENT_DISPLAY_BASIC_CHANGED");
        final int lifecycleMask = XposedHelpers.getStaticIntField(global, "EVENT_DISPLAY_CONNECTED")
                | XposedHelpers.getStaticIntField(global, "EVENT_DISPLAY_ADDED")
                | XposedHelpers.getStaticIntField(global, "EVENT_DISPLAY_REMOVED")
                | XposedHelpers.getStaticIntField(global, "EVENT_DISPLAY_DISCONNECTED");
        if (changedMask != 4 || lifecycleMask != 771)
            throw new IllegalStateException("Display event mask differs from inspected firmware");
        XposedHelpers.findAndHookMethod(record, "transmitDisplayEvents", int.class, int.class,
                new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !ModuleState.enabled()) return;
                int displayId = (Integer) p.args[0];
                int mask = (Integer) p.args[1];
                if (displayId <= 0 || (mask & changedMask) == 0 || (mask & lifecycleMask) != 0) return;
                try {
                    Context c = ModuleControl.systemContext();
                    if (c == null) return;
                    // Native registrations may omit mPackageName; identify the owning UID
                    // through PackageManager instead of trusting an optional caller string.
                    if (homeUid < 0) homeUid = c.getPackageManager().getPackageUid("com.miui.home", 0);
                    if (XposedHelpers.getIntField(p.thisObject, "mUid") != homeUid) return;
                    XLog.i("[gesture-config] MiuiHome DMS event display=" + displayId + " mask=" + mask);
                    int mirror = Settings.Secure.getInt(c.getContentResolver(), SettingsGuardHooks.KEY_MIRROR, -1);
                    if (!GestureConfigPolicy.shouldRefresh(ModuleState.enabled(), mirror, displayId,
                            ExternalDisplayGate.present ? ExternalDisplayGate.externalId : -1)) return;
                    // Invoke the original method to avoid recursive hook invocation. The existing
                    // callback API makes the client query display 0's real DisplayInfo itself.
                    XposedBridge.invokeOriginalMethod(p.method, p.thisObject, new Object[]{0, changedMask});
                    XLog.i("[gesture-config] sent MiuiHome display 0 BASIC_CHANGED after display " + displayId);
                } catch (Throwable t) { XLog.e("[gesture-config] DMS refresh failed", t); }
            }
        });
        XLog.i("[gesture-config] DMS transmitDisplayEvents installed (BASIC_CHANGED=" + changedMask + ")");
        Diag.mark("gesture-config", true);
    }
    private GestureConfigHooks() {}
}
