package io.maurizio.hyperdesktop;

/** Exact stock-home match; never target the phone, rear panel or ordinary apps. */
final class SecondaryHomePolicy {
    static boolean matches(boolean enabled, boolean physicalExternal, int display,
            String pkg, String cls) {
        return enabled && physicalExternal && display > 0
                && "com.miui.home".equals(pkg)
                && "com.miui.home.launcher.SecondaryDisplayLauncher".equals(cls);
    }
    static boolean removable(boolean enabled, boolean external, int display,
            String pkg, String cls, int activityCount) {
        return activityCount == 1 && matches(enabled, external, display, pkg, cls);
    }
    private SecondaryHomePolicy() {}
}
