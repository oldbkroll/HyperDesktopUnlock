package io.maurizio.hyperdesktop;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import de.robv.android.xposed.XposedHelpers;

/** Cached per-process gate. No provider IPC on the steady-state hook path. */
final class ModuleState {
    static final String KEY = "hyperdesktop_unlock_enabled";
    static final String FLAGS_SYSTEM = "hyperdesktop_flags_system";
    static final String FLAGS_UI = "hyperdesktop_flags_systemui";
    private static volatile boolean flagsSystem;
    private static volatile boolean flagsUi;
    private static volatile boolean enabled;
    private static volatile boolean ready;
    private static long nextAttempt;

    static boolean enabled() {
        if (!ready) bootstrap();
        return enabled;
    }

    static boolean desktopEnabled(String where) {
        return enabled() && ("systemui".equals(where) ? flagsUi : flagsSystem);
    }

    private static synchronized void bootstrap() {
        if (ready || SystemClock.uptimeMillis() < nextAttempt) return;
        nextAttempt = SystemClock.uptimeMillis() + 1000;
        try {
            Class<?> at = XposedHelpers.findClass("android.app.ActivityThread", null);
            Context c = (Context) XposedHelpers.callStaticMethod(at, "currentApplication");
            if (c == null) {
                Object thread = XposedHelpers.callStaticMethod(at, "currentActivityThread");
                if (thread != null) c = (Context) XposedHelpers.callMethod(thread, "getSystemContext");
            }
            if (c == null) return;
            final Context context = c;
            ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) { refresh(context); }
            };
            for (String key : new String[]{KEY, FLAGS_SYSTEM, FLAGS_UI})
                c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(key), false, observer);
            refresh(c);
            ready = true;
        } catch (Throwable ignored) { enabled = false; } // Leave original system behavior intact.
    }

    private static void refresh(Context c) {
        try {
            enabled = Settings.Secure.getInt(c.getContentResolver(), KEY, 1) != 0;
            flagsSystem = Settings.Secure.getInt(c.getContentResolver(), FLAGS_SYSTEM, 0) != 0;
            flagsUi = Settings.Secure.getInt(c.getContentResolver(), FLAGS_UI, 0) != 0;
        } catch (Throwable ignored) { enabled = flagsSystem = flagsUi = false; }
    }

    static boolean setEnabled(Context c, boolean value) {
        if (!Settings.Secure.putInt(c.getContentResolver(), KEY, value ? 1 : 0)) return false;
        enabled = value;
        return true;
    }
    static boolean setDesktopFlags(Context c, boolean systemUi, boolean value) {
        if (!Settings.Secure.putInt(c.getContentResolver(), systemUi ? FLAGS_UI : FLAGS_SYSTEM, value ? 1 : 0)) return false;
        if (systemUi) flagsUi = value; else flagsSystem = value;
        return true;
    }
    private ModuleState() {}
}
