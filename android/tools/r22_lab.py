#!/usr/bin/env python3
"""R22 no-Studio host runner and explicit remote native dispatch; no fake Android substitute."""
import argparse,os,shutil,subprocess,sys,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def run(args):subprocess.run(args,cwd=ROOT,check=True)
p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['doctor','host','dispatch-native']);a=p.parse_args()
if a.mode=='doctor':
 print(json.dumps({'JAVA_HOME':os.environ.get('JAVA_HOME'),'kotlinc':shutil.which(os.environ.get('KOTLINC','kotlinc')),'gh':shutil.which('gh'),'adb':shutil.which('adb'),'ANDROID_HOME':os.environ.get('ANDROID_HOME'),'scope':'Availability only. Not execution or acceptance.'},indent=2))
elif a.mode=='host':
 for script in ['check_project.py','run_r22_contracts.py','run_r22_interop.py','run_r21_contracts.py']:run([sys.executable,'tools/'+script])
 run([sys.executable,'-m','unittest','discover','-s','tools','-p','test_*.py'])
else:
 branch=subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()
 if branch!='arena/31a7965e-osb':raise SystemExit('Refuse another branch for this session')
 for workflow in ['android.yml','android-r22.yml']:run(['gh','workflow','run',workflow,'--ref',branch])
 print('Dispatched committed REMOTE branch. Queued is not passed; collect completed runs under NEW prefixes, --focused for R22 boundary workflow.')
