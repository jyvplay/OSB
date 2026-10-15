#!/usr/bin/env python3
"""Compile six actual production kernels separately, then compile/run public consumers."""
import hashlib,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r14';OUT.mkdir(parents=True,exist_ok=True)
(OUT/'result.txt').unlink(missing_ok=True)
compiler=os.environ.get('KOTLINC','kotlinc');java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
paths=['feature/files/src/main/kotlin/dev/fusion/filesui/DirectoryQuery.kt','core/designsystem/src/main/kotlin/dev/fusion/designsystem/Contrast.kt','feature/accessibility/src/main/kotlin/dev/fusion/a11y/SpatialFocus.kt','feature/alerts/src/main/kotlin/dev/fusion/alerts/NoticeLedger.kt','feature/settings/src/main/kotlin/dev/fusion/settingsui/SettingsPatch.kt','format/ooxml/src/main/kotlin/dev/fusion/ooxml/DecimalSheet.kt']
sources=[ROOT/p for p in paths];jars=[]
version=subprocess.run([compiler,'-version'],capture_output=True,text=True,check=True)
print(version.stdout+version.stderr,end='')
jvm=subprocess.run([java,'-version'],capture_output=True,text=True,check=True)
for source in sources:
 jar=OUT/(source.stem+'.jar');subprocess.run([compiler,'-jvm-target','17','-module-name',source.stem,str(source),'-d',str(jar)],check=True);jars.append(jar)
checks=sorted((ROOT/'testing/src/r14/kotlin').rglob('*.kt'));jar=OUT/'checks.jar';cp=os.pathsep.join(map(str,jars))
subprocess.run([compiler,'-jvm-target','17','-classpath',cp,*map(str,checks),'-include-runtime','-d',str(jar)],check=True)
r=subprocess.run([java,'-cp',str(jar)+os.pathsep+cp,'dev.fusion.testing.R14Checks'],capture_output=True,text=True)
print(r.stdout+r.stderr,end='')
if r.returncode:(OUT/'failure.txt').write_text(r.stdout+r.stderr)
r.check_returncode()
(OUT/'result.txt').write_text(r.stdout+'\n'+version.stdout+version.stderr+jvm.stdout+jvm.stderr+'\nSix separately compiled production libraries; no Android execution inferred.\n'+''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(ROOT))+'\n' for p in sources+checks+[Path(__file__)]+jars+[jar]))
