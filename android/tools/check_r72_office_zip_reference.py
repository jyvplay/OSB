#!/usr/bin/env python3
"""Independently re-measure R72 Office ZIP fixtures and compare actual reader results.

Every expected outcome is derived from the archive bytes (names, decoded lengths,
duplicate counts, data-descriptor flags, CRC), never from the product code under test.
Finite corpus; not Android, provider, Word or Excel acceptance.
"""
import argparse
import collections
import csv
import hashlib
import io
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PART = 1_048_576
TOTAL = 16_777_216
TEXT = 'Office ZIP boundary: α <&>'
SPECS = {
    'normal': ('ACCEPT', '-'),
    'empty-dir': ('ACCEPT', '-'),
    'small-dir': ('ACCEPT', '-'),
    'dir-at-limit': ('ACCEPT', '-'),
    'dir-over': ('REFUSE', 'EXPANDED'),
    'file-at-limit': ('ACCEPT', '-'),
    'file-over': ('REFUSE', 'EXPANDED'),
    'aggregate-below': ('ACCEPT', '-'),
    'aggregate-at-limit': ('ACCEPT', '-'),
    'aggregate-over': ('REFUSE', 'EXPANDED'),
    'aggregate-suffix-over': ('REFUSE', 'EXPANDED'),
    'duplicate-dir': ('REFUSE', 'DUPLICATE'),
    'duplicate-file': ('REFUSE', 'DUPLICATE'),
    'entries-at-limit': ('ACCEPT', '-'),
    'entries-over': ('REFUSE', 'COUNT'),
    'descriptor-dir-over': ('REFUSE', 'EXPANDED'),
    'unsafe-path': ('REFUSE', 'UNSAFE'),
    'macro': ('REFUSE', 'MACRO'),
    'bad-crc': ('REFUSE', 'CORRUPT_OR_OTHER'),
    'input-over': ('REFUSE', 'INPUT'),
}
CASES = {f'{kind}-{name}': (kind, *outcome) for kind in ('docx', 'xlsx') for name, outcome in SPECS.items()}
BASELINE_FAILURES = {f'{kind}-{name}' for kind in ('docx', 'xlsx')
                     for name in ('dir-over', 'aggregate-over', 'aggregate-suffix-over',
                                  'duplicate-dir', 'descriptor-dir-over')}


def require(ok, message):
    if not ok:
        raise ValueError(message)


def read_tsv(path, fields):
    with Path(path).open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream, delimiter='\t')
        require(tuple(reader.fieldnames or ()) == fields, f'Unexpected header: {path}')
        rows = list(reader)
    require(all(all(value is not None for value in row.values()) for row in rows), 'Malformed TSV row')
    return rows


def index_cases(rows):
    by_id = {}
    for row in rows:
        key = row['id']
        require(key in CASES and key not in by_id, 'Unknown or duplicate case')
        kind, status, reason = CASES[key]
        require((row['kind'], row['expected'], row['reason'], row['file']) ==
                (kind, status, reason, key + '.zip'), f'Forged fixture manifest: {key}')
        require(len(row['sha256']) == 64 and all(c in '0123456789abcdef' for c in row['sha256']), 'Bad SHA-256')
        by_id[key] = row
    require(set(by_id) == set(CASES), 'Missing fixture case')
    return by_id


def compare_observations(rows, observations, baseline=False):
    expected = index_cases(rows)
    actual = {}
    for row in observations:
        key = row['id']
        require(key in expected and key not in actual, 'Unknown or duplicate observed case')
        require(row['status'] in ('ACCEPT', 'REFUSE'), 'Unknown observed status')
        actual[key] = row
    require(set(actual) == set(expected), 'Missing observed case')
    mismatches = {key for key in actual if (actual[key]['status'], actual[key]['reason']) !=
                  (expected[key]['expected'], expected[key]['reason'])}
    if baseline:
        require(mismatches == BASELINE_FAILURES, f'Baseline counterexamples changed: {sorted(mismatches)}')
        for key in mismatches:
            require((actual[key]['status'], actual[key]['reason']) == ('ACCEPT', '-'),
                    f'Unexpected baseline failure mode: {key}')
    else:
        require(not mismatches, f'Candidate ZIP cases failed: {sorted(mismatches)}')
    return len(actual)


def office_readers(directory):
    import docx
    import openpyxl
    require(docx.__version__ == '1.2.0' and openpyxl.__version__ == '3.1.5', 'Unexpected reader version')
    require([p.text for p in docx.Document(directory / 'seed.docx').paragraphs] == [TEXT], 'docx seed text')
    formula = openpyxl.load_workbook(directory / 'seed.xlsx', data_only=False).active
    cached = openpyxl.load_workbook(directory / 'seed.xlsx', data_only=True).active
    require(formula.title == 'Boundary' and formula['A1'].value == TEXT and formula['A1'].data_type == 's',
            'XLSX text')
    require(formula['B1'].value == 2 and formula['C1'].value == '=B1*2', 'XLSX formula/type')
    require(cached['C1'].value == 4, 'XLSX cached value')
    return 5


def check_fixtures(directory, rows):
    directory = Path(directory)
    require(directory.is_dir() and not directory.is_symlink(), 'Bad fixture directory')
    indexed = index_cases(rows)
    require({p.name for p in directory.glob('*.zip')} == {r['file'] for r in rows}, 'Missing or extra archive')
    seeds = {}
    for kind in ('docx', 'xlsx'):
        path = directory / ('seed.' + kind)
        require(path.is_file() and not path.is_symlink(), 'Missing or symlink seed')
        with zipfile.ZipFile(path) as archive:
            seeds[kind] = [(i.filename, archive.read(i)) for i in archive.infolist()]
        require(len({n for n, _ in seeds[kind]}) == len(seeds[kind]), 'Seed duplicate name')
    checks = 0
    for key, row in indexed.items():
        path = directory / row['file']
        require(path.is_file() and not path.is_symlink(), f'Missing/symlink fixture: {key}')
        data = path.read_bytes()
        require(hashlib.sha256(data).hexdigest() == row['sha256'], f'Fixture hash changed: {key}')
        kind = row['kind']
        scenario = key[len(kind) + 1:]
        base = seeds[kind]
        if scenario == 'input-over':
            require(len(data) == TOTAL + 1 and data.startswith(b'PK\x03\x04'), 'Wrong raw-input boundary')
            checks += 2
            continue
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            infos = archive.infolist()
            names = [i.filename for i in infos]
            require(names[:len(base)] == [n for n, _ in base], 'Seed part order/name changed')
            for info, (_, content) in zip(infos[:len(base)], base):
                require(archive.read(info) == content, 'Seed part contents changed')
            checks += len(base) + len(infos) + 3
            extras = infos[len(base):]
            if scenario == 'bad-crc':
                require(archive.testzip() == 'bad-crc.bin', 'CRC attack did not corrupt the intended entry')
                continue
            contents = [archive.read(info) for info in infos]
            require(all(len(c) == i.file_size for i, c in zip(infos, contents)), 'ZIP length mismatch')
            total = sum(map(len, contents))
            by_name = {i.filename: (i, content) for i, content in zip(infos, contents)}
            if scenario in ('aggregate-below', 'aggregate-at-limit', 'aggregate-over', 'aggregate-suffix-over'):
                target = TOTAL + {'aggregate-below': -1, 'aggregate-at-limit': 0,
                                  'aggregate-over': 1, 'aggregate-suffix-over': 1}[scenario]
                require(total == target, 'Aggregate boundary total')
                require(all(i.is_dir() and i.file_size <= PART for i in extras), 'Aggregate directories')
                if scenario in ('aggregate-at-limit', 'aggregate-suffix-over'):
                    require(extras[-1].filename == 'after-full/' and
                            len(contents[-1]) == (scenario == 'aggregate-suffix-over'), 'Boundary suffix fixture')
            elif scenario in ('dir-at-limit', 'dir-over', 'descriptor-dir-over'):
                filename = {'dir-at-limit': 'limit/', 'dir-over': 'overflow/',
                            'descriptor-dir-over': 'descriptor/'}[scenario]
                entry, content = by_name[filename]
                require(entry.is_dir() and len(content) == PART + (scenario != 'dir-at-limit'),
                        'Directory boundary fixture')
                if scenario == 'descriptor-dir-over':
                    require(all(i.flag_bits & 8 for i in infos), 'Missing ZIP data descriptors')
            elif scenario in ('file-at-limit', 'file-over'):
                filename = 'limit.bin' if scenario == 'file-at-limit' else 'overflow.bin'
                entry, content = by_name[filename]
                require(not entry.is_dir() and len(content) == PART + (scenario == 'file-over'),
                        'File boundary fixture')
            elif scenario == 'duplicate-dir':
                require(collections.Counter(names)['twice/'] == 2 and all(i.is_dir() for i in extras),
                        'Duplicate directory missing')
            elif scenario == 'duplicate-file':
                require(collections.Counter(names)[base[0][0]] == 2, 'Duplicate file missing')
            elif scenario in ('entries-at-limit', 'entries-over'):
                require(len(infos) == 256 + (scenario == 'entries-over'), 'Entry count fixture')
            elif scenario == 'unsafe-path':
                require(extras[-1].filename == '../escape.bin', 'Traversal fixture')
            elif scenario == 'macro':
                require(extras[-1].filename.lower().endswith('vbaproject.bin'), 'Macro fixture')
            elif scenario == 'small-dir':
                require(extras[-1].is_dir() and contents[-1] == b'directory bytes', 'Small directory payload')
            elif scenario == 'empty-dir':
                require(extras[-1].is_dir() and not contents[-1], 'Empty directory')
            elif scenario == 'normal':
                require(not extras, 'Normal fixture has extra entries')
            else:
                raise ValueError(f'Unrecognized scenario: {scenario}')
    return checks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--baseline', action='store_true')
    args = parser.parse_args()
    rows = read_tsv(args.directory / 'cases.tsv', ('id', 'kind', 'file', 'expected', 'reason', 'sha256'))
    observed = read_tsv(args.directory / 'observed.tsv', ('id', 'status', 'reason'))
    predicates = check_fixtures(args.directory, rows) + office_readers(args.directory)
    count = compare_observations(rows, observed, baseline=args.baseline)
    tag = '10 known baseline counterexamples' if args.baseline else 'no candidate mismatches'
    print(f'R72 independent ZIP/python-docx/openpyxl: {count} actual cases, {predicates} predicates; '
          f'{tag}; not Android or desktop Office')


if __name__ == '__main__':
    main()
