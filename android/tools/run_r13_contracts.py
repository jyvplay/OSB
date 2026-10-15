#!/usr/bin/env python3
"""Compile five actual production kernels separately, then compile/run public consumers."""
import hashlib,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r13';OUT.mkdir(parents=True,exist_ok=True)
(OUT/'result.txt').unlink(missing_ok=True)
compiler=os.environ.get('KOTLINC','kotlinc');java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
paths=['core/files/src/main/kotlin/dev/fusion/files/ContentDigest.kt','feature/terminal/src/main/kotlin/dev/fusion/terminal/ShellWords.kt','feature/automation/src/main/kotlin/dev/fusion/automation/ActionPlan.kt','feature/continuity/src/main/kotlin/dev/fusion/continuity/CalendarExport.kt','format/ooxml/src/main/kotlin/dev/fusion/ooxml/LiteralWorkbook.kt']
sources=[ROOT/p for p in paths];jars=[]
version=subprocess.run([compiler,'-version'],capture_output=True,text=True,check=True)
print(version.stdout+version.stderr,end='')
jvm=subprocess.run([java,'-version'],capture_output=True,text=True,check=True)
for source in sources:
 jar=OUT/(source.stem+'.jar');subprocess.run([compiler,'-jvm-target','17','-module-name',source.stem,str(source),'-d',str(jar)],check=True);jars.append(jar)
checks=sorted((ROOT/'testing/src/r13/kotlin').rglob('*.kt'));jar=OUT/'checks.jar';cp=os.pathsep.join(map(str,jars))
subprocess.run([compiler,'-jvm-target','17','-classpath',cp,*map(str,checks),'-include-runtime','-d',str(jar)],check=True)
r=subprocess.run([java,'-cp',str(jar)+os.pathsep+cp,'dev.fusion.testing.R13Checks'],capture_output=True,text=True)
print(r.stdout+r.stderr,end='')
if r.returncode:(OUT/'failure.txt').write_text(r.stdout+r.stderr)
r.check_returncode()
(OUT/'result.txt').write_text(r.stdout+'\n'+version.stdout+version.stderr+jvm.stdout+jvm.stderr+'\nFive separately compiled production libraries; no Android execution inferred.\n'+''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(ROOT))+'\n' for p in sources+checks+[Path(__file__)]+jars+[jar]))
