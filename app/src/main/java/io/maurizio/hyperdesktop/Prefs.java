/*
 * HyperDesktop Unlock
 * Copyright (c) 2026 Maurizio Laschi (the-marox)
 * SPDX-License-Identifier: MIT
 */
package io.maurizio.hyperdesktop;

/** Shared protocol constants; the Extend companion has no session preferences. */
final class Prefs {
    static final String PKG = BuildConfig.APPLICATION_ID;
    static final String OLD_PKG = "io.maurizio.hyperdesktop";
    static final String PERMISSION_APPLY = PKG + ".permission.APPLY_SETTINGS";
    static final String ACTION_COMMAND = PKG + ".COMMAND";
    static final String EXTRA_CMD = "cmd";
    static final String EXTRA_DIAG = "diag";
    private Prefs() {}
}
