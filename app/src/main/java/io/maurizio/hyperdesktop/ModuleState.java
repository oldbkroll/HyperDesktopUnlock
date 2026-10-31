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
    private static volatile boolean enabled;
    private static volatile boolean ready;
    private static long nextAttempt;

    static boolean enabled() {
        if (!ready) bootstrap();
        return enabled;
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
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override public void onChange(boolean selfChange) { refresh(context); }
                    });
            refresh(c);
            ready = true;
        } catch (Throwable ignored) { enabled = false; } // Leave original system behavior intact.
    }

    private static void refresh(Context c) {
        try { enabled = Settings.Secure.getInt(c.getContentResolver(), KEY, 1) != 0; }
        catch (Throwable ignored) { enabled = false; }
    }

    static boolean setEnabled(Context c, boolean value) {
        if (!Settings.Secure.putInt(c.getContentResolver(), KEY, value ? 1 : 0)) return false;
        enabled = value;
        return true;
    }
    private ModuleState() {}
}
