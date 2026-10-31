/*
 * HyperDesktop Unlock
 * Copyright (c) 2026 Maurizio Laschi (the-marox)
 * SPDX-License-Identifier: MIT
 */
package io.maurizio.hyperdesktop;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import java.lang.reflect.Method;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Preserve HyperOS Computer-choice compatibility without launching a dock.
 * Some firmware applies the choice without writing mirror_built_in_display=0.
 * Keep the original ViewModel hook and settings repair for that firmware.
 */
public final class LauncherHooks {

    private static final String VIEWMODEL =
        "com.android.systemui.display.ui.viewmodel.ConnectingDisplayViewModel";

    private static long lastTrigger = 0;

    public static void hook(LoadPackageParam lp) {
        Class<?> vm = XposedHelpers.findClassIfExists(VIEWMODEL, lp.classLoader);
        if (vm == null) { XLog.i("[launcher] ViewModel non trovato"); Diag.mark("desktop-choice", false); return; }

        int n = 0;
        for (Method m : vm.getDeclaredMethods()) {
            if (!m.getName().contains("applyConnectionChoice")) continue;
            try {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!ModuleState.enabled()) return;
                        Boolean enableMirroring = null;
                        for (Object a : p.args) if (a instanceof Boolean) { enableMirroring = (Boolean) a; break; }
                        if (Boolean.FALSE.equals(enableMirroring)) onDesktopChosen();
                    }
                });
                n++;
            } catch (Throwable t) { XLog.e("[launcher] hook fallito", t); }
        }
        XLog.i("[launcher] hook su " + n + " varianti di applyConnectionChoice");
        Diag.mark("desktop-choice", n > 0);
    }

    private static void onDesktopChosen() {
        long now = SystemClock.uptimeMillis();
        if (now - lastTrigger < 10_000) return;   // applyConnectionChoice e' una suspend fun: chiamata 2 volte
        lastTrigger = now;
        final Handler h = new Handler(Looper.getMainLooper());
        // le impostazioni si leggono fuori dal percorso della finestra
        h.post(new Runnable() {
            @Override public void run() {
                try {
                    Context app = (Context) XposedHelpers.callStaticMethod(
                        XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication");
                    if (app != null) ensureDesktopSetting(app);
                } catch (Throwable t) { XLog.e("[launcher] scrittura di mirror_built_in_display = 0 fallita", t); }
            }
        });
    }

    /**
     * Scelta Computer: su alcune build (Xiaomi 14 houji, OS4.0.27, issue #2) SystemUI
     * applica la scelta ma non scrive mai mirror_built_in_display = 0 (lo Xiaomi 15 lo fa
     * da se'), e il monitor resta in mirroring. Lo scriviamo noi, come fa il riquadro
     * "Monitor"; la guardia in system_server impedisce poi che torni a 1.
     */
    private static void ensureDesktopSetting(Context ctx) {
        if (!ModuleState.enabled()) return;
        ContentResolver cr = ctx.getContentResolver();
        if (Settings.Secure.getInt(cr, SettingsGuardHooks.KEY_MIRROR, 0) != 1) return;
        Settings.Secure.putInt(cr, SettingsGuardHooks.KEY_MIRROR, 0);
        XLog.i("[launcher] SystemUI non ha scritto " + SettingsGuardHooks.KEY_MIRROR + " = 0: lo scrivo io");
    }

    private LauncherHooks() {}
}
