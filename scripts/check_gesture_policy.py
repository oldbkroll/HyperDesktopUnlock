"""Host-JDK regression checks for the candidate's callback routing boundary."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/io/maurizio/hyperdesktop'
java_home = Path(os.environ['JAVA_HOME'])
with tempfile.TemporaryDirectory() as temp:
    temp = Path(temp)
    harness = temp / 'GesturePolicyTest.java'
    harness.write_text('''package io.maurizio.hyperdesktop;
public class GesturePolicyTest {
  public static void main(String[] args) {
    int checked = 0;
    for (boolean enabled : new boolean[]{false, true}) {
      for (int mirror : new int[]{-1, 0, 1}) {
        for (int changed : new int[]{-1, 0, 1, 2, 3}) {
          boolean expected = enabled && mirror == 0 && changed == 2;
          if (GestureConfigPolicy.shouldRefresh(enabled, mirror, changed, 2) != expected)
            throw new AssertionError(enabled + ":" + mirror + ":" + changed);
          checked++;
        }
      }
    }
    if (GestureConfigPolicy.shouldRefresh(true, 0, -1, -1)) throw new AssertionError();
    System.out.println("PASS: " + (checked + 1) + " routing combinations");
  }
}''', encoding='utf-8')
    subprocess.run([str(java_home/'bin/javac.exe'), '-d', str(temp),
                    str(src/'GestureConfigPolicy.java'), str(harness)], check=True)
    subprocess.run([str(java_home/'bin/java.exe'), '-cp', str(temp),
                    'io.maurizio.hyperdesktop.GesturePolicyTest'], check=True)

code = (src/'GestureConfigHooks.java').read_text(encoding='utf-8')
assert '"com.miui.home".equals(pkg)' in code
assert 'new Configuration(config)' in code
assert 'afterHookedMethod' in code and 'p.setResult' not in code
assert 'GestureConfigPolicy.shouldRefresh(ModuleState.enabled()' in code
assert '"OS4.0.5.0.XPBCNXM".equals(Build.VERSION.INCREMENTAL)' in code
print('PASS: launcher-only, original callbacks preserved, actual phone config copied, firmware gated')
