#!/usr/bin/env python3
"""Retain actual UI-lab preconditions. Readiness is not a native behavior pass or a retrospective diagnosis."""
import argparse
import hashlib
import json
import os
from pathlib import Path


def collect(root, api=34):
    records = []
    for path in sorted(Path(root).rglob('r25-ui-preflight.jsonl')):
        if path.stat().st_size > 256 * 1024: raise ValueError('UI preflight byte budget exceeded')
        for line in path.read_text().splitlines():
            row = json.loads(line)
            if row.get('origin') != 'android-instrumentation' or not isinstance(row.get('case'), str) or not 1 <= len(row['case']) <= 512:
                raise ValueError('Not an identified native UI-lab observation')
            for state in (row['before'], row['after']):
                if state.get('api') != api: raise ValueError('Wrong observed runtime')
                for key in ('interactive', 'keyguard_locked', 'device_locked', 'credential_secure'):
                    if type(state.get(key)) is not bool: raise ValueError('Unknown/coerced UI-lab state refused')
            if not isinstance(row.get('actions'), list) or not set(row['actions']) <= {'one-real-wake', 'one-unsecured-keyguard-dismiss'} or len(row['actions']) != len(set(row['actions'])):
                raise ValueError('Unexpected/repeated preparation actions')
            if row not in records: records.append(row)
            if len(records) > 256: raise ValueError('UI-lab observation count budget exceeded')
    candidate_records = [r for r in records if r.get('phase', 'candidate') == 'candidate']
    def ready(s): return s['interactive'] and not s['keyguard_locked'] and not s['device_locked']
    return {'source_candidate': os.environ.get('GITHUB_SHA'), 'api': api, 'records': records,
            'summary': {'observations': len(candidate_records), 'other_phase_observations': len(records) - len(candidate_records), 'not_ready_before': sum(not ready(r['before']) for r in candidate_records),
                        'not_ready_after': sum(not ready(r['after']) for r in candidate_records),
                        'wake_requests': sum('one-real-wake' in r['actions'] for r in candidate_records),
                        'keyguard_dismiss_requests': sum('one-unsecured-keyguard-dismiss' in r['actions'] for r in candidate_records)},
            'scope': 'Actual disposable UI preparation only. No case retry, production state injection, app/privacy acceptance or earlier-cause proof.'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root', type=Path, default=Path('app/build/outputs/managed_device_android_test_additional_output'))
    p.add_argument('--api', type=int, choices=(34, 37), default=34)
    p.add_argument('--require-observed', action='store_true')
    a = p.parse_args(); report = collect(a.root, a.api)
    raw = (json.dumps(report, indent=2) + '\n').encode()
    Path('r25-ui-readiness.json').write_bytes(raw)
    summary = {key: report[key] for key in ('source_candidate', 'api', 'summary', 'scope')}
    summary.update(report_sha256=hashlib.sha256(raw).hexdigest(), evidence='OBSERVED' if report['summary']['observations'] else 'NOT_OBSERVED')
    text = json.dumps(summary, separators=(',', ':'))
    if len(text.encode()) > 3500: raise ValueError('Notice budget exceeded')
    print('::notice::R25UIREADY1 ' + text)
    if a.require_observed and not report['summary']['observations']: raise SystemExit('No actual UI preparation observations retained')
    if report['summary']['not_ready_after']: raise SystemExit('A recorded UI preparation did not meet its precondition')


if __name__ == '__main__': main()
