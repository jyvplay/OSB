#!/usr/bin/env python3
"""Independent decimal classification and openpyxl/XML inspection of real R59 artifacts."""
import base64
import csv
from decimal import Decimal, InvalidOperation
import io
import math
from pathlib import Path
import re
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r59/artifacts'
NS = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
NUMBERS = {'A1': '0', 'B1': '-7', 'C1': '12.5', 'E2': '0.1', 'F2': '999999999999999', 'B3': '0.000000000000001'}
TEXTS = {'D1': '42', 'E1': '0012', 'F1': '=1+1', 'A2': '+1', 'B2': '1e3', 'C2': '1.0', 'D2': '-0',
         'A3': '9999999999999999', 'C3': ' 1', 'D3': '1,2', 'F3': 'literal'}
EXPECTED_ROWS = [['0', '-7', '12.5', '42', '0012', '=1+1'], ['+1', '1e3', '1.0', '-0', '0.1', '999999999999999'],
                 ['9999999999999999', '0.000000000000001', ' 1', '1,2', '', 'literal']]


def classify(value: str, quoted: bool) -> str:
    if not value:
        return 'EMPTY'
    if quoted or not re.fullmatch(r'-?(?:0|[1-9][0-9]*)(?:\.[0-9]*[1-9])?', value, flags=re.ASCII):
        return 'TEXT'
    try:
        exact = Decimal(value)
        binary = float(value)
        if len(exact.as_tuple().digits) > 15 or (value.startswith('-') and exact.is_zero()):
            return 'TEXT'
        if not math.isfinite(binary) or Decimal(str(binary)) != exact:
            return 'TEXT'
        return 'NUMBER'
    except (ValueError, OverflowError, InvalidOperation):
        return 'TEXT'


def validate(directory: Path = ARTIFACTS) -> int:
    from openpyxl import load_workbook
    checks = 0
    def require(ok, message):
        nonlocal checks
        checks += 1
        if not ok:
            raise AssertionError(message)

    with (directory / 'typed-input.tsv').open(encoding='utf-8', newline='') as stream:
        require(list(csv.reader(stream, delimiter='\t', quotechar='"', strict=True)) == EXPECTED_ROWS, 'TSV content changed')
    rows = (directory / 'classifications.tsv').read_text(encoding='utf-8').splitlines()
    require(len(rows) == 1500, 'missing or extra classification observations')
    for index, line in enumerate(rows):
        fields = line.split('\t')
        require(len(fields) == 3 and fields[1] in ('true', 'false'), f'invalid observation {index}')
        token = base64.b64decode(fields[0], validate=True).decode('utf-8')
        require(fields[2] == classify(token, fields[1] == 'true'), f'classification mismatch at {index}: {token!r}')
    data = (directory / 'typed.xlsx').read_bytes()
    with zipfile.ZipFile(io.BytesIO(data)) as package:
        names = package.namelist()
        require(len(names) == len(set(names)), 'duplicate ZIP entries')
        require(not any('externallink' in n.lower() or 'vbaproject' in n.lower() for n in names), 'unexpected active content')
        root = ET.fromstring(package.read('xl/worksheets/sheet1.xml'))
        found = root.findall('.//s:c', NS)
        cells = {cell.attrib['r']: cell for cell in found}
        require(len(cells) == len(found), 'duplicate cell address')
        for ref, value in NUMBERS.items():
            cell = cells[ref]
            require(cell.attrib.get('t') in (None, 'n') and cell.find('s:f', NS) is None, f'{ref} not numeric')
            require(Decimal(cell.find('s:v', NS).text) == Decimal(value), f'{ref} decimal value changed')
        for ref, value in TEXTS.items():
            cell = cells[ref]
            node = cell.find('s:is/s:t', NS)
            require(cell.attrib.get('t') == 'inlineStr' and cell.find('s:f', NS) is None, f'{ref} is not literal text')
            require(node is not None and node.text == value, f'{ref} text changed')
        require('E3' not in cells, 'empty field was not cleared')
        require(cells['D4'].find('s:f', NS).text == 'A1+1', 'unrelated formula missing')
    sheet = load_workbook(io.BytesIO(data), data_only=False).active
    for ref, value in NUMBERS.items():
        require(sheet[ref].data_type == 'n' and Decimal(str(sheet[ref].value)) == Decimal(value), f'{ref} reader numeric mismatch')
    for ref, value in TEXTS.items():
        require(sheet[ref].data_type == 's' and sheet[ref].value == value, f'{ref} reader text mismatch')
    require(sheet['E3'].value is None and sheet['D4'].value == '=A1+1', 'cleared cell or unrelated formula changed')
    require(load_workbook(io.BytesIO(data), data_only=True).active['D4'].value == 1, 'formula cached value changed (not recalculation)')
    dependent_data = (directory / 'dependent.xlsx').read_bytes()
    with zipfile.ZipFile(io.BytesIO(dependent_data)) as package:
        root = ET.fromstring(package.read('xl/worksheets/sheet1.xml'))
        cells = {cell.attrib['r']: cell for cell in root.findall('.//s:c', NS)}
        require(cells['D4'].find('s:f', NS).text == 'A1+1', 'dependent formula lost')
        require(Decimal(cells['D4'].find('s:v', NS).text) == Decimal('13.5'), 'dependent formula cache incorrect')
    dependent_book = load_workbook(io.BytesIO(dependent_data), data_only=True).active
    require(dependent_book['A1'].value == 12.5 and dependent_book['D4'].value == 13.5,
            'independent reader disagrees with dependent values; not independent recalculation')
    return checks


if __name__ == '__main__':
    print(f'R59 independent Python decimal/CSV/XML/openpyxl: {validate()} predicates passed; no Excel/LibreOffice recalculation')
