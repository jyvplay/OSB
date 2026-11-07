#!/usr/bin/env python3
"""Independent openpyxl/XLSX checks for actual range-copy outputs, not Excel evaluation."""
import csv
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r64sheet/artifacts'
FIELDS = ['id', 'formula', 'destination', 'protect', 'skip', 'reversed', 'status',
          'inserted', 'replaced', 'cleared', 'unchanged', 'skipped', 'removed', 'written']
FORMULAS = (
    '=A1+$B$2+C$3+$D4', '=SUM(A1:B2)+1E2', '=IF(A1>0,"A1",B2)',
    '=COUNT($A1:B$2)+$C$3', '=IFERROR(1/(A1-1),"C3")', '=LEN("A1""B2")+1E2',
    '=A1+$B$2+C$3+$D4+2.5e-1', '=IF(A1=B1,TRUE,FALSE)',
)
DESTINATIONS = ('M50', 'N50', 'M51', 'N51', 'O52', 'P50', 'L49', 'Y100')
NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'


def check(folder=ARTIFACTS):
    import openpyxl
    from openpyxl.formula.tokenizer import Tokenizer
    from openpyxl.formula.translate import Translator, TranslatorError
    from openpyxl.utils.cell import coordinate_from_string, column_index_from_string, range_boundaries

    folder = Path(folder); predicates = 0
    def require(condition, why):
        nonlocal predicates
        predicates += 1
        if not condition: raise ValueError(why)
    require(folder.is_dir() and not folder.is_symlink(), 'Missing/symlink corpus')
    manifest = folder / 'cases.tsv'
    require(manifest.is_file() and not manifest.is_symlink(), 'Missing/symlink manifest')
    with manifest.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream, delimiter='\t')
        require(reader.fieldnames == FIELDS, 'Manifest schema')
        cases = list(reader)
    require(len(cases) == 512 and [row['id'] for row in cases] == [f'{i:03}' for i in range(512)], 'Case IDs')
    expected_files = {'cases.tsv'}; accepted = refused = 0
    matrix = [(fi, dest, protect, skip, reversed_) for fi in range(8) for dest in DESTINATIONS
              for protect in (False, True) for skip in (False, True) for reversed_ in (False, True)]
    for row, params in zip(cases, matrix):
        fi, dest, protect, skip, reversed_ = params; cid = row['id']
        require((row['formula'], row['destination'], row['protect'], row['skip'], row['reversed']) ==
                (str(fi), dest, str(protect).lower(), str(skip).lower(), str(reversed_).lower()), 'Corpus inputs ' + cid)
        original = {'A1': ('n', 1), 'B2': ('n', 2), 'C3': ('n', 3), 'D4': ('n', 4),
                    'M50': ('f', FORMULAS[fi]), 'N50': ('s', 'alpha, "β"'), 'M51': ('b', True),
                    'O52': ('s', 'occupied'), 'Z100': ('s', 'outside')}
        current = dict(original)
        col_letters, target_row = coordinate_from_string(dest)
        target_col = column_index_from_string(col_letters)
        counts = dict(inserted=0, replaced=0, cleared=0, unchanged=0, skipped=0, removed=0, written=0)
        bad = target_col + 1 > 26 or target_row + 1 > 100
        if not bad:
            for dy in range(2):
                for dx in range(2):
                    source = f'{chr(ord("M") + dx)}{50 + dy}'
                    target = f'{chr(ord("A") + target_col - 1 + dx)}{target_row + dy}'
                    new = original.get(source)
                    if new is None and skip:
                        counts['skipped'] += 1
                        continue
                    if new is not None and new[0] == 'f':
                        try:
                            translated = Translator(new[1], origin=source).translate_formula(target)
                            # Restrict the oracle to this product's bounded single-sheet references.
                            for token in Tokenizer(translated).items:
                                if token.type == 'OPERAND' and token.subtype == 'RANGE':
                                    lo_c, lo_r, hi_c, hi_r = range_boundaries(token.value.replace('$', ''))
                                    if not (1 <= lo_c <= 26 and 1 <= hi_c <= 26 and 1 <= lo_r <= 100 and 1 <= hi_r <= 100):
                                        bad = True
                            new = ('f', translated)
                        except (TranslatorError, ValueError):
                            bad = True
                    old = original.get(target)
                    if protect and old is not None and old != new:
                        bad = True
                    if old == new: counts['unchanged'] += 1
                    elif old is None: counts['inserted'] += 1; current[target] = new
                    elif new is None: counts['cleared'] += 1; current.pop(target)
                    else: counts['replaced'] += 1; current[target] = new
                    if old and old[0] == 'f' and old != new: counts['removed'] += 1
                    if new and new[0] == 'f' and old != new: counts['written'] += 1
        path = folder / f'result-{cid}.xlsx'
        if bad:
            refused += 1
            require(row['status'] == 'refused' and all(not row[key] for key in counts), 'Expected refusal ' + cid)
            require(not path.exists(), 'Refusal must not emit a workbook ' + cid)
            continue
        accepted += 1
        require(row['status'] == 'accepted', 'Expected accepted copy ' + cid)
        require(all(row[key] == str(value) for key, value in counts.items()), 'Change counts ' + cid)
        require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 2 * 1024 * 1024, 'Missing or unsafe XLSX ' + cid)
        expected_files.add(path.name)
        book = openpyxl.load_workbook(path, data_only=False, read_only=False)
        require(book.sheetnames == ['Sheet1'], 'Sheet identity ' + cid)
        sheet = book['Sheet1']
        actual = {cell.coordinate: (cell.data_type, cell.value) for cell in sheet._cells.values()}
        require(actual == current, 'Typed values/formulas differ ' + cid)
        with ZipFile(path) as archive:
            names = archive.namelist()
            require(len(names) == len(set(names)) and 'xl/worksheets/sheet1.xml' in names, 'XLSX parts ' + cid)
            body = ET.fromstring(archive.read('xl/worksheets/sheet1.xml'))
            formulas = {cell.attrib['r']: cell.find(NS + 'f').text for cell in body.iter(NS + 'c') if cell.find(NS + 'f') is not None}
            require(formulas == {ref: value[1][1:] for ref, value in current.items() if value[0] == 'f'}, 'Formula XML ' + cid)
    require({p.name for p in folder.iterdir()} == expected_files, 'Unexpected or stale corpus artifacts')
    require(accepted > 0 and refused > 0, 'Both positive and negative cases required')
    print(f'R64 independent openpyxl/XLSX/XML: {predicates} predicates; {accepted} accepted/{refused} refused; no Excel evaluation')
    return predicates


if __name__ == '__main__': check()
