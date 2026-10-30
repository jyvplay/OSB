#!/usr/bin/env python3
"""Publish bounded selector observations from the disposable Android picker test.

These observations describe which UI object the real test clicked. They are not a
picker/feature pass; JUnit device outcomes must be consulted separately.
"""
import json
from pathlib import Path
import re

FIXTURE = re.compile(r'r25-(?:src|rec)-[a-f0-9]{8}\.png\Z')
KEYS = ('fixture', 'foreground', 'selector', 'matched_id', 'matched_description', 'strategy', 'target_id')


def collect(root):
    paths = sorted(Path(root).rglob('r25-picker-selection-*.json'))
    if len(paths) > 8:
        raise ValueError('Too many picker observations')
    rows = []
    for path in paths:
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 4096:
            raise ValueError('Unsafe picker observation')
        row = json.loads(path.read_text(encoding='utf-8'))
        if type(row.get('schema')) is not int or row['schema'] != 1 or row.get('origin') != 'android-instrumentation' or type(row.get('api')) is not int or row['api'] < 34:
            raise ValueError('Not an identified Android picker observation')
        if any(type(row.get(key)) is not str or len(row[key]) > 160 for key in KEYS):
            raise ValueError('Invalid picker field')
        if not FIXTURE.fullmatch(row['fixture']) or path.name != 'r25-picker-selection-' + row['fixture'][:-4] + '.json':
            raise ValueError('Not a disposable fixture')
        if row['selector'] not in ('text', 'description') or row['strategy'] not in ('filename-text', 'preview-sibling-title'):
            raise ValueError('Unknown selector/strategy')
        if row['strategy'] == 'preview-sibling-title' and (not row['matched_id'].endswith(':id/preview_icon') or
                                                           row['target_id'] != 'android:id/title'):
            raise ValueError('Preview strategy does not match observed resources')
        if row['strategy'] == 'filename-text' and (row['selector'] != 'text' or row['matched_id'] != row['target_id']):
            raise ValueError('Unverified description cannot be a filename target')
        rows.append({key: row[key] for key in ('api', *KEYS)})
    return rows


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', default='app/build/outputs/managed_device_android_test_additional_output')
    args = parser.parse_args()
    rows = collect(args.root)
    if not rows:
        print('::notice::R25PICKER1 No picker selector observations; no selection success inferred.')
        return
    for row in rows:
        text = json.dumps(row, ensure_ascii=True, separators=(',', ':'))
        # Escape GitHub command delimiters even though the test uses disposable fixtures.
        print('::notice::R25PICKER1 ' + text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))


if __name__ == '__main__':
    main()
