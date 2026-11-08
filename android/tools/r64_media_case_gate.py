#!/usr/bin/env python3
"""Require the exact one-shot connected JUnit results, independently of PNG analysis.

This reads actual XML output. It cannot attest to a device or infer cases not
present. Identical duplicate XML cases are refused, not silently coalesced.
"""
import argparse
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET

import r25_evidence_gate
import report_device_results


def verify(root: Path, source: str | None) -> dict:
    if source is None or re.fullmatch(r'[0-9a-f]{40}', source) is None:
        raise ValueError('A full exact GitHub source SHA is required')
    if not root.is_dir() or root.is_symlink():
        raise ValueError('Connected-test result directory is missing or unsafe')
    paths = sorted(root.rglob('TEST-*.xml'))
    if not paths or len(paths) > 100:
        raise ValueError('Missing or excessive connected-test result files')
    names = []
    for path in paths:
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 16 * 1024 * 1024:
            raise ValueError('Unsafe or oversized connected-test result')
        tree = ET.parse(path)
        for node in tree.iter('testcase'):
            cls, name = node.get('classname', ''), node.get('name', '')
            if not cls or not name or len((cls + name).encode('utf-8')) > 1024:
                raise ValueError('Invalid connected-test identity')
            names.append(f'{cls}.{name}')
            if len(names) > 1000:
                raise ValueError('Too many connected-test cases')
    expected = r25_evidence_gate.expected('media')
    if len(names) != len(expected) or len(set(names)) != len(names) or set(names) != expected:
        raise ValueError('Expected exactly the three unique original named media cases')
    report = report_device_results.collect(root)
    if report['source_commit'] != source or report['counts'] != {'PASS': 3, 'FAIL': 0, 'SKIP': 0}:
        raise ValueError('All three connected cases must actually PASS on the candidate SHA')
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', default='app/build/outputs/androidTest-results/connected')
    args = parser.parse_args()
    report = verify(Path(args.root), os.environ.get('GITHUB_SHA'))
    print(f"Exact connected-media case gate: {report['counts']['PASS']}/3 PASS; pixel analysis still required.")


if __name__ == '__main__':
    main()
