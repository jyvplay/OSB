#!/usr/bin/env python3
"""Independent CSV/openpyxl/XML inspection of actual R60 transposed outputs, not Excel recalculation."""
import argparse
import csv
import io
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r60sheet/artifacts'
NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
PARTS = {'[Content_Types].xml', '_rels/.rels', 'xl/workbook.xml',
         'xl/_rels/workbook.xml.rels', 'xl/worksheets/sheet1.xml'}


def validate(directory=ARTIFACTS, count=64):
    try:
        import openpyxl
    except ImportError as exc:
        raise RuntimeError('openpyxl==3.1.5 is required for the independent host reader') from exc
    directory = Path(directory)
    if not 1 <= count <= 64:
        raise ValueError('Invalid corpus size')
    predicates = 0

    def require(condition, message):
        nonlocal predicates
        predicates += 1
        if not condition:
            raise ValueError(message)

    names = {name for i in range(count) for name in (f'input-{i:03}.tsv', f'transposed-{i:03}.xlsx')}
    require({p.name for p in directory.iterdir()} == names, 'missing or unexpected corpus files')
    for i in range(count):
        raw = (directory / f'input-{i:03}.tsv').read_bytes().decode('utf-8')
        expected_raw = f'12.5\t"42"\t=1+1\n0012\t"line\r\n{i} α🙂"\t'
        require(raw == expected_raw, f'fixture changed at {i}')
        fields = list(csv.reader(io.StringIO(raw, newline=''), delimiter='\t', quotechar='"', strict=True))
        require(fields == [['12.5', '42', '=1+1'], ['0012', f'line\r\n{i} α🙂', '']], 'CSV producer/reader disagree')
        path = directory / f'transposed-{i:03}.xlsx'
        with zipfile.ZipFile(path) as z:
            members = z.namelist()
            require(len(members) == len(set(members)) and set(members) == PARTS, 'unexpected or duplicate XLSX parts')
            root = ET.fromstring(z.read('xl/worksheets/sheet1.xml'))
            cells = {cell.attrib['r']: cell for cell in root.findall(f'.//{NS}c')}
            require(set(cells) == {'B2', 'C2', 'B3', 'C3', 'B4'}, 'transposed coordinates or blank clearing changed')
            require(not root.findall(f'.//{NS}f'), 'formula injected into pasted text')
            require(cells['B2'].attrib.get('t') is None and cells['B2'].find(NS + 'v').text == '12.5', 'numeric XML changed')
            for coord, expected in (('C2', '0012'), ('B3', '42'), ('C3', f'line\r\n{i} α🙂'), ('B4', '=1+1')):
                cell = cells[coord]
                require(cell.attrib.get('t') == 'inlineStr' and cell.find(f'{NS}is/{NS}t').text == expected,
                        f'XML literal/type mismatch: {coord}')
        book = openpyxl.load_workbook(path, data_only=False)
        require(len(book.sheetnames) == 1, 'unexpected additional sheet')
        ws = book.active
        for coord, value, kind in (('B2', 12.5, 'n'), ('C2', '0012', 's'), ('B3', '42', 's'),
                                   ('C3', f'line\r\n{i} α🙂', 's'), ('B4', '=1+1', 's')):
            require(ws[coord].value == value and ws[coord].data_type == kind, f'openpyxl value/type mismatch: {coord}')
        require(ws['C4'].value is None, 'blank transposed field was not blank')
    return predicates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=ARTIFACTS)
    args = parser.parse_args()
    print(f'R60 independent CSV/ZIP/XML/openpyxl checks: {validate(args.directory)} predicates across 64 actual XLSX files; no Excel recalculation')


if __name__ == '__main__':
    main()
