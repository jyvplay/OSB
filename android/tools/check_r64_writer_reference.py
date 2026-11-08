#!/usr/bin/env python3
"""Independently inspect generated DOCX tables with XML and python-docx, not a renderer."""
import csv
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r64writer/artifacts'
FIELDS = ['id', 'numbered', 'strike', 'restart', 'tables', 'index']
W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
MALFORMED = (
    '| A | B |\n| --- | --- |',
    '| A | B |\n| :--- | --- |\n| x | y |',
    '| A | B |\n| --- | --- |\n| x |',
    '| A | B |\n| --- | --- |\n| x | y',
    '| A\\|x | B |\n| --- | --- |\n| a | b |',
    '| wrong |\n| A | B |\n| --- | --- |\n| x | y |',
    ' | A | B |\n | --- | --- |\n | x | y |',
    '| A | B |\n| -- | --- |\n| x | y |',
    '| **bold** |\n| -- |\n| ~~strike~~ |',
)


def check(folder=ARTIFACTS):
    from docx import Document
    folder = Path(folder); predicates = 0
    def require(value, why):
        nonlocal predicates
        predicates += 1
        if not value: raise ValueError(why)
    require(folder.is_dir() and not folder.is_symlink(), 'Missing/symlink corpus')
    manifest = folder / 'cases.csv'
    require(manifest.is_file() and not manifest.is_symlink(), 'Missing/symlink manifest')
    with manifest.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream)
        require(reader.fieldnames == FIELDS, 'Manifest schema')
        cases = list(reader)
    require(len(cases) == 192 and [r['id'] for r in cases] == [f'{i:03}' for i in range(192)], 'Case IDs')
    options = [(False, False, False), (False, True, False), (True, False, False),
               (True, False, True), (True, True, False), (True, True, True)]
    expected_files = {'cases.csv'}
    for number, case in enumerate(cases):
        numbered, strike, restart = options[number // 32]
        tables = (number % 32) >= 16
        index = number % 16
        require(tuple(case[k] for k in ('numbered', 'strike', 'restart', 'tables', 'index')) ==
                (*[str(v).lower() for v in (numbered, strike, restart, tables)], str(index)), 'Option matrix')
        path = folder / f'doc-{number:03}.docx'; expected_files.add(path.name)
        require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 2 * 1024 * 1024, 'Missing/unsafe DOCX')
        with ZipFile(path) as z:
            names = z.namelist()
            require(len(names) == len(set(names)) and 'word/document.xml' in names and 'word/numbering.xml' in names, 'Package parts')
            body_bytes = z.read('word/document.xml'); numbering_bytes = z.read('word/numbering.xml')
            require(len(body_bytes) <= 1024 * 1024 and len(numbering_bytes) <= 1024 * 1024, 'Part budget')
        body = ET.fromstring(body_bytes).find(W + 'body')
        require(body is not None, 'Missing body')
        tags = [child.tag for child in body if child.tag != W + 'sectPr']
        require(tags == ([W+'p', W+'p', W+'tbl', W+'p', W+'p'] if tables else [W+'p'] * 8), 'Body block ordering')
        tables_xml = body.findall(W + 'tbl')
        require(len(tables_xml) == (1 if tables else 0), 'Table count')
        paragraphs = body.findall(W + 'p')
        ids = []
        for paragraph in paragraphs:
            found = paragraph.findall('./' + W + 'pPr/' + W + 'numPr/' + W + 'numId')
            require(len(found) <= 1, 'Ambiguous numbering')
            ids.append(int(found[0].attrib[W + 'val']) if found else None)
        expect_ids = [None] * len(paragraphs)
        if numbered:
            expect_ids[1] = 2
            expect_ids[-1] = 3 if restart else 2
        require(ids == expect_ids, 'List instance relationship')
        number_ids = [n.attrib[W + 'numId'] for n in ET.fromstring(numbering_bytes).findall(W + 'num')]
        require(number_ids == (['1', '2', '3'] if restart else ['1', '2']), 'Unexpected numbering instances')
        doc = Document(path)
        first = ('first ' if numbered else '1. first ') + ('ink' if strike else '~~ink~~')
        last = 'second' if numbered else '1. second'
        wanted_paragraphs = [f'Heading {index}', first]
        if not tables:
            wanted_paragraphs += ['| Name | Value |', '| --- | --- |', f'| α-{index} | <&> |', '|  | 007 |']
        wanted_paragraphs += ['After | literal', last]
        require([p.text for p in doc.paragraphs] == wanted_paragraphs, 'Visible paragraph text')
        require(any(r.text == 'ink' and r.font.strike for r in doc.paragraphs[1].runs) == strike, 'Strike option')
        require(len(doc.tables) == (1 if tables else 0), 'Independent table count')
        if tables:
            expected_rows = [['Name', 'Value'], [f'α-{index}', '<&>'], ['', '007']]
            rows = tables_xml[0].findall(W + 'tr')
            require(len(rows) == 3 and all(len(row.findall(W + 'tc')) == 2 for row in rows), 'Rectangular XML table')
            require([[cell.text for cell in row.cells] for row in doc.tables[0].rows] == expected_rows, 'Independent cell text')
            require(not tables_xml[0].findall('.//' + W + 'tbl'), 'Unexpected nested table')
            require(all(len(cell.findall(W + 'p')) == 1 for row in rows for cell in row.findall(W + 'tc')), 'Cell paragraph structure')
    for number, raw in enumerate(raw for raw in MALFORMED for _ in (False, True)):
        path = folder / f'malformed-{number:02}.docx'; expected_files.add(path.name)
        require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 2 * 1024 * 1024, 'Missing malformed output')
        with ZipFile(path) as z:
            names = z.namelist()
            require(len(names) == len(set(names)) and 'word/document.xml' in names, 'Malformed-output package')
            body_bytes = z.read('word/document.xml')
            require(len(body_bytes) <= 1024 * 1024, 'Malformed-output part budget')
        xml = ET.fromstring(body_bytes).find(W + 'body')
        require(xml is not None and not xml.findall('.//' + W + 'tbl'), 'Rejected block became a table')
        doc = Document(path)
        require(not doc.tables and [p.text for p in doc.paragraphs] == raw.split('\n'), 'Malformed line content lost')
        require(all(not run.bold and not run.italic and not run.font.strike and not run.underline
                    for p in doc.paragraphs for run in p.runs), 'Malformed markers formatted')
    boundary = folder / 'boundary-256.docx'; expected_files.add(boundary.name)
    require(boundary.is_file() and not boundary.is_symlink() and boundary.stat().st_size <= 2 * 1024 * 1024, 'Missing boundary file')
    boundary_doc = Document(boundary)
    require(len(boundary_doc.tables) == 1 and len(boundary_doc.tables[0].rows) == 4, 'Boundary table rows')
    require(all(len(row.cells) == 64 and all(cell.text == 'x' for cell in row.cells)
                for row in boundary_doc.tables[0].rows), 'Boundary table cells')
    require({p.name for p in folder.iterdir()} == expected_files and
            all(p.is_file() and not p.is_symlink() for p in folder.iterdir()), 'Stale/extra/symlink artifacts')
    return predicates


if __name__ == '__main__':
    print(f'R64 independent DOCX/XML/python-docx: {check()} predicates across 192 matrix, 18 malformed and one boundary file; no Word rendering')
