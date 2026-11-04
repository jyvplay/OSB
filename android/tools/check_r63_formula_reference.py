#!/usr/bin/env python3
"""Independent finite formula-fill/XLSX comparison; no Excel rendering or recalculation."""
import argparse
import base64
import csv
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r63sheet/artifacts'
FIELDS = ['id', 'formula64', 'range', 'protect', 'status', 'inserted', 'replaced', 'unchanged', 'written']
FORMULAS = (
    '=SUM(A1:B2)+$C$3+D$4+$E5+1E2',
    '=IF(A1>0,"A1 ""B2""",B2)',
    '=COUNT(A1:B2)+ABS(-1E2)+$C3',
    '=A1+$B$2+C$3+$D4',
    '=IFERROR(1/(A1-1),"C3")',
    '=SUM($A1:B$2,$C$3)+2.5e-1',
    '=IF(A1=B1,TRUE,FALSE)',
    '=LEN("A1""E2")+1E2',
)
INITIAL = {'A1': ('n', 1), 'A2': ('n', 2), 'B1': ('n', 3), 'B2': ('n', 4),
           'C3': ('n', 5), 'D4': ('n', 6), 'E5': ('n', 7),
           'O52': ('s', 'existing'), 'Z100': ('s', 'outside')}
NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'


class CorpusCoordinates:
    """Reconstruct java.util.Random(63063), solely to verify the fixed test manifest."""
    def __init__(self):
        self.state = (63063 ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def bits(self, count):
        self.state = (self.state * 0x5DEECE66D + 11) & ((1 << 48) - 1)
        return self.state >> (48 - count)

    def integer(self, bound):
        if bound & (bound - 1) == 0:
            return (bound * self.bits(31)) >> 31
        while True:
            bits = self.bits(31)
            value = bits % bound
            if bits - value + bound - 1 < (1 << 31):
                return value

    def target(self):
        col = chr(ord('M') + self.integer(3))
        row = 50 + self.integer(3)
        reversed_corners = self.bits(1) == 0
        return f'{col}{row}:M50' if reversed_corners else f'M50:{col}{row}'


def check(folder=ARTIFACTS):
    try:
        import openpyxl
        from openpyxl.formula.translate import Translator
    except ImportError as exc:
        raise RuntimeError('openpyxl==3.1.5 is required for the independent reader') from exc
    folder = Path(folder)
    predicates = 0
    def require(value, why):
        nonlocal predicates
        predicates += 1
        if not value:
            raise ValueError(why)
    require(folder.is_dir() and not folder.is_symlink(), 'Missing/symlink artifact root')
    manifest = folder / 'cases.tsv'
    require(manifest.is_file() and not manifest.is_symlink(), 'Missing/symlink manifest')
    with manifest.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream, delimiter='\t')
        require(reader.fieldnames == FIELDS, 'Manifest schema')
        cases = list(reader)
    require(len(cases) == 512 and [x['id'] for x in cases] == [f'{i:03}' for i in range(512)], 'Case IDs')
    expected_files = {'cases.tsv'}
    accepted = refused = 0
    combos = {}
    coordinates = CorpusCoordinates()
    for index, case in enumerate(cases):
        cid = case['id']
        try:
            formula = base64.b64decode(case['formula64'], validate=True).decode('utf-8')
        except (ValueError, UnicodeDecodeError) as exc:
            raise ValueError('Invalid formula encoding ' + cid) from exc
        expected_formula = FORMULAS[index // 64]
        expected_protect = (index % 64) >= 32
        require(formula == expected_formula and case['protect'] == str(expected_protect).lower(), 'Formula/policy corpus ' + cid)
        combos[(formula, expected_protect)] = combos.get((formula, expected_protect), 0) + 1
        require(case['range'] == coordinates.target(), 'Seeded rectangle mismatch ' + cid)
        match = re.fullmatch(r'([M-O])(50|51|52):([M-O])(50|51|52)', case['range'])
        require(match is not None, 'Unexpected rectangle ' + cid)
        a, b, c, d = match.groups()
        require((a, b) == ('M', '50') or (c, d) == ('M', '50'), 'Rectangle must start/end at source')
        col_lo, col_hi = sorted((ord(a), ord(c)))
        row_lo, row_hi = sorted((int(b), int(d)))
        translator = Translator(formula, origin='M50')
        cells = dict(INITIAL)
        cells['M50'] = ('f', formula)
        counts = {'inserted': 0, 'replaced': 0, 'unchanged': 0, 'written': 0}
        conflict = False
        for row in range(row_lo, row_hi + 1):
            for col in range(col_lo, col_hi + 1):
                address = chr(col) + str(row)
                # The source must remain byte-for-byte unchanged, even if another
                # translator chooses to normalize a zero-offset formula.
                new_formula = formula if address == 'M50' else translator.translate_formula(address)
                desired = ('f', new_formula)
                old = cells.get(address)
                if expected_protect and old is not None and old != desired:
                    conflict = True
                if old == desired:
                    counts['unchanged'] += 1
                elif old is None:
                    counts['inserted'] += 1
                    counts['written'] += 1
                    cells[address] = desired
                else:
                    counts['replaced'] += 1
                    counts['written'] += 1
                    cells[address] = desired
        result = folder / f'result-{cid}.xlsx'
        require(case['status'] == ('refused' if conflict else 'accepted'), 'Decision ' + cid)
        if conflict:
            refused += 1
            require(not result.exists() and not result.is_symlink(), 'Refusal produced file ' + cid)
            require(all(case[k] == '' for k in FIELDS[5:]), 'Refusal supplied counts ' + cid)
            continue
        accepted += 1
        for key, value in counts.items():
            require(case[key] == str(value), cid + ' wrong ' + key)
        expected_files.add(result.name)
        require(result.is_file() and not result.is_symlink() and result.stat().st_size <= 1024 * 1024, 'Missing/oversize/symlink workbook')
        with ZipFile(result) as package:
            names = package.namelist()
            require(len(names) == len(set(names)) and 'xl/worksheets/sheet1.xml' in names, 'ZIP entries ' + cid)
            data = package.read('xl/worksheets/sheet1.xml')
            require(len(data) <= 1024 * 1024, 'Worksheet budget')
        xml = ET.fromstring(data)
        elements = xml.findall('.//' + NS + 'c')
        refs = [cell.attrib.get('r') for cell in elements]
        require(len(refs) == len(set(refs)) and set(refs) == set(cells), 'Worksheet coordinates ' + cid)
        for element in elements:
            expected = cells[element.attrib['r']]
            require((element.find(NS + 'f') is not None) == (expected[0] == 'f'), 'Formula identity ' + cid)
        book = openpyxl.load_workbook(result, data_only=False)
        try:
            require(len(book.worksheets) == 1, 'Unexpected extra sheet ' + cid)
            sheet = book.worksheets[0]
            for address, (kind, value) in cells.items():
                cell = sheet[address]
                require(cell.data_type == kind, 'Wrong cell type ' + cid + '/' + address)
                require(cell.value == value, 'Wrong formula/value ' + cid + '/' + address)
        finally:
            book.close()
    require(len(combos) == 16 and set(combos.values()) == {32}, 'Missing formula/protection matrix')
    require(accepted > 0 and refused > 0, 'Missing positive/negative cases')
    require({p.name for p in folder.iterdir()} == expected_files and all(p.is_file() and not p.is_symlink() for p in folder.iterdir()), 'Stale, extra or symlink file')
    return predicates, accepted, refused


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    args = parser.parse_args()
    predicates, accepted, refused = check(args.artifacts)
    print(f'R63 independent openpyxl Translator/XLSX/XML: {predicates} predicates; {accepted} accepted/{refused} refused; no Excel rendering or recalculation')


if __name__ == '__main__':
    main()
