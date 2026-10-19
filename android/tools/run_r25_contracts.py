#!/usr/bin/env python3
"""Separately compile real PNG production/consumer code, execute real files and independent decoder checks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[1]

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reference-tools',type=Path,default=Path.home()/'.cache/fusion-r25-reference')
    a=p.parse_args();out=ROOT/'testing/build/r25';out.mkdir(parents=True,exist_ok=True)
    result=out/'result.json';result.unlink(missing_ok=True)
    production=ROOT/'format/image/src/main/kotlin/dev/fusion/format/image/PngMetadata.kt'
    checks=ROOT/'testing/src/r25/kotlin/dev/fusion/testing/PngChecks.kt'
    oracle=ROOT/'tools/check_r25_png_reference.py'
    compiler=os.environ.get('KOTLINC','kotlinc');java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    commands=[];start=time.monotonic()
    def run(args,**kw):commands.append(args);return subprocess.run(args,check=True,**kw)
    run([compiler,'-jvm-target','17','-module-name','r25_png_production',str(production),'-d',str(out/'png-production.jar')])
    run([compiler,'-jvm-target','17','-module-name','r25_png_consumer','-classpath',str(out/'png-production.jar'),str(checks),'-include-runtime','-d',str(out/'png-checks.jar')])
    native=run([java,'-cp',os.pathsep.join(str(out/n) for n in ('png-checks.jar','png-production.jar')),'dev.fusion.testing.PngChecks'],capture_output=True,text=True)
    (out/'result.txt').write_text(native.stdout+native.stderr);print(native.stdout,end='')
    reference=a.reference_tools.expanduser().resolve()/'python'
    if not (reference/'PIL/__init__.py').is_file():raise SystemExit('Actual pinned reference decoder missing. Run bootstrap_r25_reference.py install; no reference pass inferred.')
    env=os.environ.copy();env['PYTHONPATH']=str(reference)+(os.pathsep+env['PYTHONPATH'] if env.get('PYTHONPATH') else '')
    comparison=run([sys.executable,str(oracle),'--report',str(out/'png-reference.json')],capture_output=True,text=True,env=env);print(comparison.stdout,end='')
    report={'elapsed_seconds':round(time.monotonic()-start,3),'exit_code':0,'commands':commands,'output':native.stdout,
            'reference':json.loads((out/'png-reference.json').read_text()),
            'sha256':{str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in (production,checks,oracle)},
            'scope':'Actual separate host compiler/consumer/files/reference decoding only; NOT Android isolation/UI, arbitrary image fidelity, malware removal or anonymity.'}
    result.write_text(json.dumps(report,indent=2)+'\n')

if __name__=='__main__':main()
