#!/usr/bin/env python3
"""Independent DOCX list-instance inspection; does not render in Word/LibreOffice."""
import argparse
import csv
from pathlib import Path
from zipfile import ZipFile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r63writer/artifacts'
FIELDS = ['id', 'numbered', 'strike', 'restart']
W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'


def check(folder=ARTIFACTS):
    try:
        from docx import Document
    except ImportError as exc:
        raise RuntimeError('python-docx==1.2.0 is required for the independent reader') from exc
    folder = Path(folder)
    predicates = 0
    def require(value, why):
        nonlocal predicates
        predicates += 1
        if not value:
            raise ValueError(why)
    require(folder.is_dir() and not folder.is_symlink(), 'Missing/symlink artifact root')
    manifest = folder / 'cases.csv'
    require(manifest.is_file() and not manifest.is_symlink(), 'Missing/symlink manifest')
    with manifest.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream)
        require(reader.fieldnames == FIELDS, 'Manifest schema')
        cases = list(reader)
    require(len(cases) == 192 and [row['id'] for row in cases] == [f'{i:03}' for i in range(192)], 'Case IDs')
    expected_files = {'cases.csv'}
    options = [(False, False, False), (False, True, False), (True, False, False),
               (True, False, True), (True, True, False), (True, True, True)]
    for case_index, case in enumerate(cases):
        numbered, strike, restart = options[case_index // 32]
        require(tuple(case[k] for k in ('numbered', 'strike', 'restart')) ==
                tuple(str(x).lower() for x in (numbered, strike, restart)), 'Option matrix')
        i = case_index % 32
        name = f'doc-{case_index:03}.docx'
        expected_files.add(name)
        path = folder / name
        require(path.is_file() and not path.is_symlink() and path.stat().st_size <= 2 * 1024 * 1024, 'Missing/oversize/symlink DOCX')
        with ZipFile(path) as package:
            names = package.namelist()
            require(len(names) == len(set(names)), 'Duplicate ZIP entries')
            require({'word/document.xml', 'word/numbering.xml'} <= set(names), 'Missing document/numbering part')
            body_bytes = package.read('word/document.xml')
            numbering_bytes = package.read('word/numbering.xml')
            require(len(body_bytes) <= 1024 * 1024 and len(numbering_bytes) <= 1024 * 1024, 'Part budget')
        body = ET.fromstring(body_bytes)
        numbering = ET.fromstring(numbering_bytes)
        nums = numbering.findall(W + 'num')
        num_ids = [int(node.attrib[W + 'numId']) for node in nums]
        expected_ids = list(range(1, 7)) if restart else [1, 2]
        require(num_ids == expected_ids and len(set(num_ids)) == len(num_ids), 'Unexpected numbering instances')
        for node, num_id in zip(nums, num_ids):
            abstract = node.find(W + 'abstractNumId')
            require(abstract is not None and abstract.attrib.get(W + 'val') == ('0' if num_id == 1 else '1'), 'Abstract numbering reference')
            overrides = node.findall(W + 'lvlOverride')
            if num_id >= 3:
                require(len(overrides) == 1 and overrides[0].attrib.get(W + 'ilvl') == '0', 'Missing level-zero override')
                start = overrides[0].find(W + 'startOverride')
                require(start is not None and start.attrib.get(W + 'val') == '1', 'Missing start override')
            else:
                require(not overrides, 'Unexpected legacy override')
        abstracts = numbering.findall(W + 'abstractNum')
        require([node.attrib.get(W + 'abstractNumId') for node in abstracts] == ['0', '1'], 'Abstract definitions')
        level_zero = abstracts[1].find(W + 'lvl')
        require(level_zero is not None and level_zero.attrib.get(W + 'ilvl') == '0', 'Ordered level zero')
        start = level_zero.find(W + 'start')
        fmt = level_zero.find(W + 'numFmt')
        require(start is not None and start.attrib.get(W + 'val') == '1' and fmt is not None and fmt.attrib.get(W + 'val') == 'decimal', 'Ordered format/start')
        paragraphs = body.find(W + 'body').findall(W + 'p')
        require(len(paragraphs) == 13, 'Paragraph count')
        expected_numids = ([2, 2, None, 3, 3, 1, 4, None, 5, None, None, 6, None] if restart else
                           [2, 2, None, 2, 2, 1, 2, None, 2, None, None, 2, None] if numbered else
                           [None, None, None, None, None, 1, None, None, None, None, None, None, None])
        for paragraph, wanted in zip(paragraphs, expected_numids):
            ids = paragraph.findall('./' + W + 'pPr/' + W + 'numPr/' + W + 'numId')
            actual = int(ids[0].attrib[W + 'val']) if len(ids) == 1 else None
            require(len(ids) <= 1 and actual == wanted, 'Paragraph numbering instance')
        document = Document(path)
        expected_text = [f'first-{i} bold', 'second', f'plain-{i}',
                         'third ' + ('strike' if strike else '~~strike~~'), 'fourth', 'bullet',
                         'fifth', '', 'sixth', '2. literal', ' 1. indented', 'seventh', f'last-{i}']
        if not numbered:
            for position in (0, 1, 3, 4, 6, 8, 11):
                expected_text[position] = '1. ' + expected_text[position]
        require([p.text for p in document.paragraphs] == expected_text, 'Paragraph text or prefixes')
        require(any(run.text == 'bold' and run.bold for run in document.paragraphs[0].runs), 'Bold lost')
        require(any(run.text == 'strike' and run.font.strike for run in document.paragraphs[3].runs) == strike, 'Strike option')
    require({p.name for p in folder.iterdir()} == expected_files and all(p.is_file() and not p.is_symlink() for p in folder.iterdir()), 'Stale, extra or symlink file')
    return predicates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    args = parser.parse_args()
    print(f'R63 independent DOCX/XML/python-docx: {check(args.artifacts)} predicates across 192 actual files; no Word rendering')


if __name__ == '__main__':
    main()
