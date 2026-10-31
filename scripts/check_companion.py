"""Check the system-unlock-only ownership boundary and runtime-gated core hooks."""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/io/maurizio/hyperdesktop'
base = '397fa1a78d77a159b2ba6d518093ad0c9900922f'
core = ['ConnectDialogHooks', 'DesktopModeHooks', 'SettingsGuardHooks',
        'MirrorPolicyHooks', 'SettingsHooks']
for name in core + ['LauncherHooks']:
    code = (source / (name + '.java')).read_text(encoding='utf-8')
    assert 'ModuleState.enabled()' in code, f'Ungated hook: {name}'
    assert 'XC_MethodReplacement.returnConstant' not in code
    assert 'replaceHookedMethod' not in code
    callbacks = re.findall(r'protected void (?:before|after)HookedMethod\(MethodHookParam p\) \{([^}]+)', code)
    assert callbacks and all('ModuleState.enabled()' in body for body in callbacks), name

all_code = '\n'.join(path.read_text(encoding='utf-8') for path in source.glob('*.java'))
for api in ['setForcedDisplaySize', 'setForcedDisplayDensityForUser',
            'setUserPreferredDisplayMode', 'setDisplayPowerMode', 'injectInputEvent',
            'enabled_accessibility_services', '/dev/input/', '/dev/uhid',
            'HttpURLConnection', 'interceptKeyBeforeQueueing', 'touchpad_on', 'touchpad_off']:
    assert api not in all_code, f'Unexpected session ownership: {api}'
launcher = (source / 'LauncherHooks.java').read_text(encoding='utf-8')
assert 'ensureDesktopSetting(app)' in launcher
assert 'Settings.Secure.putInt(cr, SettingsGuardHooks.KEY_MIRROR, 0)' in launcher
assert not re.search(r'startActivity|launchWhenReady|launchExtras', launcher)

android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
permissions = [p.get(android + 'name') for p in manifest.findall('uses-permission')]
assert permissions == ['${applicationId}.permission.APPLY_SETTINGS'], permissions
assert manifest.find('permission').get(android + 'protectionLevel') == 'signature'
app = manifest.find('application')
assert [s.get(android + 'name') for s in app.findall('service')] == ['.MonitorTile']
assert not app.findall('provider')
print('PASS: runtime gate covers all mutating hooks; no display/input/power/Dock ownership; protected mirror control retained')
