/*
 * HyperDesktop Unlock
 * Copyright (c) 2026 Maurizio Laschi (the-marox)
 * SPDX-License-Identifier: MIT
 */
package io.maurizio.hyperdesktop;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeSet;

/** Unlock status, explicit mode switching and diagnostics. Extend owns session controls. */
public class SettingsActivity extends Activity {

    private LinearLayout root;
    private android.widget.Switch master;
    private boolean updatingMaster;
    private TextView oldWarn;
    private Button oldButton;
    private ModuleClient.Callback modeUpdate;
    private DisplayManager displayManager;
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) { refreshModeStatus(); }
        @Override public void onDisplayRemoved(int id) { refreshModeStatus(); }
        @Override public void onDisplayChanged(int id) { refreshModeStatus(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(8), pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        header(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
        oldPackageRow();
        note(getString(R.string.companion_summary));
        note(getString(R.string.tiles_hint));
        section(R.string.section_monitor);
        master = new android.widget.Switch(this);
        master.setText(R.string.master_enabled);
        master.setEnabled(false);
        root.addView(master, matchWidth());
        note(getString(R.string.master_hint));
        master.setOnCheckedChangeListener((button, checked) -> {
            if (updatingMaster) return;
            master.setEnabled(false);
            ModuleClient.send(this, checked ? "enable" : "disable", false, data -> {
                modeUpdate.onResult(data);
                Map<String, String> state = ModuleClient.parse(data);
                if (state == null || !(checked ? "1" : "0").equals(state.get("enabled"))
                        || (!checked && !"-1".equals(state.get("monitor")) && !"1".equals(state.get("mirror"))))
                    Toast.makeText(this, R.string.mode_switch_failed, Toast.LENGTH_LONG).show();
            });
        });
        modeControls();
        section(R.string.section_diag);
        diagnosticsRow();
    }

    private void modeControls() {
        final TextView status = note(getString(R.string.diag_checking));
        final Button desktop = new Button(this);
        final Button mirror = new Button(this);
        desktop.setText(R.string.mode_switch_desktop);
        mirror.setText(R.string.mode_switch_mirror);
        desktop.setAllCaps(false);
        mirror.setAllCaps(false);
        desktop.setEnabled(false);
        mirror.setEnabled(false);
        root.addView(desktop, matchWidth());
        root.addView(mirror, matchWidth());
        modeUpdate = data -> {
            if (isFinishing() || isDestroyed()) return;
            Map<String, String> s = ModuleClient.parse(data);
            boolean current = s != null && BuildConfig.VERSION_NAME.equals(s.get("version"));
            boolean enabled = current && "1".equals(s.get("enabled"));
            updatingMaster = true;
            master.setChecked(enabled);
            master.setEnabled(current);
            updatingMaster = false;
            if (s != null && !BuildConfig.VERSION_NAME.equals(s.get("version"))) {
                desktop.setEnabled(false);
                mirror.setEnabled(false);
                status.setText(getString(R.string.diag_reboot, s.get("version"), BuildConfig.VERSION_NAME));
                return;
            }
            boolean connected = s != null && s.get("monitor") != null && !"-1".equals(s.get("monitor"));
            boolean mirrored = s != null && "1".equals(s.get("mirror"));
            desktop.setEnabled(enabled && (connected || mirrored));
            mirror.setEnabled(enabled && connected);
            status.setText(getString(s == null ? R.string.diag_no_answer
                    : !enabled ? R.string.master_disabled : mirrored ? R.string.mode_status_mirroring
                    : connected ? R.string.mode_status_computer : R.string.tile_no_monitor));
        };
        desktop.setOnClickListener(v -> switchMode("desktop", desktop, mirror, modeUpdate));
        mirror.setOnClickListener(v -> switchMode("mirror", desktop, mirror, modeUpdate));
    }

    private void refreshModeStatus() {
        if (modeUpdate != null) ModuleClient.send(this, "status", false, modeUpdate);
    }

    private void switchMode(String cmd, Button desktop, Button mirror, ModuleClient.Callback update) {
        desktop.setEnabled(false);
        mirror.setEnabled(false);
        ModuleClient.send(this, cmd, false, data -> {
            if (isFinishing() || isDestroyed()) return;
            update.onResult(data);
            Map<String, String> s = ModuleClient.parse(data);
            String expected = "mirror".equals(cmd) ? "1" : "0";
            if (s == null || !expected.equals(s.get("mirror"))) {
                Toast.makeText(this, R.string.mode_switch_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Fino alla 1.12.7 il pacchetto era io.maurizio.hyperdesktop: per Android e' un'altra
     * app, che resterebbe installata (e attiva in LSPosed) accanto a questa. Avviso, con
     * un pulsante che apre la sua pagina nelle impostazioni per disinstallarla.
     */
    private void oldPackageRow() {
        oldWarn = note(getString(R.string.old_package_warning));
        oldButton = new Button(this);
        oldButton.setAllCaps(false);
        oldButton.setText(R.string.old_package_button);
        oldButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + Prefs.OLD_PKG)));
                } catch (Throwable t) {
                    Toast.makeText(SettingsActivity.this, R.string.old_package_warning, Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(oldButton, matchWidth());
        updateOldPackageRow();
    }

    private void updateOldPackageRow() {
        if (oldWarn == null) return;
        boolean installed;
        try { getPackageManager().getPackageInfo(Prefs.OLD_PKG, 0); installed = true; }
        catch (Throwable t) { installed = false; }
        int vis = installed ? View.VISIBLE : View.GONE;
        oldWarn.setVisibility(vis);
        oldButton.setVisibility(vis);
    }

    @Override protected void onResume() {
        super.onResume();
        updateOldPackageRow();   // di ritorno dalla disinstallazione
        displayManager = getSystemService(DisplayManager.class);
        if (displayManager != null) displayManager.registerDisplayListener(displayListener, new Handler(getMainLooper()));
        refreshModeStatus();
    }

    @Override protected void onPause() {
        if (displayManager != null) displayManager.unregisterDisplayListener(displayListener);
        super.onPause();
    }

    // ------------------------------------------------------------------
    // diagnostica
    // ------------------------------------------------------------------
    private static final String ISSUES_URL = "https://github.com/oldbkroll/HyperDesktopUnlock/issues/new";

    private void diagnosticsRow() {
        final TextView status = note(getString(R.string.diag_checking));
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(R.string.diag_report_button);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { createReport(); }
        });
        root.addView(b, matchWidth());
        small(getString(R.string.diag_note));
        ModuleClient.send(this, "status", true, new ModuleClient.Callback() {
            @Override public void onResult(String data) {
                if (isFinishing() || isDestroyed()) return;
                status.setText(statusLine(data));
            }
        });
    }

    /** Una riga per l'utente: tutto a posto, riavvio necessario, o cosa manca. */
    private String statusLine(String data) {
        Map<String, String> s = ModuleClient.parse(data);
        if (s == null) return getString(R.string.diag_no_answer);
        String loaded = s.get("version");
        if (loaded != null && !loaded.equals(BuildConfig.VERSION_NAME)) {
            return getString(R.string.diag_reboot, loaded, BuildConfig.VERSION_NAME);
        }
        TreeSet<String> missing = new TreeSet<>();
        if (!data.contains("\n=== systemui ===\n")) missing.add("systemui-report");
        for (String line : data.split("\n")) {
            if (!line.startsWith("[diag]")) continue;
            int colon = line.indexOf(": ");
            String body = colon > 0 ? line.substring(colon + 2) : line;
            int bar = body.indexOf(" | ");
            if (bar >= 0) body = body.substring(0, bar);
            for (String part : body.split(", ")) {
                if (part.endsWith(" MISSING")) missing.add(part.substring(0, part.length() - 8).trim());
            }
        }
        if (!missing.isEmpty()) return getString(R.string.diag_missing, android.text.TextUtils.join(", ", missing));
        return getString(R.string.diag_ok, loaded);
    }

    private void createReport() {
        final Toast wait = Toast.makeText(this, R.string.diag_collecting, Toast.LENGTH_SHORT);
        wait.show();
        // "report" = status + diagnostica completa, compresa la disposizione del monitor
        ModuleClient.send(this, "report", true, new ModuleClient.Callback() {
            @Override public void onResult(String data) {
                if (isFinishing() || isDestroyed()) return;
                showReport(buildReport(data));
            }
        });
    }

    private String buildReport(String data) {
        StringBuilder r = new StringBuilder();
        r.append("HyperDesktop Unlock ").append(BuildConfig.VERSION_NAME)
         .append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        r.append(getString(R.string.report_phone)).append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
         .append(" (").append(Build.DEVICE).append(")\n");
        r.append("Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT)
         .append(") ").append(getString(R.string.report_build)).append(Build.VERSION.INCREMENTAL).append('\n');
        String os = getprop("ro.mi.os.version.incremental");
        if (os.isEmpty()) os = getprop("ro.mi.os.version.name");
        r.append("HyperOS: ").append(os.isEmpty() ? "?" : os).append('\n');
        r.append("LSPosed: ").append(getString(ModuleClient.parse(data) != null ? R.string.report_active : R.string.report_inactive)).append('\n');
        r.append(getString(R.string.report_settings));
        r.append(' ').append(getString(R.string.companion_scope));
        r.append("\n\n");
        if (data == null) r.append(getString(R.string.diag_no_answer)).append('\n');
        else r.append(data);
        return r.toString();
    }

    private void showReport(final String report) {
        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTextIsSelectable(true);
        int pad = dp(16);
        tv.setPadding(pad, dp(8), pad, dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
            .setTitle(R.string.diag_report_title)
            .setView(sv)
            .setPositiveButton(R.string.diag_copy, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    copy(report);
                    Toast.makeText(SettingsActivity.this, R.string.diag_copied, Toast.LENGTH_SHORT).show();
                }
            })
            .setNeutralButton(R.string.diag_issue, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    copy(report);
                    Toast.makeText(SettingsActivity.this, R.string.diag_copied_issue, Toast.LENGTH_LONG).show();
                    String body = getString(R.string.report_issue_template);
                    String url = ISSUES_URL + "?body=" + Uri.encode(body);
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Throwable ignored) {}
                }
            })
            .setNegativeButton(R.string.diag_share, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject))
                            .putExtra(Intent.EXTRA_TEXT, report);
                    try { startActivity(Intent.createChooser(send, getString(R.string.diag_share))); } catch (Throwable ignored) {}
                }
            })
            .show();
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.report_subject), text));
        } catch (Throwable ignored) {}
    }

    /** Proprieta' di sistema lette con getprop (consentito alle app per quelle pubbliche). */
    private static String getprop(String key) {
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "getprop", key });
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            p.waitFor();
            return line == null ? "" : line.trim();
        } catch (Throwable t) { return ""; }
    }

    private void header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(8), 0, 0);
        root.addView(t);
    }

    private void section(int res) {
        TextView t = new TextView(this);
        t.setText(getString(res));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.06f);
        t.setPadding(0, dp(28), 0, dp(4));
        t.setAlpha(0.7f);
        root.addView(t);
    }

    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setLineSpacing(dp(3), 1f);
        t.setPadding(0, dp(8), 0, dp(4));
        t.setAlpha(0.85f);
        root.addView(t);
        return t;
    }

    private void small(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setAlpha(0.65f);
        t.setPadding(0, 0, 0, dp(6));
        root.addView(t);
    }

    private static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
