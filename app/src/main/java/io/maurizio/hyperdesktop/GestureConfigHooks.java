package io.maurizio.hyperdesktop;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Firmware-specific candidate: keep external BASIC_CHANGED out of MiuiHome's
 * phone-wide display listener. Other clients and all lifecycle events are intact.
 * No synthetic default-display events or display-parameter writes.
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
        XposedHelpers.findAndHookMethod(record, "notifyDisplayEventAsync", int.class, int.class,
                new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!ModuleState.enabled()) return;
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
                    // Leave original delivery accounting and all remaining event bits intact.
                    // With mask 0 the original method returns 0 without dispatching an event.
                    p.args[1] = mask & ~changedMask;
                    XLog.i("[gesture-config] filtered MiuiHome external BASIC_CHANGED display="
                            + displayId + " mask=" + mask + " remaining=" + p.args[1]);
                } catch (Throwable t) { XLog.e("[gesture-config] DMS refresh failed", t); }
            }
        });
        XLog.i("[gesture-config] DMS notifyDisplayEventAsync filter installed (BASIC_CHANGED=" + changedMask + ")");
        Diag.mark("gesture-config", true);
    }
    private GestureConfigHooks() {}
}
