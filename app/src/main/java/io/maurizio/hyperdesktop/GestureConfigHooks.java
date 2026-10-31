package io.maurizio.hyperdesktop;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.provider.Settings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Experimental workaround for the observed HyperOS launcher gesture-region regression.
 * Keep the real external configuration notification, then refresh ONLY MiuiHome's
 * listener with an unmodified copy of display 0's actual configuration.
 */
final class GestureConfigHooks {
    private static final Map<IBinder, Object> HOME = new ConcurrentHashMap<>();
    private static final String CALLER = "hyperdesktop.gesture.uid";

    static void hook(LoadPackageParam lp) {
        // Do not silently apply a firmware-specific workaround to other devices.
        if (!"popsicle".equals(Build.DEVICE)
                || !"OS4.0.5.0.XPBCNXM".equals(Build.VERSION.INCREMENTAL)) {
            XLog.i("[gesture-config] skipped: firmware not validated for this candidate");
            return;
        }
        String wms = "com.android.server.wm.WindowManagerService";
        String listener = "android.view.IDisplayWindowListener";
        XposedHelpers.findAndHookMethod(wms, lp.classLoader, "registerDisplayWindowListener",
                listener, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                // WMS clears calling identity before delegating to the controller.
                p.setObjectExtra(CALLER, Binder.getCallingUid());
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                try {
                    Context c = ModuleControl.systemContext();
                    if (c == null) return;
                    String[] packages = c.getPackageManager().getPackagesForUid((Integer) p.getObjectExtra(CALLER));
                    if (packages == null) return;
                    for (String pkg : packages) {
                        if (!"com.miui.home".equals(pkg)) continue;
                        IBinder binder = ((IInterface) p.args[0]).asBinder();
                        HOME.put(binder, p.args[0]);
                        XLog.i("[gesture-config] MiuiHome listener registered");
                        break;
                    }
                } catch (Throwable t) { XLog.e("[gesture-config] registration inspection failed", t); }
            }
        });
        XposedHelpers.findAndHookMethod(wms, lp.classLoader, "unregisterDisplayWindowListener",
                listener, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                try { HOME.remove(((IInterface) p.args[0]).asBinder()); }
                catch (Throwable ignored) {}
            }
        });
        XposedHelpers.findAndHookMethod("com.android.server.wm.DisplayWindowListenerController",
                lp.classLoader, "dispatchDisplayChanged", "com.android.server.wm.DisplayContent",
                Configuration.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || HOME.isEmpty() || !ModuleState.enabled()) return;
                try {
                    int changed = ExternalDisplayGate.displayIdOf(p.args[0]);
                    Context c = ModuleControl.systemContext();
                    if (c == null) return;
                    int mirror = Settings.Secure.getInt(c.getContentResolver(), SettingsGuardHooks.KEY_MIRROR, -1);
                    if (!GestureConfigPolicy.shouldRefresh(ModuleState.enabled(), mirror, changed,
                            ExternalDisplayGate.present ? ExternalDisplayGate.externalId : -1)) return;
                    Object service = XposedHelpers.getObjectField(p.thisObject, "mService");
                    Object atm = XposedHelpers.getObjectField(service, "mAtmService");
                    Object root = XposedHelpers.getObjectField(atm, "mRootWindowContainer");
                    Object phone = XposedHelpers.callMethod(root, "getDisplayContent", 0);
                    if (phone == null) return;
                    Configuration config = new Configuration((Configuration)
                            XposedHelpers.callMethod(phone, "getConfiguration"));
                    if (config.screenWidthDp <= 0 || config.screenHeightDp <= 0) return;
                    for (Map.Entry<IBinder, Object> entry : HOME.entrySet()) {
                        if (!entry.getKey().isBinderAlive()) { HOME.remove(entry.getKey()); continue; }
                        try {
                            XposedHelpers.callMethod(entry.getValue(), "onDisplayConfigurationChanged",
                                    0, new Configuration(config));
                            XLog.i("[gesture-config] refreshed MiuiHome display 0 after display " + changed
                                    + " (" + config.screenWidthDp + "x" + config.screenHeightDp + "dp)");
                        } catch (Throwable t) { XLog.e("[gesture-config] refresh failed", t); }
                    }
                } catch (Throwable t) { XLog.e("[gesture-config] configuration inspection failed", t); }
            }
        });
        Diag.mark("gesture-config", true);
    }
    private GestureConfigHooks() {}
}
