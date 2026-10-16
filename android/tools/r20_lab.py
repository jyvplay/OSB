#!/usr/bin/env python3
"""No-Studio orchestration: real host checks or explicit native Actions dispatch. No emulator substitute."""
import argparse,os,shutil,subprocess,sys,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def run(args):subprocess.run(args,cwd=ROOT,check=True)
def doctor():
 java=Path(os.environ.get('JAVA_HOME',''))/'bin/java';compiler=os.environ.get('KOTLINC','kotlinc')
 data={'java':str(java) if java.is_file() else shutil.which('java'),'kotlinc':shutil.which(compiler),'gh':shutil.which('gh'),'adb':shutil.which('adb'),'ANDROID_HOME':os.environ.get('ANDROID_HOME'),
       'scope':'Availability only; missing SDK/ADB is NOT native execution. No Android Studio GUI or physical-device claim.'}
 print(json.dumps(data,indent=2));return data
p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['doctor','host','dispatch-native']);a=p.parse_args()
if a.mode=='doctor':doctor()
elif a.mode=='host':
 d=doctor()
 if not d['java'] or not d['kotlinc']:raise SystemExit('Set JAVA_HOME (JDK17) and KOTLINC (Kotlin2.2.21); no compilation inferred.')
 for script in ['check_project.py','run_r20_contracts.py','run_r20_interop.py','prove_r20_models.py']:run([sys.executable,'tools/'+script])
 run([sys.executable,'tools/check_r20_zip_timezone.py','--expect','equal','--report','r20-zip-timezone-after.json'])
 run([sys.executable,'-m','unittest','discover','-s','tools','-p','test_*.py'])
else:
 branch=subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()
 if not branch:raise SystemExit('Detached HEAD: refuse ambiguous remote dispatch')
 for workflow in ['android.yml','android-privacy.yml']:run(['gh','workflow','run',workflow,'--ref',branch])
 print('Dispatch requested for committed remote branch '+branch+'. Queued work is not a pass. Use gh run list/watch and collect_native_evidence.py; --privacy selects the separate companion suite.')
