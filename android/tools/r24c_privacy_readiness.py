#!/usr/bin/env python3
"""Summarize actual test-lab posture observations, never privacy acceptance or a guessed root cause."""
import argparse
import hashlib
import json
import os
from pathlib import Path


def collect(root, expected_api):
    records = []
    for path in sorted(Path(root).rglob('r24c-privacy-preflight.jsonl')):
        if path.stat().st_size > 64 * 1024: raise ValueError('Preflight observation byte budget exceeded')
        for line in path.read_text().splitlines():
            row = json.loads(line)
            if row.get('origin') != 'android-instrumentation' or not isinstance(row.get('case'), str):
                raise ValueError('Not a native readiness observation')
            for state in (row['before'], row['after']):
                if state.get('api') != expected_api: raise ValueError('Wrong actual runtime observation')
                for key in ('interactive', 'keyguard_locked', 'device_locked', 'credential_secure'):
                    if type(state.get(key)) is not bool: raise ValueError('Readiness flags must be actual booleans')
            if not isinstance(row.get('actions'), list) or not set(row['actions']) <= {'one-real-wake', 'one-unsecured-keyguard-dismiss'}:
                raise ValueError('Unexpected lab action')
            if row not in records: records.append(row)
            if len(records) > 64: raise ValueError('Too many preflight observations')
    ready = lambda state: state['interactive'] and not state['keyguard_locked'] and not state['device_locked']
    return {'source_candidate': os.environ.get('GITHUB_SHA'), 'runtime_api': expected_api, 'observations': records,
            'summary': {'records': len(records), 'not_ready_before': sum(not ready(r['before']) for r in records),
                        'not_ready_after': sum(not ready(r['after']) for r in records),
                        'wake_requests': sum('one-real-wake' in r['actions'] for r in records),
                        'keyguard_dismiss_requests': sum('one-unsecured-keyguard-dismiss' in r['actions'] for r in records)},
            'scope': 'Actual disposable-emulator preconditions only. No workspace rearm, test retry, privacy acceptance or retrospective root-cause proof.'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, default=Path('privacy/build/outputs/managed_device_android_test_additional_output'))
    p.add_argument('--api', type=int, choices=(34, 37), required=True)
    a = p.parse_args(); report = collect(a.root, a.api)
    raw = (json.dumps(report, indent=2) + '\n').encode()
    Path('privacy-readiness.json').write_bytes(raw)
    notice = {key: report[key] for key in ('source_candidate', 'runtime_api', 'summary', 'scope')}
    notice['report_sha256'] = hashlib.sha256(raw).hexdigest()
    notice['evidence'] = 'OBSERVED' if report['observations'] else 'NOT_OBSERVED'
    print('::notice::R24CPRIVACYREADY1 ' + json.dumps(notice, separators=(',', ':')))


if __name__ == '__main__': main()
