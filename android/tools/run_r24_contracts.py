#!/usr/bin/env python3
"""Real separately compiled R24 production policies and held-out consumer. No Android stubs."""
import hashlib
import os
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[1]

def main():
    out=ROOT/'testing/build/r24';out.mkdir(parents=True,exist_ok=True)
    compiler=os.environ.get('KOTLINC','kotlinc');java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    appearance=ROOT/'core/settings/src/main/kotlin/dev/fusion/settings/Appearance.kt'
    design=[ROOT/'core/designsystem/src/main/kotlin/dev/fusion/designsystem'/name for name in ['Contrast.kt','ProfessionalPalettes.kt']]
    windows=[ROOT/'core/windowing/src/main/kotlin/dev/fusion/windowing'/name for name in ['WindowEngine.kt','DesktopCommandStream.kt','InputPresentation.kt']]
    groups=[('appearance',[appearance]),('design',design),('windows',windows)]
    jars=[];sources=[]
    for name,files in groups:
        jar=out/(name+'.jar');cmd=[compiler,'-jvm-target','17','-module-name',name]
        if jars:cmd+=['-classpath',os.pathsep.join(map(str,jars))]
        subprocess.run(cmd+list(map(str,files))+['-d',str(jar)],check=True);jars.append(jar);sources+=files
    checks=sorted((ROOT/'testing/src/r24/kotlin').rglob('*.kt'));cp=os.pathsep.join(map(str,jars));consumer=out/'checks.jar'
    subprocess.run([compiler,'-jvm-target','17','-classpath',cp,*map(str,checks),'-include-runtime','-d',str(consumer)],check=True)
    p=subprocess.run([java,'-cp',str(consumer)+os.pathsep+cp,'dev.fusion.testing.R24Checks'],text=True,capture_output=True)
    (out/('result.txt' if p.returncode==0 else 'failure.txt')).write_text(p.stdout+p.stderr+'\n'+''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+str(f.relative_to(ROOT))+'\n' for f in sources+checks+[consumer]))
    print(p.stdout+p.stderr,end='');p.check_returncode()
if __name__=='__main__':main()
