#!/usr/bin/env python3
"""Compile six actual production kernels separately, then compile/run public consumers."""
import hashlib,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r16';OUT.mkdir(parents=True,exist_ok=True)
(OUT/'result.txt').unlink(missing_ok=True)
compiler=os.environ.get('KOTLINC','kotlinc');java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
groups=[['feature/together/src/main/kotlin/dev/fusion/together/TogetherChannel.kt','feature/together/src/main/kotlin/dev/fusion/together/PeerClock.kt']]
sources=[ROOT/p for group in groups for p in group];jars=[]
version=subprocess.run([compiler,'-version'],capture_output=True,text=True,check=True)
print(version.stdout+version.stderr,end='')
jvm=subprocess.run([java,'-version'],capture_output=True,text=True,check=True)
for group in groups:
 name=Path(group[0]).stem;jar=OUT/(name+'.jar')
 subprocess.run([compiler,'-jvm-target','17','-module-name',name,*[str(ROOT/p) for p in group],'-d',str(jar)],check=True);jars.append(jar)

checks=sorted((ROOT/'testing/src/r16/kotlin').rglob('*.kt'));jar=OUT/'checks.jar';cp=os.pathsep.join(map(str,jars))
subprocess.run([compiler,'-jvm-target','17','-classpath',cp,*map(str,checks),'-include-runtime','-d',str(jar)],check=True)
r=subprocess.run([java,'-cp',str(jar)+os.pathsep+cp,'dev.fusion.testing.R16Checks'],capture_output=True,text=True)
print(r.stdout+r.stderr,end='')
if r.returncode:(OUT/'failure.txt').write_text(r.stdout+r.stderr)
r.check_returncode()
(OUT/'result.txt').write_text(r.stdout+'\n'+version.stdout+version.stderr+jvm.stdout+jvm.stderr+'\nSeparately compiled Together production library; no Android execution inferred.\n'+''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(ROOT))+'\n' for p in sources+checks+[Path(__file__)]+jars+[jar]))
