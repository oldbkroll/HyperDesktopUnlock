# Repository guidance

## Scope and layout
- This fork is the HyperOS system-unlock companion to Extend. Preserve the working Computer-mode dialog, anti-mirroring hooks, manual mirror recovery, compatibility checks and diagnostics. Do not force freeform/SystemUI window decorations.
- `app/src/main/java/io/maurizio/hyperdesktop/`: classic Xposed entry (`MainHook`), hooks, settings, signature-protected control and diagnostics. `app/src/main/assets/xposed_init`: module entry. `app/src/main/res/`: native Android resources. Read `docs/ARCHITECTURE_AND_PLAN.md` before changing hook dependencies.
- Baseline: upstream `the-marox/HyperDesktopUnlock`, commit `397fa1a78d77a159b2ba6d518093ad0c9900922f`, app 1.12.8 (238). MIT; preserve attribution.

## Build and validation
- AGP 8.6.1, Gradle 8.9, JDK 17, compile/target SDK 35, min SDK 31. Runtime desktop hooks are firmware-dependent; a successful build does not establish HyperOS compatibility.
- This upstream has no executable Gradle wrapper. Use an externally installed, checksum-verified Gradle 8.9: `gradle --no-daemon :app:assembleDebug :app:lintDebug`. If adding tests, use `:app:testDebugUnitTest` for meaningful pure logic tests only.
- Keep build/dependency versions unchanged. Configure SDK through ignored `local.properties` or environment. Official upstream Release requires the owner's keystore. The user explicitly requested personal Fork Release builds on 2026-10-31; those use separate locally preserved keys and must never be described as upstream-signed. Read docs/RELEASE_20261031.md for environment-based signing. Never commit keys or credentials.
- Review merged manifest, exported components/permissions, resource locale parity and format placeholders, and `git diff --check`. Test physical reconnect, rotation, resolution and mirror recovery only with explicit device authorization.
- Run `python scripts/check_locales.py` and `python scripts/check_companion.py`; the latter verifies runtime gates on all mutating hooks (revision 2) and excludes session-management APIs from this profile.

## Privileged behavior and safety
- LSPosed scope and hook registration must match the manifest and `MainHook`. Keep changes small and reversible; do not rewrite stable core hooks for cleanup.
- Document purpose/caller/permission for settings writes, broadcasts, shell execution, input injection, screen power and package/accessibility changes. Verify uncertain firmware behavior before editing it.
- No new unnecessary permissions, remote control, download-and-execute, user-data deletion, security-credential clearing, TEE/Gatekeeper/KeyMint/Keystore or system-partition modifications.
- Do not install, uninstall, reboot, flash or change device settings without the user's explicit permission. Read-only source/build checks do not authorize device changes.
- Extend owns launch UI, display size/density/refresh/rotation, touchpad/input/navigation and panel power. Avoid duplicate ownership. Preserve a route to native mirroring.

## Resources and Git
- Default `values/` stays English; Simplified Chinese goes in `values-zh-rCN/`. Externalize visible strings, preserve placeholders, XML escaping, package names and protocol/API identifiers. Review accessibility labels and long text. Do not change behavior in a localization commit.
- Work on `codex/zh-cn-extend-companion`, never experiment on `main`. `origin` is `oldbkroll/HyperDesktopUnlock`; `upstream` is read-only. Never push to upstream, force-push, delete others' changes, publish a Release or open an upstream PR without explicit authorization.
- Commit initialization/audit, localization, removal and security changes separately. Record checks and pending physical validation; use revertable commits for rollback.
- Initialized manually because this tool session cannot execute the interactive `/init` command. No pre-existing AGENTS.md was present.
