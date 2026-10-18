#!/usr/bin/env python3
"""R24 actual host compiler and actual SDK/device commands; never a substitute Android runtime."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
from bootstrap_host_tools import environment
ROOT=Path(__file__).resolve().parents[1]
GRADLE=str(ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew'))
UI_CLASSES='dev.fusion.shell.R24AppearanceDeviceTest,dev.fusion.shell.R24TaskInputDeviceTest,dev.fusion.shell.R18TaskUiDeviceTest'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['doctor','host','android','device'])
    p.add_argument('--tools',type=Path);p.add_argument('--suite',choices=['ui','privacy','memory','full','pdf'],default='ui');p.add_argument('--disposable',action='store_true')
    a=p.parse_args();env=environment(a.tools.expanduser().resolve()) if a.tools else os.environ.copy()
    def run(args):subprocess.run(args,cwd=ROOT,env=env,check=True)
    if a.mode=='host':
        run([sys.executable,'tools/r23_lab.py','host']+(['--tools',str(a.tools)] if a.tools else []))
        run([sys.executable,'tools/run_r24_contracts.py']);return
    if a.mode=='doctor':
        run([sys.executable,'tools/r23_lab.py','doctor']+(['--tools',str(a.tools)] if a.tools else []));return
    if a.tools:raise SystemExit('Host cache is an old JRE, not a native JDK/SDK. No Android execution inferred.')
    if a.mode=='android':
        run([sys.executable,'tools/r23_lab.py','android']);run([GRADLE,':testing:r24Contracts']);return
    if not a.disposable:raise SystemExit('Disposable Android/data consent required')
    if a.suite=='pdf':
        run([GRADLE,':app:pixel6Api34ProDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true','-Pandroid.testInstrumentationRunnerArguments.class=dev.fusion.shell.R24PdfDeviceTest']);return
    if a.suite in ['privacy','memory','full']:
        run([sys.executable,'tools/r23_lab.py','device','--suite',a.suite,'--disposable']);return
    if not env.get('JAVA_HOME') or not Path(env['JAVA_HOME'],'bin/'+('javac.exe' if os.name=='nt' else 'javac')).is_file() or not (env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT')):
        raise SystemExit('Actual maintained JDK17+Android SDK required; no mock native test available')
    run([GRADLE,':app:pixel6Api34ProDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true',
         '-Pandroid.testInstrumentationRunnerArguments.class='+UI_CLASSES])
if __name__=='__main__':main()
