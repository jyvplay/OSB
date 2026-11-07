#!/usr/bin/env python3
"""Independent finite rectangle model plus actual CSV/OOXML/openpyxl inspection, not Excel."""
import argparse
import csv
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r62sheet/artifacts'
FIELDS = ['id', 'selected', 'range', 'operation', 'protect', 'status',
          'inserted', 'replaced', 'cleared', 'unchanged', 'formulas']
INITIAL = {'A1': ('n', 12.5), 'B1': ('s', '12.5'), 'B2': ('f', '=1+2'),
           'C3': ('b', True), 'D4': ('s', '=1+1'), 'E1': ('s', 'outside')}
NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'


def check(folder=ARTIFACTS):
    try:
        import openpyxl
    except ImportError as exc:
        raise RuntimeError('openpyxl==3.1.5 is required for the independent reader') from exc
    folder = Path(folder)
    predicates = 0
    def require(value, why):
        nonlocal predicates
        predicates += 1
        if not value: raise ValueError(why)
    require(folder.is_dir() and not folder.is_symlink(), 'Missing/symlink artifact root')
    manifest = folder / 'cases.csv'
    require(manifest.is_file() and not manifest.is_symlink(), 'Missing/symlink manifest')
    with manifest.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream)
        require(reader.fieldnames == FIELDS, 'Manifest schema')
        cases = list(reader)
    require(len(cases) == 512 and [x['id'] for x in cases] == [f'{i:03}' for i in range(512)], 'Case IDs')
    expected_files = {'cases.csv'}; combos = {}; accepted = refused = 0
    for case in cases:
        cid = case['id']; selected = case['selected']; op = case['operation']
        require(selected in ('A1', 'B1', 'B2', 'C3', 'D4', 'F1'), 'Unexpected source')
        require(op in ('CLEAR_CONTENTS', 'FILL_LITERAL') and case['protect'] in ('true', 'false'), 'Unknown mode')
        protect = case['protect'] == 'true'
        combos[(op, protect)] = combos.get((op, protect), 0) + 1
        match = re.fullmatch(r'([A-E])([1-5]):([A-E])([1-5])', case['range'])
        require(match is not None, 'Unexpected finite rectangle')
        a, b, c, d = match.groups()
        col_lo, col_hi = sorted((ord(a), ord(c))); row_lo, row_hi = sorted((int(b), int(d)))
        source = INITIAL.get(selected)
        conflict = op == 'FILL_LITERAL' and (source is None or source[0] == 'f')
        cells = dict(INITIAL)
        counts = {key: 0 for key in ('inserted', 'replaced', 'cleared', 'unchanged', 'formulas')}
        for row in range(row_lo, row_hi + 1):
            for col in range(col_lo, col_hi + 1):
                address = chr(col) + str(row)
                old = cells.get(address); new = source if op == 'FILL_LITERAL' else None
                if op == 'FILL_LITERAL' and protect and old is not None and old != new:
                    conflict = True
                if old == new:
                    counts['unchanged'] += 1
                elif old is None:
                    counts['inserted'] += 1; cells[address] = new
                elif new is None:
                    counts['cleared'] += 1; cells.pop(address)
                else:
                    counts['replaced'] += 1; cells[address] = new
                if old is not None and old[0] == 'f' and old != new: counts['formulas'] += 1
        result = folder / f'result-{cid}.xlsx'
        require(case['status'] == ('refused' if conflict else 'accepted'), 'Decision ' + cid)
        if conflict:
            refused += 1
            require(not result.exists() and not result.is_symlink(), 'Refusal produced file')
            require(all(case[k] == '' for k in FIELDS[6:]), 'Refusal supplied counts')
            continue
        accepted += 1
        for key, value in counts.items(): require(case[key] == str(value), cid + ' wrong ' + key)
        expected_files.add(result.name)
        require(result.is_file() and not result.is_symlink(), 'Missing/symlink workbook')
        with ZipFile(result) as package:
            names = package.namelist()
            require(len(names) == len(set(names)) and 'xl/worksheets/sheet1.xml' in names, 'ZIP entries')
            data = package.read('xl/worksheets/sheet1.xml')
            require(len(data) <= 1024 * 1024, 'Worksheet budget')
        xml = ET.fromstring(data)
        elements = xml.findall('.//' + NS + 'c')
        refs = [cell.attrib.get('r') for cell in elements]
        require(len(refs) == len(set(refs)) and set(refs) == set(cells), 'Worksheet coordinates ' + cid)
        for element in elements:
            expected = cells[element.attrib['r']]
            require((element.find(NS + 'f') is not None) == (expected[0] == 'f'), 'Formula identity ' + cid)
        workbook = openpyxl.load_workbook(result, read_only=False, data_only=False)
        try:
            require(len(workbook.worksheets) == 1, 'Extra sheet')
            sheet = workbook.worksheets[0]
            for address, (kind, value) in cells.items():
                cell = sheet[address]
                require(cell.data_type == kind, 'Wrong cell type ' + cid + '/' + address)
                require(cell.value == value, 'Wrong cell value ' + cid + '/' + address)
        finally:
            workbook.close()
    require(len(combos) == 4 and set(combos.values()) == {128}, 'Missing operation/protection matrix')
    require(accepted > 0 and refused > 0, 'Missing positive/negative cases')
    require({p.name for p in folder.iterdir()} == expected_files and all(p.is_file() and not p.is_symlink() for p in folder.iterdir()), 'Stale, extra or symlink file')
    return predicates, accepted, refused


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    args = parser.parse_args()
    predicates, accepted, refused = check(args.artifacts)
    print(f'R62 independent rectangle/CSV/XML/openpyxl: {predicates} predicates; {accepted} accepted/{refused} refused; no Excel rendering or recalculation')


if __name__ == '__main__':
    main()
