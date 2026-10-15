#!/usr/bin/env python3
"""Actual A/B Android regression: prior MainActivity + current test, then restore candidate source.
Not a mock, simulated focus model, or full build of the prior commit. Run only in disposable CI.
"""
from pathlib import Path
import subprocess
import shutil
import xml.etree.ElementTree as ET
import hashlib
import json
import os

if os.environ.get('CI')!='true': raise SystemExit('Disposable CI execution required')
source=Path('app/src/main/java/dev/fusion/shell/MainActivity.kt')
prior='428018c0181d0caca8e469ad3d42f48459337c70'
original=source.read_bytes()
subprocess.run(['git','fetch','--no-tags','--depth=1','origin',prior],check=True)
baseline=subprocess.check_output(['git','show',prior+':android/'+str(source)])
result_root=Path('app/build/outputs/androidTest-results/managedDevice')
destination=Path('focus-before-results')
if destination.exists(): shutil.rmtree(destination)
try:
    if result_root.exists(): shutil.rmtree(result_root) # No stale case can satisfy the baseline experiment.
    source.write_bytes(baseline)
    with open('focus-before.log','w') as log:
        result=subprocess.run(['./gradlew',':app:pixel6Api34ProDebugAndroidTest',
            '-Pandroid.testInstrumentationRunnerArguments.fusionDisposable=true',
            '-Pandroid.testInstrumentationRunnerArguments.class=dev.fusion.shell.ShellSmokeTest#activatingWindowKeepsChildFocus'],stdout=log,stderr=subprocess.STDOUT)
    cases=[]
    for path in result_root.rglob('TEST-*.xml'):
        cases.extend(ET.parse(path).iter('testcase'))
    selected=[c for c in cases if c.get('name')=='activatingWindowKeepsChildFocus']
    failures=[f for c in selected for f in c.findall('failure')]
    detail='\n'.join(f.get('message','')+'\n'+(f.text or '') for f in failures)
    if result.returncode==0 or len(selected)!=1 or len(failures)!=1 or 'Focused' not in detail:
        from ci_annotations import annotation
        print(annotation('focus-before.log','Focus counterexample experiment'))
        raise RuntimeError('Expected native child-focus assertion counterexample was not established; inspect focus-before.log')
    record={'kind':'actual counterfactual Android build: prior MainActivity with current tests/other sources',
        'prior_main_commit':prior,'candidate_commit':os.environ.get('GITHUB_SHA'),
        'prior_main_sha256':hashlib.sha256(baseline).hexdigest(),
        'candidate_main_sha256':hashlib.sha256(original).hexdigest(),
        'gradle_exit':result.returncode,'expected_focus_failure_observed':True,'detail':detail[:1800]}
    Path('focus-before-result.json').write_text(json.dumps(record,indent=2)+'\n')
    message='Expected prior-source focus counterexample observed: '+detail[:1400]
    print('::notice::'+message.replace('%','%25').replace('\r','%0D').replace('\n','%0A'))
finally:
    source.write_bytes(original)
    # Never label counterfactual XML as candidate-source execution in report_device_results.py.
    if result_root.exists(): shutil.move(result_root,destination)
