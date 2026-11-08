#!/usr/bin/env python3
"""Independent csv/openpyxl/XML oracle for the finite R61 policy corpus, not Excel."""
import argparse
import csv
import io
import re
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r61sheet/artifacts'
NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
FIELDS = ['id', 'mode', 'transpose', 'skip', 'protect', 'status', 'inserted', 'replaced',
          'cleared', 'unchanged', 'skipped', 'numeric', 'text', 'empty']
INITIAL = {'A1': ('s', 'KEEP'), 'B1': ('n', 12.5), 'B2': ('f', '=1+2'),
           'C3': ('s', 'touch'), 'E1': ('s', 'outside')}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check(folder=ARTIFACTS):
    try:
        import openpyxl
    except ImportError as exc:
        raise RuntimeError('openpyxl==3.1.5 is required for the independent host reader') from exc
    folder = Path(folder)
    require(folder.is_dir() and not folder.is_symlink(), 'Missing/symlink artifact root')
    manifest = folder / 'cases.csv'
    require(manifest.is_file() and not manifest.is_symlink(), 'Missing/symlink manifest')
    with manifest.open(encoding='utf-8', newline='') as f:
        reader = csv.DictReader(f)
        require(reader.fieldnames == FIELDS, 'Wrong manifest schema')
        cases = list(reader)
    require(len(cases) == 512 and [r['id'] for r in cases] == [f'{i:03d}' for i in range(512)], 'Missing/duplicate/extra cases')
    combos = {}
    expected_files = {'cases.csv'}
    predicates = 0
    accepted = refused = 0
    for row in cases:
        case = row['id']
        require(row['mode'] in ('TEXT_ONLY', 'DECIMALS'), 'Unknown mode')
        require(all(row[k] in ('true', 'false') for k in ('transpose', 'skip', 'protect')), 'Unknown boolean')
        transpose, skip, protect = (row[k] == 'true' for k in ('transpose', 'skip', 'protect'))
        combo = row['mode'], transpose, skip, protect
        combos[combo] = combos.get(combo, 0) + 1
        filename = f'input-{case}.tsv'
        expected_files.add(filename)
        path = folder / filename
        require(path.is_file() and not path.is_symlink(), 'Missing/symlink input ' + case)
        raw = path.read_text(encoding='utf-8')
        # These independently generated finite cases intentionally contain no embedded
        # tabs/newlines in a quoted field, so this separate lexical view recovers quote flags.
        lexical = [r.split('\t') for r in raw.split('\n')]
        require(len(lexical) == 2 and all(len(r) == 2 for r in lexical), 'Unexpected input shape')
        parsed = list(csv.reader(io.StringIO(raw, newline=''), delimiter='\t', quotechar='"', strict=True))
        require(len(parsed) == 2 and all(len(r) == 2 for r in parsed), 'CSV disagreement')
        cells = dict(INITIAL)
        counts = dict(inserted=0, replaced=0, cleared=0, unchanged=0, skipped=0, numeric=0, text=0, empty=0)
        conflict = False
        for y in range(2):
            for x in range(2):
                value = parsed[y][x]
                token = lexical[y][x]
                quoted = token.startswith('"')
                require(token in ('', '""', 'KEEP', '12.5', 'NEW', '=1+2', '"12.5"', ' '), 'Unexpected token')
                require(value == (token[1:-1] if quoted else token), 'CSV/token disagreement')
                dest_row, dest_col = (x, y) if transpose else (y, x)
                address = chr(ord('A') + dest_col) + str(dest_row + 1)
                old = cells.get(address)
                if not value:
                    counts['empty'] += 1
                    if skip:
                        counts['skipped'] += 1
                        continue
                    next_value = None
                elif row['mode'] == 'DECIMALS' and not quoted and value == '12.5':
                    counts['numeric'] += 1
                    next_value = ('n', 12.5)
                else:
                    counts['text'] += 1
                    next_value = ('s', value)
                if protect and old is not None and old != next_value:
                    conflict = True
                if old == next_value:
                    counts['unchanged'] += 1
                elif old is None:
                    counts['inserted'] += 1
                    cells[address] = next_value
                elif next_value is None:
                    counts['cleared'] += 1
                    del cells[address]
                else:
                    counts['replaced'] += 1
                    cells[address] = next_value
        expected_status = 'refused' if conflict else 'accepted'
        require(row['status'] == expected_status, 'Wrong accept/refuse decision ' + case)
        predicates += 1
        result = folder / f'result-{case}.xlsx'
        if conflict:
            refused += 1
            require(not result.exists() and not result.is_symlink(), 'Refused case produced workbook ' + case)
            require(all(row[k] == '' for k in FIELDS[6:]), 'Refused case supplied counts')
            predicates += 2
            continue
        accepted += 1
        for key, value in counts.items():
            require(row[key] == str(value), f'{case} wrong count {key}')
            predicates += 1
        expected_files.add(result.name)
        require(result.is_file() and not result.is_symlink(), 'Missing/symlink workbook ' + case)
        with ZipFile(result) as z:
            names = z.namelist()
            require(len(names) == len(set(names)) and 'xl/worksheets/sheet1.xml' in names, 'Invalid workbook members')
            data = z.read('xl/worksheets/sheet1.xml')
            require(len(data) <= 1024 * 1024, 'Worksheet budget exceeded')
        xml = ET.fromstring(data)
        xml_cells = xml.findall('.//' + NS + 'c')
        refs = [c.attrib.get('r') for c in xml_cells]
        require(len(refs) == len(set(refs)) and set(refs) == set(cells), 'Wrong/duplicate worksheet cells ' + case)
        for c in xml_cells:
            expected = cells[c.attrib['r']]
            require((c.find(NS + 'f') is not None) == (expected[0] == 'f'), 'Formula/text mismatch ' + case)
            predicates += 1
        workbook = openpyxl.load_workbook(result, read_only=False, data_only=False)
        try:
            require(len(workbook.worksheets) == 1, 'Unexpected worksheets')
            sheet = workbook.worksheets[0]
            for address, (kind, value) in cells.items():
                cell = sheet[address]
                require(cell.data_type == kind and cell.value == value, f'{case} wrong {address} type/value')
                predicates += 2
        finally:
            workbook.close()
    require(len(combos) == 16 and set(combos.values()) == {32}, 'Missing option combination')
    require(accepted > 0 and refused > 0, 'Missing positive or negative outcome')
    actual = {p.name for p in folder.iterdir()}
    require(actual == expected_files and all(p.is_file() and not p.is_symlink() for p in folder.iterdir()), 'Stale/extra/symlink artifact')
    return predicates, accepted, refused


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    args = parser.parse_args()
    checks, accepted, refused = check(args.artifacts)
    print(f'R61 independent CSV/ZIP/XML/openpyxl: {checks} predicates, {accepted} accepted/{refused} refused; no Office rendering or recalculation')


if __name__ == '__main__':
    main()
