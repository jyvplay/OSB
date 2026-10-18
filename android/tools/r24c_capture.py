#!/usr/bin/env python3
"""Bounded transport of actual native screenshots and target measurements, never reference artwork."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
from png_capture import dimensions

ROOT = Path(__file__).resolve().parents[1]
NAMES = {'r24c-tabletop.png', 'r24c-silver.png', 'r24c-wall.png', 'r24c-atlas.png'}
PATTERN = re.compile(r'R24CPNG1 (r24c-(?:tabletop|silver|wall|atlas)\.png) (\d+)/(\d+) ([0-9a-f]{64}) ([A-Za-z0-9+/=]+)')


def validate_audit(report):
    if report.get('schema') != 1 or report.get('origin') != 'android-instrumentation' or report.get('api') not in (34, 37):
        raise ValueError('Not a recognized native observation schema')
    rows = report['required_targets']
    if not 1 <= len(rows) <= 128 or len({r['tag'] for r in rows}) != len(rows):
        raise ValueError('Missing or duplicate declared targets')
    for row in rows:
        keys = ['width_dp', 'height_dp', 'visible_width_dp', 'visible_height_dp']
        if any(key not in row for key in keys): raise ValueError('Missing clipped/unclipped measured bounds')
        for key in keys:
            value = row[key]
            if type(value) not in (float, int) or not 47.99 <= value <= 32768:
                raise ValueError('Invalid/small measured target: ' + row['tag'])
    return {'measured_targets': len(rows), 'scope': 'Declared targets in a finite native frame only; metadata is not runner attestation or global beauty/AX verification.'}


MAX_PARTS = 48
MAX_CURRENT_PARTS = 45
MAX_PREVIEW_BYTES = 120 * 1024
CHUNK_CHARS = 3700  # Actual annotations were truncated at4096 bytes, not64KiB.
CHUNKS_PER_STEP = 8


def stage():
    source = ROOT / 'app/build/outputs/managed_device_android_test_additional_output'
    target = ROOT / 'testing/build/r24c/native-frames'
    if target.is_symlink(): raise ValueError('Symlink staging root refused')
    target.mkdir(parents=True, exist_ok=True)
    allowed = NAMES | {n.removesuffix('.png') + '-full.png' for n in NAMES} | {n.removesuffix('.png') + '.json' for n in NAMES}
    for name in allowed:
        previous = target / name
        if previous.is_symlink(): raise ValueError('Symlink staged frame refused')
        if previous.exists(): previous.unlink()  # Clear only this tool's named scratch outputs, never retain stale frames.
    seen = {}; total = 0
    for path in source.rglob('r24c-*'):
        if path.name not in allowed: continue
        if path.is_symlink() or not path.is_file(): raise ValueError('Not an actual frame file')
        if path.stat().st_size > 8 * 1024 * 1024: raise ValueError('Native frame artifact budget exceeded')
        data = path.read_bytes()
        if len(data) > 8 * 1024 * 1024: raise ValueError('Growing frame budget exceeded')
        if path.name in seen and seen[path.name] != data: raise ValueError('Ambiguous native frame staging')
        if path.name not in seen: total += len(data)
        if total > 32 * 1024 * 1024: raise ValueError('Native frame aggregate budget exceeded')
        seen[path.name] = data
    # No synthesized missing frames; the next job will explicitly observe absence.
    for name, data in seen.items(): (target / name).write_bytes(data)
    print(json.dumps({'staged_actual_files': sorted(seen), 'bytes': total, 'scope': 'Copy actual captured files only; not a visual pass'}))


def transport_chunks(name, data):
    if name not in NAMES: raise ValueError('Unexpected capture name')
    w, h = dimensions(data)
    if len(data) > MAX_PREVIEW_BYTES or w > 400 or h > 2000:
        raise ValueError('Capture budget exceeded')
    text = base64.b64encode(data).decode()
    parts = [text[i:i+CHUNK_CHARS] for i in range(0, len(text), CHUNK_CHARS)]
    if len(parts) > MAX_CURRENT_PARTS: return []  # Artifact-only, explicitly reported by the metadata step.
    return [f'R24CPNG1 {name} {i+1}/{len(parts)} {hashlib.sha256(data).hexdigest()} {part}' for i, part in enumerate(parts)]


def export(group, image=None):
    if image is None: raise ValueError('One selected image per real CI check-run is required')
    wanted = {image}
    if not wanted <= NAMES: raise ValueError('Unknown native frame')
    found = {}
    root = ROOT / 'app/build/outputs/managed_device_android_test_additional_output'
    for file in root.rglob('r24c-*.png'):
        if file.name not in wanted: continue
        data = file.read_bytes()
        if file.name in found and found[file.name] != data:
            raise ValueError('Ambiguous native capture')
        found[file.name] = data
    messages = []; index = []
    for name, data in sorted(found.items()):
        chunks = transport_chunks(name, data)
        messages.extend(chunks)
        index.append({'name': name, 'bytes': len(data), 'chunks': len(chunks), 'artifact_only': not bool(chunks)})
    if group:
        # GitHub limits annotations per STEP. The earlier per-image grouping lost later images/metadata.
        # Globally paginate, never emit 8 chunks for EACH image in the same step.
        first = (group - 1) * CHUNKS_PER_STEP
        for message in messages[first:first + CHUNKS_PER_STEP]: print('::notice::' + message)
        return
    print('::notice::R24CCAPTUREINDEX1 ' + json.dumps(index, separators=(',', ':')))
    for file in root.rglob('r24c-*.json'):
        if file.name.removesuffix('.json') + '.png' not in wanted: continue
        report = json.loads(file.read_text()); validate_audit(report)
        observed = {key: report[key] for key in ('schema', 'origin', 'api', 'density', 'font_scale', 'required_targets')}
        for key in ('native_image_width', 'native_image_height', 'preview_width', 'preview_height', 'preview_filter', 'capture_api'):
            if key in report: observed[key] = report[key]
        message = 'R24CTARGETS1 ' + file.name + ' ' + json.dumps(observed, separators=(',', ':'))
        if len(message.encode()) > 3900: raise ValueError('Target metadata exceeds actual annotation transport budget')
        print('::notice::' + message)
    if not found: print('::notice::No current native image. No visual verification inferred.')


def collect(run_id, ci_frames=False):
    def api(path):
        return json.loads(subprocess.check_output(['gh', 'api', path]))
    run = api(f'repos/jyvplay/OSB/actions/runs/{run_id}')
    if run['status'] != 'completed' and not ci_frames:
        raise ValueError('Wait for actual completion; do not infer captures')
    jobs = api(f'repos/jyvplay/OSB/actions/runs/{run_id}/jobs?per_page=100')['jobs']
    if ci_frames:
        wanted = {'Actual frame ' + name + ' (not all-page acceptance)' for name in NAMES}
        completed = {job['name'] for job in jobs if job['status'] == 'completed' and job['conclusion'] == 'success'}
        if not wanted <= completed: raise ValueError('All actual frame check-runs must finish successfully before the CI image check')
    chunks = {}; audits = {}; indexes = []
    for job in jobs:
        page = 1
        while True:
            rows = api(job['check_run_url'] + f'/annotations?per_page=100&page={page}')
            for row in rows:
                message = row.get('message', '')
                if message.startswith('R24CCAPTUREINDEX1 '): indexes.extend(json.loads(message.split(' ', 1)[1]))
                if message.startswith('R24CTARGETS1 '):
                    _, name, raw = message.split(' ', 2)
                    if name.removesuffix('.json') + '.png' not in NAMES or name in audits:
                        raise ValueError('Unexpected/duplicate target observation')
                    report = json.loads(raw); validate_audit(report); audits[name] = report
                m = PATTERN.fullmatch(message)
                if m:
                    name, i, n, digest, part = m[1], int(m[2]), int(m[3]), m[4], m[5]
                    if not 1 <= i <= n <= MAX_PARTS or len(part) > 32768:
                        raise ValueError('Invalid capture chunk')
                    target = chunks.setdefault(name, {'n': n, 'sha256': digest, 'parts': {}})
                    if target['n'] != n or target['sha256'] != digest or i in target['parts']:
                        raise ValueError('Duplicate/inconsistent capture chunks')
                    target['parts'][i] = part
            if len(rows) < 100:
                break
            page += 1
    images, incomplete = {}, {}
    for name, value in chunks.items():
        if len(value['parts']) != value['n']:
            incomplete[name] = {'received': len(value['parts']), 'expected': value['n']}
            continue
        data = base64.b64decode(''.join(value['parts'][i] for i in range(1, value['n']+1)), validate=True)
        if len(data) > MAX_PREVIEW_BYTES or hashlib.sha256(data).hexdigest() != value['sha256']:
            raise ValueError('Native PNG hash/budget mismatch')
        w, h = dimensions(data)
        audit = audits.get(name.removesuffix('.png') + '.json')
        if audit and 'preview_width' in audit and (audit['preview_width'], audit['preview_height']) != (w, h):
            raise ValueError('Native image/measurement preview dimension mismatch')
        path = ROOT / 'testing/build/r24c/captures' / run['head_sha'] / name
        path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        images[name] = {'path': str(path), 'width': w, 'height': h, 'sha256': value['sha256']}
    result = {'run': run_id, 'candidate': run['head_sha'], 'whole_conclusion': run['conclusion'], 'images': images, 'actual_target_observations': audits, 'capture_index': indexes, 'incomplete_not_displayed': incomplete,
              'scope': 'Actual finite native frames only. A screenshot from a failed workflow is not a workflow pass.'}
    if ci_frames and (set(images) != NAMES or incomplete or set(audits) != {n.removesuffix('.png') + '.json' for n in NAMES}):
        raise ValueError('Missing required actual native frames/measurements; no complete visual transport inferred')
    result['transport_complete'] = set(images) == NAMES and not incomplete
    print(json.dumps(result, indent=2))
    if not images:
        raise SystemExit('No complete actual native image; do not fabricate a visual inspection')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['export', 'collect', 'audit', 'stage'])
    p.add_argument('--group', type=int, choices=range(7), default=0)
    p.add_argument('--run', type=int)
    p.add_argument('--image', choices=sorted(NAMES))
    p.add_argument('--ci-frames', action='store_true', help='Read already-completed frame jobs while this CI run is still finishing; NEVER implies whole-workflow success')
    p.add_argument('--file', type=Path)
    a = p.parse_args()
    if a.mode == 'stage': stage()
    elif a.mode == 'export': export(a.group, a.image)
    elif a.mode == 'collect':
        if not a.run: p.error('--run is required')
        collect(a.run, a.ci_frames)
    else:
        if not a.file: p.error('--file is required')
        print(json.dumps(validate_audit(json.loads(a.file.read_text())), indent=2))


if __name__ == '__main__': main()
