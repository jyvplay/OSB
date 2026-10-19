#!/usr/bin/env python3
"""No-Studio R25 entry. Host execution is never substituted for Android/native gates."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
from bootstrap_host_tools import environment

ROOT=Path(__file__).resolve().parents[1]
READINESS='dev.fusion.shell.R25UiReadinessDeviceTest,dev.fusion.shell.R24TaskInputDeviceTest,dev.fusion.shell.RealImeDeviceTest,dev.fusion.shell.ShellSmokeTest#editorActionsRemainVisibleAfterLongInputAtTwoHundredPercentFont'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=('doctor','host','android','device'))
    p.add_argument('--tools',type=Path);p.add_argument('--reference-tools',type=Path,default=Path.home()/'.cache/fusion-r25-reference')
    p.add_argument('--suite',choices=('readiness','png','media','full','privacy'),default='readiness');p.add_argument('--disposable',action='store_true')
    a=p.parse_args();env=environment(a.tools.expanduser().resolve()) if a.tools else os.environ.copy()
    def run(command):subprocess.run(command,cwd=ROOT,env=env,check=True)
    if a.mode=='doctor':run([sys.executable,'tools/r24c_lab.py','doctor']+(['--tools',str(a.tools)] if a.tools else []));return
    if a.mode=='host':
        run([sys.executable,'tools/r24c_lab.py','host']+(['--tools',str(a.tools)] if a.tools else []))
        run([sys.executable,'tools/run_r25_contracts.py','--reference-tools',str(a.reference_tools)]);return
    if a.tools:raise SystemExit('Host cache is a disposable JRE, not an Android SDK/full JDK; native execution refused')
    home=Path(os.environ.get('JAVA_HOME','/missing'))
    if not (home/'bin'/('javac.exe' if os.name=='nt' else 'javac')).is_file() or not (os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')):raise SystemExit('Maintained full JDK17 and actual Android SDK required; no native substitute')
    gradle='gradlew.bat' if os.name=='nt' else './gradlew'
    if a.mode=='android':run([gradle,'--no-daemon',':app:assembleProDebug',':app:assemblePlayDebug',':privacy:assembleDebug',':testing:r25PngContracts',':app:lintProDebug',':app:lintPlayDebug']);return
    if not a.disposable:raise SystemExit('Only expendable AVDs: pass --disposable explicitly')
    args=['-Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true','-Pandroid.experimental.enableAdditionalTestOutput=true']
    if a.suite=='privacy':run([gradle,':privacy:pixel6Api34DebugAndroidTest',*args]);return
    if a.suite in ('readiness','png','media'):
        classes={'readiness':READINESS,'png':'dev.fusion.shell.R25PngCopyDeviceTest','media':'dev.fusion.shell.R24LocalMediaDeviceTest,dev.fusion.shell.R25LocalVideoDeviceTest'}
        args+=['-Pandroid.testInstrumentationRunnerArguments.class='+classes[a.suite]]
    else:args+=['-Pandroid.testInstrumentationRunnerArguments.notClass=dev.fusion.shell.VisualCaptureTest']
    run([gradle,':app:pixel6Api34ProDebugAndroidTest',*args])

if __name__=='__main__':main()
