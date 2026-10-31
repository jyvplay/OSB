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
        if row['selector'] not in ('text', 'description') or row['strategy'] not in ('filename-text', 'preview-sibling-title', 'preview-card-body', 'preview-grid-item-body'):
            raise ValueError('Unknown selector/strategy')
        if 'navigation' in row and row['navigation'] not in ('initial', 'recent', 'downloads-to-recent'):
            raise ValueError('Unknown picker root navigation')
        if row['strategy'] == 'preview-sibling-title' and (not row['matched_id'].endswith(':id/preview_icon') or
                                                           row['target_id'] != 'android:id/title'):
            raise ValueError('Preview strategy does not match observed resources')
        if row['strategy'] == 'filename-text' and (row['selector'] != 'text' or row['matched_id'] != row['target_id']):
            raise ValueError('Unverified description cannot be a filename target')
        if row['strategy'] in ('preview-card-body', 'preview-grid-item-body'):
            prefix = row['matched_id'].removesuffix(':id/preview_icon')
            if (row['selector'] != 'description' or not row['matched_id'].endswith(':id/preview_icon') or
                    row['fixture'] not in row['matched_description']):
                raise ValueError('Item route is not associated with the observed fixture preview')
            if row['strategy'] == 'preview-card-body' and row['target_id'] != prefix + ':id/item_root':
                raise ValueError('Card route target mismatch')
            if row['strategy'] == 'preview-grid-item-body' and (row.get('grid_id') != prefix + ':id/dir_list' or
                    type(row.get('target_class')) is not str or not 1 <= len(row['target_class']) <= 128):
                raise ValueError('Grid item ancestry mismatch')
            item, preview, tap = (row.get('item_bounds'), row.get('preview_bounds'), row.get('tap'))
            if any(type(v) is not list or len(v) != n or any(type(x) is not int or not 0 <= x <= 32768 for x in v)
                   for v, n in ((item, 4), (preview, 4), (tap, 2))):
                raise ValueError('Invalid picker geometry')
            il, it, ir, ib = item
            pl, pt, pr, pb = preview
            x, y = tap
            if not (ir-il >= 48 and ib-it >= 48 and il <= pl < pr <= ir and it <= pt < pb <= ib and
                    il <= x < ir and it <= y < ib and not (pl <= x < pr and pt <= y < pb)):
                raise ValueError('Tap does not avoid the preview within its unique card')
            if 'description_bounds' in row:
                boxes = row['description_bounds']
                if (type(boxes) is not list or not 1 <= len(boxes) <= 8 or preview not in boxes or
                        any(type(b) is not list or len(b) != 4 or any(type(v) is not int for v in b) or
                            not (il <= b[0] < b[2] <= ir and it <= b[1] < b[3] <= ib) for b in boxes)):
                    raise ValueError('Filename descriptions do not fit the same observed grid item')
        geometry = ('item_bounds', 'preview_bounds', 'tap') if row['strategy'] in ('preview-card-body', 'preview-grid-item-body') else ()
        if 'description_bounds' in row and row['strategy'] not in ('preview-card-body', 'preview-grid-item-body'):
            raise ValueError('Description geometry without an observed item')
        if 'description_bounds' in row:
            geometry += ('description_bounds',)
        ancestry = ('grid_id', 'target_class') if row['strategy'] == 'preview-grid-item-body' else ()
        navigation = ('navigation',) if 'navigation' in row else ()
        keys = ('api', *KEYS, *geometry, *ancestry, *navigation)
        rows.append({key: row[key] for key in keys})
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
