package io.maurizio.hyperdesktop;

/** Only the active physical independent display can require the phone refresh. */
final class GestureConfigPolicy {
    static boolean shouldRefresh(boolean enabled, int mirror, int changed, int external) {
        return enabled && mirror == 0 && changed > 0 && changed == external;
    }
    private GestureConfigPolicy() {}
}
