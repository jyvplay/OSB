#!/usr/bin/env python3
"""Build and independently verify archives around the production Ooxml import budgets.

`build` writes disposable fixtures into an existing empty directory; `verify`
re-measures the archives themselves and compares the extractor's actual results.
Neither mode is Android, provider, or Office-rendering evidence.
"""
import argparse
import collections
import csv
import hashlib
import io
from pathlib import Path
import sys
import warnings
import zipfile

PART = 32 * 1024 * 1024
TOTAL = 64 * 1024 * 1024
XML = 2 * 1024 * 1024
ENTRIES = 5_000
BASE = {
    'normal': ('ACCEPT', '-'),
    'entries-at-limit': ('ACCEPT', '-'),
    'entries-over': ('REFUSE', 'COUNT'),
    'duplicate-dir': ('REFUSE', 'DUPLICATE'),
    'traversal': ('REFUSE', 'UNSAFE'),
    'absolute': ('REFUSE', 'UNSAFE'),
    'backslash': ('REFUSE', 'UNSAFE'),
    'dot-segment': ('REFUSE', 'UNSAFE'),
    'empty-segment': ('REFUSE', 'UNSAFE'),
    'control-char': ('REFUSE', 'UNSAFE'),
    'dir-at-limit': ('ACCEPT', '-'),
    'dir-over': ('REFUSE', 'EXPANDED'),
    'total-at-limit': ('ACCEPT', '-'),
    'total-over': ('REFUSE', 'EXPANDED'),
    'xml-over': ('REFUSE', 'EXPANDED'),
}
KINDS = {'docx': dict(BASE), 'xlsx': dict(BASE)}
HEADER = ('id', 'kind', 'file', 'expected', 'reason', 'sha256')
UNSAFE = {'traversal': '../escape.txt', 'absolute': '/absolute.txt', 'backslash': 'dir\\file.txt',
          'dot-segment': 'dir/./file.txt', 'empty-segment': 'dir//file.txt', 'control-char': 'bad\x01name'}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def write_zip(parts):
    target = io.BytesIO()
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore', message='Duplicate name:.*', category=UserWarning)
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as out:
            for name, data in parts:
                out.writestr(name, data)
    return target.getvalue()


def seed_parts(kind):
    doc = ('word/document.xml', b'<?xml version="1.0" encoding="UTF-8"?><w:document '
           b'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r>'
           b'<w:t>Import boundary \xce\xb1 &lt;&amp;&gt;</w:t></w:r></w:p></w:body></w:document>')
    xlsx = ('xl/worksheets/sheet1.xml', b'<?xml version="1.0" encoding="UTF-8"?><worksheet '
            b'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1">'
            b'<c r="A1"><v>42</v></c></row></sheetData></worksheet>')
    return [doc if kind == 'docx' else xlsx, ('[Content_Types].xml', b'<Types/>')]


def selected_part(kind):
    return 'word/document.xml' if kind == 'docx' else 'xl/worksheets/sheet1.xml'


def scenario_parts(kind, scenario, base):
    if scenario == 'normal':
        return []
    if scenario in ('entries-at-limit', 'entries-over'):
        count = ENTRIES + (scenario == 'entries-over') - len(base)
        return [(f'd{i:04d}/', b'') for i in range(count)]
    if scenario == 'duplicate-dir':
        return [('twice/', b''), ('twice/', b'')]
    if scenario in UNSAFE:
        return [(UNSAFE[scenario], b'x')]
    if scenario in ('dir-at-limit', 'dir-over'):
        return [('payload/', b'D' * (PART + (scenario == 'dir-over')))]
    if scenario in ('total-at-limit', 'total-over'):
        remaining = TOTAL - len(base[0][1]) - len(base[1][1]) + (scenario == 'total-over')
        half = remaining // 2
        return [('half-a/', b'T' * half), ('half-b/', b'T' * (remaining - half))]
    if scenario == 'xml-over':
        return [(selected_part(kind), base[0][1] + b' ' * (XML + 1 - len(base[0][1])))]
    raise ValueError('Unknown scenario ' + scenario)


def build(directory):
    directory = Path(directory)
    require(directory.is_dir() and not directory.is_symlink(), 'Fixture directory must exist and not be a symlink')
    require(not any(directory.glob('*.zip')) and not (directory / 'cases.tsv').exists(), 'Refusing to overwrite')
    rows = []
    for kind, scenarios in KINDS.items():
        base = seed_parts(kind)
        for scenario, (expected, reason) in scenarios.items():
            case_id = f'{kind}-{scenario}'
            extra = scenario_parts(kind, scenario, base)
            parts = [extra[0], base[1]] if scenario == 'xml-over' else base + extra
            payload = write_zip(parts)
            (directory / f'{case_id}.zip').write_bytes(payload)
            rows.append((case_id, kind, f'{case_id}.zip', expected, reason, hashlib.sha256(payload).hexdigest()))
    with (directory / 'cases.tsv').open('x', encoding='utf-8', newline='') as output:
        writer = csv.writer(output, delimiter='\t', lineterminator='\n')
        writer.writerow(HEADER)
        writer.writerows(rows)
    print(f'R72 Ooxml fixtures: {len(rows)} actual archives; not Android or provider evidence')


def read_tsv(path, fields):
    with Path(path).open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream, delimiter='\t')
        require(tuple(reader.fieldnames or ()) == fields, f'Bad header: {path}')
        rows = list(reader)
    require(all(all(value is not None for value in row.values()) for row in rows), f'Malformed row: {path}')
    return rows


def index(rows):
    expected = {}
    for row in rows:
        key = row['id']
        require('-' in key, 'Malformed key')
        kind, scenario = key.split('-', 1)
        require(kind in KINDS and scenario in KINDS[kind] and key not in expected, 'Unknown or duplicate case')
        status, reason = KINDS[kind][scenario]
        require((row['kind'], row['file'], row['expected'], row['reason']) == (kind, key + '.zip', status, reason),
                f'Forged manifest row: {key}')
        require(len(row['sha256']) == 64 and all(c in '0123456789abcdef' for c in row['sha256']), 'Bad digest')
        expected[key] = row
    require(len(expected) == sum(len(v) for v in KINDS.values()), 'Missing case')
    return expected


def inspect(directory, rows):
    directory = Path(directory)
    expected = index(rows)
    require({p.name for p in directory.glob('*.zip')} == {r['file'] for r in rows}, 'Missing or extra archive')
    checks = 0
    for key, row in expected.items():
        path = directory / row['file']
        require(path.is_file() and not path.is_symlink(), f'Missing fixture: {key}')
        data = path.read_bytes()
        require(hashlib.sha256(data).hexdigest() == row['sha256'], f'Fixture hash changed: {key}')
        kind, scenario = key.split('-', 1)
        base = seed_parts(kind)
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            infos = archive.infolist()
            names = [i.filename for i in infos]
            contents = [archive.read(i) for i in infos]
            total = sum(map(len, contents))
            if scenario not in ('xml-over',):
                require(len(infos) >= len(base), 'Seed entries missing')
            if scenario not in ('xml-over',) and scenario not in ('entries-at-limit', 'entries-over'):
                require(names[:len(base)] == [n for n, _ in base] and contents[:len(base)] == [c for _, c in base],
                        'Seed entries changed')
            if scenario == 'entries-at-limit':
                require(len(infos) == ENTRIES and all(i.is_dir() for i in infos[len(base):]),
                        'Entry-count boundary')
            elif scenario == 'entries-over':
                require(len(infos) == ENTRIES + 1, 'Entry-count overflow')
            elif scenario == 'duplicate-dir':
                require(collections.Counter(names)['twice/'] == 2 and infos[-1].is_dir(), 'Duplicate directory')
            elif scenario in UNSAFE:
                require(names[-1] == UNSAFE[scenario], f'Unsafe-name fixture changed: {scenario}')
            elif scenario in ('dir-at-limit', 'dir-over'):
                require(infos[-1].is_dir() and len(contents[-1]) == PART + (scenario == 'dir-over'),
                        'Entry boundary')
                require(total <= TOTAL, 'Entry fixture already exceeds total')
            elif scenario in ('total-at-limit', 'total-over'):
                require(total == TOTAL + (scenario == 'total-over'), 'Total boundary')
                require(all(i.is_dir() and len(c) <= PART for i, c in zip(infos[-2:], contents[-2:])),
                        'Total fixture shape')
            elif scenario == 'xml-over':
                part = selected_part(kind)
                require(names.count(part) == 1, 'Selected part missing')
                require(len(contents[names.index(part)]) == XML + 1, 'Selected-part boundary')
            elif scenario == 'normal':
                require(len(infos) == len(base), 'Normal fixture changed')
        checks += len(infos) + 4
    return checks


def compare(rows, observations):
    expected = index(rows)
    actual = {}
    for row in observations:
        require(row['id'] in expected and row['id'] not in actual, 'Unknown or duplicate observation')
        require(row['status'] in ('ACCEPT', 'REFUSE'), 'Unknown status')
        actual[row['id']] = row
    require(set(actual) == set(expected), 'Missing observation')
    mismatches = {k: (expected[k]['expected'], expected[k]['reason'], actual[k]['status'], actual[k]['reason'])
                  for k in expected if (actual[k]['status'], actual[k]['reason']) !=
                  (expected[k]['expected'], expected[k]['reason'])}
    require(not mismatches, f'Import extractor mismatches: {sorted(mismatches.items())[:4]}')
    return len(actual)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('build', 'verify'))
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    if args.mode == 'build':
        build(args.directory)
        return 0
    rows = read_tsv(args.directory / 'cases.tsv', HEADER)
    observed = read_tsv(args.directory / 'observed.tsv', ('id', 'status', 'reason'))
    checks = inspect(args.directory, rows)
    count = compare(rows, observed)
    print(f'R72 Ooxml independent ZIP/digest: {count} actual extractor cases, {checks} predicates; '
          f'no mismatch; not Android, provider or full Office import')
    return 0


if __name__ == '__main__':
    sys.exit(main())
