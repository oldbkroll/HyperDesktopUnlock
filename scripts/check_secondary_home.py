"""Host-JDK tests for the exact component/display/root-removal boundary."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/io/maurizio/hyperdesktop'
jdk = Path(os.environ['JAVA_HOME'])
with tempfile.TemporaryDirectory() as folder:
    out = Path(folder)
    harness = out / 'SecondaryHomeTest.java'
    harness.write_text('''package io.maurizio.hyperdesktop;
public class SecondaryHomeTest {
  public static void main(String[] args) {
    String pkg = "com.miui.home";
    String cls = "com.miui.home.launcher.SecondaryDisplayLauncher";
    int checked = 0;
    for (boolean enabled : new boolean[]{false, true})
      for (boolean external : new boolean[]{false, true})
        for (int display : new int[]{-1, 0, 1, 2})
          for (String name : new String[]{cls, "com.miui.home.launcher.Launcher", "other.App"}) {
            boolean expected = enabled && external && display > 0 && cls.equals(name);
            if (SecondaryHomePolicy.matches(enabled, external, display, pkg, name) != expected)
              throw new AssertionError(enabled + ":" + external + ":" + display + ":" + name);
            checked++;
          }
    if (SecondaryHomePolicy.matches(true, true, 2, "other.package", cls)) throw new AssertionError();
    for (int count : new int[]{0, 1, 2, 10}) {
      if (SecondaryHomePolicy.removable(true, true, 2, pkg, cls, count) != (count == 1))
        throw new AssertionError("activityCount=" + count);
      checked++;
    }
    System.out.println("PASS: " + (checked + 1) + " component/display/task boundary cases");
  }
}''', encoding='utf-8')
    subprocess.run([str(jdk/'bin/javac.exe'), '-d', str(out),
                    str(src/'SecondaryHomePolicy.java'), str(harness)], check=True)
    subprocess.run([str(jdk/'bin/java.exe'), '-cp', str(out),
                    'io.maurizio.hyperdesktop.SecondaryHomeTest'], check=True)
code = (src/'SecondaryHomeHooks.java').read_text(encoding='utf-8')
assert 'actualDisplay != display' in code and 'if (id <= 0) continue;' in code
assert 'SecondaryHomePolicy.removable' in code and 'SecondaryHomePolicy.matches' in code
assert 'ExternalDisplayGate.isExternal(dc)' in code
assert '"OS4.0.5.0.XPBCNXM".equals(Build.VERSION.INCREMENTAL)' in code
for forbidden in ['forceStopPackage', 'killProcess', 'Runtime.getRuntime', 'setForcedDisplay']:
    assert forbidden not in code
print('PASS: exact external component only; no process kill, shell or display parameter writes')
