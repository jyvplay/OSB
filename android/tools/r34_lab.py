#!/usr/bin/env python3
"""R34 evidence lab. Honest modes only:
  host   : run_r34_contracts + check_r34_reference + python suites + check_r26_android_syntax
           + layout-bounds self-test + page-matrix self-test + touch-target audit
           + manifest-scope gate + check_project
  device : refuses unless --device-id is supplied; helper text only, zero behavior evidence claimed.
Both modes stamp R34-RESULTS.md with a hash-and-command receipt.
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools

PYTHON_TESTS = ['test_bounded_process', 'test_ci_annotations', 'test_collect_native_evidence',
                'test_device_results', 'test_engine_intake', 'test_gradle_distribution',
                'test_module_audit', 'test_module_testability', 'test_native_lab',
                'test_native_source_audit', 'test_pilot_reporting', 'test_png_capture',
                'test_probe_android_sdk', 'test_r17_evidence', 'test_r18_evidence',
                'test_r19_evidence', 'test_r20_evidence', 'test_r21_evidence',
                'test_r22_evidence', 'test_r23_evidence', 'test_r23_input_tools',
                'test_r23_lab', 'test_r24_evidence', 'test_r24_runner', 'test_r24c_tools',
                'test_r25_tools', 'test_r26_tools', 'test_r27_tools', 'test_r28_tools',
                'test_r29_tools', 'test_r30_tools', 'test_r31_tools', 'test_r32_tools',
                'test_r33_tools', 'test_r34_tools',
                'test_record_sdk_platform', 'test_release_gate', 'test_requirements']

def sh(args, env, timeout=180):
    return subprocess.run(args, env=env, capture_output=True, text=True, timeout=timeout)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['host', 'device'])
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    p.add_argument('--device-id', default=None)
    a = p.parse_args()
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    env['PYTHONPATH'] = os.pathsep.join([str(ROOT / 'tools'), env.get('PYTHONPATH', '')])
    lines = []
    if a.mode == 'device':
        if not a.device_id:
            print('device mode requires --device-id; refusing to claim device evidence.', file=sys.stderr)
            return 2
        print('device mode only labels manual screenshots; run adb steps yourself. Nothing executed.')
        return 0
    steps = [
        (['python3', 'tools/run_r34_contracts.py', '--tools', str(a.tools)], 'host contracts'),
        (['python3', 'tools/check_r34_reference.py'], 'reference recomputation'),
        (['python3', 'tools/check_r26_android_syntax.py'], 'android syntax gate'),
        (['python3', 'tools/check_r26_layout_bounds.py'], 'layout-bounds self-test'),
        (['python3', 'tools/check_r27_pagematrix.py'], 'page-matrix self-test'),
        (['python3', 'tools/check_r34_touch_targets.py'], 'touch-target static audit'),
        (['python3', 'tools/check_r34_manifest_scope.py'], 'manifest-scope gate'),
        (['python3', 'tools/check_project.py'], 'project structure'),
    ]
    ok = True
    outputs = []
    for args, label in steps:
        r = sh(args, env, timeout=900)
        outputs.append(f'$ {" ".join(args)}\n{r.stdout}{r.stderr}')
        status = 'PASS' if r.returncode == 0 else 'FAIL'
        lines.append(f'- {label}: {status}')
        if r.returncode != 0:
            ok = False
    for t in PYTHON_TESTS:
        r = sh(['python3', '-m', 'unittest', t], env, timeout=300)
        outputs.append(f'$ python3 -m unittest {t}\n{r.stdout}{r.stderr}')
        m = re.search(r'Ran (\d+) tests?.*?\n\n(OK|FAILED)', r.stderr + r.stdout, re.S)
        lines.append(f'- python {t}: {"PASS " + m.group(1) + "/" + m.group(1) if m and m.group(2) == "OK" else "FAIL"}')
        if not m or m.group(2) != 'OK':
            ok = False
    tool_versions = 'unknown toolchain'
    manifest = a.tools.expanduser().resolve() / 'host-tools.json'
    if manifest.is_file():
        versions = json.loads(manifest.read_text()).get('versions', [])
        flat = ' / '.join(v.splitlines()[0].replace('info: ', '') for v in versions if v.strip())
        if flat:
            tool_versions = flat
    receipt = [f'R34 host lab {"PASS" if ok else "FAIL"} ({tool_versions}).',
               f'Harness: {" ".join(sys.argv)}',
               *lines,
               'Hashes (sha256, fixtures+consumers):']
    tracked = ['tools/fixtures/r34/tiny.jpg', 'tools/fixtures/r34/zlib.bin',
               'testing/src/r34/kotlin/dev/fusion/testing/R34Checks.kt']
    for t in tracked:
        receipt.append(f'  {hashlib.sha256((ROOT / t).read_bytes()).hexdigest()}  {t}')
    (ROOT / 'testing/build/r34/lab-output.txt').write_text('\n'.join(outputs))
    doc = ROOT / 'docs/R34-RESULTS.md'
    text = doc.read_text() if doc.exists() else '# R34 lab results\n\n'
    stamp = '\n'.join(receipt) + '\n'
    doc.write_text(text if 'R34 host lab ' in text else text + '\n## Lab receipt\n\n' + stamp)
    print(stamp)
    return 0 if ok else 1

if __name__ == '__main__':
    raise SystemExit(main())
