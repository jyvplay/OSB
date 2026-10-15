#!/usr/bin/env python3
"""Compile six actual production kernels separately, then compile/run public consumers."""
import hashlib,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'testing/build/r15';OUT.mkdir(parents=True,exist_ok=True)
(OUT/'result.txt').unlink(missing_ok=True)
compiler=os.environ.get('KOTLINC','kotlinc');java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
groups=[['feature/lens/src/main/kotlin/dev/fusion/lens/ViewportTransform.kt'],['feature/capture/src/main/kotlin/dev/fusion/capture/RecordingTimeline.kt'],['feature/messages/src/main/kotlin/dev/fusion/messages/TranscriptArchive.kt'],['feature/vault/src/main/kotlin/dev/fusion/vault/VaultEnvelope.kt'],['core/model/src/main/kotlin/dev/fusion/model/LaunchLinks.kt'],['format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetFrame.kt','format/ooxml/src/main/kotlin/dev/fusion/ooxml/DecimalSheet.kt']]
sources=[ROOT/p for group in groups for p in group];jars=[]
version=subprocess.run([compiler,'-version'],capture_output=True,text=True,check=True)
print(version.stdout+version.stderr,end='')
jvm=subprocess.run([java,'-version'],capture_output=True,text=True,check=True)
for group in groups:
 name=Path(group[0]).stem;jar=OUT/(name+'.jar')
 subprocess.run([compiler,'-jvm-target','17','-module-name',name,*[str(ROOT/p) for p in group],'-d',str(jar)],check=True);jars.append(jar)

checks=sorted((ROOT/'testing/src/r15/kotlin').rglob('*.kt'));jar=OUT/'checks.jar';cp=os.pathsep.join(map(str,jars))
subprocess.run([compiler,'-jvm-target','17','-classpath',cp,*map(str,checks),'-include-runtime','-d',str(jar)],check=True)
r=subprocess.run([java,'-cp',str(jar)+os.pathsep+cp,'dev.fusion.testing.R15Checks'],capture_output=True,text=True)
print(r.stdout+r.stderr,end='')
if r.returncode:(OUT/'failure.txt').write_text(r.stdout+r.stderr)
r.check_returncode()
(OUT/'result.txt').write_text(r.stdout+'\n'+version.stdout+version.stderr+jvm.stdout+jvm.stderr+'\nSix separately compiled production libraries; no Android execution inferred.\n'+''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(ROOT))+'\n' for p in sources+checks+[Path(__file__)]+jars+[jar]))
