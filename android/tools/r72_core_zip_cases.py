#!/usr/bin/env python3
"""Independently built XLSX archives for the four core readers' byte budgets.

Every fixture starts from a real openpyxl workbook, so the accepted cases are
genuine packages rather than synthetic stubs. Host-only evidence.
"""
import argparse
import hashlib
from pathlib import Path
import sys
import zipfile

HEADER = ('id', 'reader', 'file', 'expected', 'pinned', 'reason', 'sha256')
TOTAL = 16 * 1024 * 1024          # MultiSheetBook.MAX_ZIP_BYTES
STYLED_EXPANDED = 32 * 1024 * 1024  # XlsxStyledBook.MAX_EXPANDED_BYTES
STYLED_ENTRIES = 2_048


def base_workbook(path):
    from openpyxl import Workbook

    book = Workbook()
    sheet = book.active
    sheet.title = 'Budget'
    sheet['A1'] = 'alpha'
    sheet['B2'] = 42
    sheet['C3'] = '=B2*2'
    book.save(path)
    return path.read_bytes()


def with_entries(base, extras, compression=zipfile.ZIP_DEFLATED):
    """Append raw entries after the real workbook parts, preserving the original bytes."""
    import io

    target = io.BytesIO(base)
    with zipfile.ZipFile(target, 'a', compression) as archive:
        for name, payload in extras:
            info = zipfile.ZipInfo(name)
            info.compress_type = compression
            archive.writestr(info, payload)
    return target.getvalue()


def cases(base):
    """(id, reader, filename, expected, pinned, reason, payload builder)"""
    big_book = TOTAL + 1024
    big_styled = STYLED_EXPANDED + 1024
    duplicate_dirs = [('d/', b''), ('d/', b'')]
    duplicate_files = [('junk/dup.txt', b'x'), ('junk/dup.txt', b'x')]
    return [
        ('book-normal', 'book', 'book-normal.xlsx', 'ACCEPT', 'ACCEPT', 'real workbook', None),
        ('book-dir-small', 'book', 'book-dir-small.xlsx', 'ACCEPT', 'ACCEPT',
         'directory payload inside every budget', [('note/', b'x' * 1024)]),
        ('book-dir-over', 'book', 'book-dir-over.xlsx', 'REFUSE', 'ACCEPT',
         'directory entry hides data above the expanded cap', [('junk/', b'\0' * big_book)]),
        ('book-duplicate-dir', 'book', 'book-duplicate-dir.xlsx', 'REFUSE', 'ACCEPT',
         'two directory entries share one name', duplicate_dirs),
        ('book-duplicate-file', 'book', 'book-duplicate-file.xlsx', 'REFUSE', 'ACCEPT',
         'two file entries share one name', duplicate_files),
        ('book-file-over', 'book', 'book-file-over.xlsx', 'REFUSE', 'REFUSE',
         'file entry above the archive cap', [('junk/file.bin', b'\0' * big_book)]),
        ('book-entries-over', 'book', 'book-entries-over.xlsx', 'REFUSE', 'REFUSE',
         'more entries than either build accepts',
         [(f'junk/{i}.bin', b'') for i in range(200)]),
        ('styled-normal', 'styled', 'styled-normal.xlsx', 'ACCEPT', 'ACCEPT', 'real workbook', None),
        ('styled-dir-small', 'styled', 'styled-dir-small.xlsx', 'ACCEPT', 'ACCEPT',
         'directory payload inside every budget', [('note/', b'x' * 1024)]),
        ('styled-dir-over', 'styled', 'styled-dir-over.xlsx', 'REFUSE', 'ACCEPT',
         'directory entry hides data above the expanded cap', [('junk/', b'\0' * big_styled)]),
        ('styled-duplicate-file', 'styled', 'styled-duplicate-file.xlsx', 'REFUSE', 'ACCEPT',
         'two file entries share one name', duplicate_files),
        ('styled-file-over', 'styled', 'styled-file-over.xlsx', 'REFUSE', 'ACCEPT',
         'file entry above the per-part cap', [('junk/file.bin', b'\0' * (8 * 1024 * 1024 + 1024))]),
        ('styled-entries-over', 'styled', 'styled-entries-over.xlsx', 'REFUSE', 'ACCEPT',
         'more entries than the reader accepts',
         [(f'junk/{i}.bin', b'') for i in range(STYLED_ENTRIES + 1)]),
    ]


def read_tsv(path, header):
    rows = []
    with open(path, encoding='utf-8', newline='') as handle:
        import csv

        reader = csv.reader(handle, delimiter='\t', quoting=csv.QUOTE_NONE)
        actual = tuple(next(reader))
        if actual != tuple(header):
            raise ValueError(f'unexpected header {actual}')
        for line in reader:
            if len(line) != len(header):
                raise ValueError('malformed row')
            rows.append(dict(zip(header, line)))
    return rows


def index(rows):
    seen = {}
    for row in rows:
        key = row['file']
        if key in seen:
            raise ValueError('duplicate fixture ' + key)
        if row['expected'] not in ('ACCEPT', 'REFUSE') or row['pinned'] not in ('ACCEPT', 'REFUSE'):
            raise ValueError('unexpected outcome ' + row['id'])
        seen[key] = row
    return seen


def build(directory):
    directory = Path(directory)
    if (directory / 'cases.tsv').exists() or any(directory.glob('*.xlsx')):
        raise SystemExit('Refusing to overwrite existing fixtures in ' + str(directory))
    directory.mkdir(parents=True, exist_ok=True)
    base = base_workbook(directory / 'seed.xlsx')
    (directory / 'seed.xlsx').unlink()
    (directory / 'base.xlsx').write_bytes(base)
    rows = []
    for case_id, reader, name, expected, pinned, reason, extras in cases(base):
        payload = base if extras is None else with_entries(base, extras)
        (directory / name).write_bytes(payload)
        rows.append(dict(id=case_id, reader=reader, file=name, expected=expected, pinned=pinned,
                         reason=reason, sha256=hashlib.sha256(payload).hexdigest()))
    with open(directory / 'cases.tsv', 'w', encoding='utf-8', newline='') as handle:
        handle.write('\t'.join(HEADER) + '\n')
        for row in rows:
            handle.write('\t'.join(row[column] for column in HEADER) + '\n')
    print(f'R72 core-reader fixtures: {len(rows)} XLSX archives; host-only, not Android')


def observations(path):
    seen = {}
    for line in Path(path).read_text(encoding='utf-8').splitlines():
        if not line.strip():
            continue
        parts = line.split('|')
        if len(parts) < 2 or parts[1] not in ('ACCEPT', 'REFUSE'):
            raise ValueError('malformed observation ' + line)
        if parts[0] in seen:
            raise ValueError('duplicate observation ' + parts[0])
        seen[parts[0]] = parts[1]
    return seen


def compare(rows, observed, key='expected'):
    if len(observed) != len(rows):
        raise ValueError(f'{len(observed)} observations for {len(rows)} fixtures')
    mismatches = []
    for row in rows:
        if row['id'] not in observed:
            raise ValueError('missing observation ' + row['id'])
        if observed[row['id']] != row[key]:
            mismatches.append(row['id'])
    return mismatches


def verify(directory):
    directory = Path(directory)
    rows = read_tsv(directory / 'cases.tsv', HEADER)
    index(rows)
    for row in rows:
        payload = (directory / row['file']).read_bytes()
        if hashlib.sha256(payload).hexdigest() != row['sha256']:
            raise ValueError('fixture changed: ' + row['file'])
    observed = observations(directory / 'candidate.txt')
    mismatches = compare(rows, observed)
    if mismatches:
        raise SystemExit('candidate mismatches: ' + ', '.join(sorted(mismatches)))
    print(f'R72 core-reader candidate: {len(rows)} archives, 0 mismatches; not Android evidence')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('build', 'verify'))
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    if args.mode == 'build':
        build(args.directory)
    else:
        verify(args.directory)


if __name__ == '__main__':
    sys.exit(main())
