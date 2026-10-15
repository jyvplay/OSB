#!/usr/bin/env python3
"""Five new pure kernels compiled as separate libraries; real operations, no Android mocks."""
import hashlib, os, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
out=root/'testing/build/subsets';out.mkdir(parents=True,exist_ok=True)
(out/'result.txt').unlink(missing_ok=True)
(out/'failure.txt').unlink(missing_ok=True)
compiler=os.environ.get('KOTLINC','kotlinc')
java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
sources=[];jars=[]
subprocess.run([compiler,'-version'],check=True)
for folder in ['core/settings','feature/media','format/image','feature/backup','feature/launcher','format/native']:
    files=sorted((root/folder/'src/main/kotlin').rglob('*.kt'));assert files
    jar=out/(folder.replace('/','_')+'.jar')
    subprocess.run([compiler,'-jvm-target','17','-module-name',folder.replace('/','_'),*map(str,files),'-d',str(jar)],check=True)
    sources+=files;jars.append(jar)
checks=sorted((root/'testing/src/subsets/kotlin').rglob('*.kt'))
cp=os.pathsep.join(map(str,jars));jar=out/'checks.jar'
subprocess.run([compiler,'-jvm-target','17','-classpath',cp,*map(str,checks),'-include-runtime','-d',str(jar)],check=True)
result=subprocess.run([java,'-cp',str(jar)+os.pathsep+cp,'dev.fusion.testing.SubsetChecks'],text=True,capture_output=True)
print(result.stdout,end='');print(result.stderr,end='')
if result.returncode: (out/'failure.txt').write_text(result.stdout+result.stderr)
result.check_returncode()
sources+=checks+[Path(__file__)]
(out/'result.txt').write_text(result.stdout+'\nSix separately compiled production JVM libraries; not an Android run.\n'+''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(root))+'\n' for p in sources))
