#!/usr/bin/env python3
"""Independently inspect finite R62 DOCX outputs via python-docx and ZIP/XML, not Word."""
import argparse
import csv
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r62writer/artifacts'
FIELDS = ['id', 'numbered', 'strike']
W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
PARTS = {'[Content_Types].xml', '_rels/.rels', 'word/_rels/document.xml.rels',
         'word/styles.xml', 'word/numbering.xml', 'word/document.xml'}


def check(folder=ARTIFACTS):
    try:
        from docx import Document
    except ImportError as exc:
        raise RuntimeError('python-docx==1.2.0 is required for the independent reader') from exc
    folder = Path(folder); predicates = 0
    def require(value, message):
        nonlocal predicates
        predicates += 1
        if not value: raise ValueError(message)
    require(folder.is_dir() and not folder.is_symlink(), 'Missing/symlink artifact directory')
    manifest = folder / 'cases.csv'
    require(manifest.is_file() and not manifest.is_symlink(), 'Missing/symlink manifest')
    with manifest.open(encoding='utf-8', newline='') as stream:
        reader = csv.DictReader(stream); require(reader.fieldnames == FIELDS, 'Manifest schema')
        rows = list(reader)
    require(len(rows) == 256 and [r['id'] for r in rows] == [f'{i:03}' for i in range(256)], 'Case IDs')
    expected_files = {'cases.csv'}; combos = {}
    for index, row in enumerate(rows):
        require(row['numbered'] in ('true', 'false') and row['strike'] in ('true', 'false'), 'Unknown option')
        numbered, strike = row['numbered'] == 'true', row['strike'] == 'true'
        combos[(numbered, strike)] = combos.get((numbered, strike), 0) + 1
        # The case source index is independently specified by the matrix position.
        i = index % 64; path = folder / f'doc-{index:03}.docx'; expected_files.add(path.name)
        require(path.is_file() and not path.is_symlink(), 'Missing/symlink DOCX')
        with ZipFile(path) as package:
            names = package.namelist()
            require(len(names) == len(set(names)) and set(names) == PARTS, 'Wrong ZIP entries')
            raw = package.read('word/document.xml'); require(len(raw) < 1024 * 1024, 'Document XML budget')
        root = ET.fromstring(raw)
        strike_elements = root.findall('.//' + W + 'strike')
        require(len(strike_elements) == (6 if strike else 0), 'Wrong strike XML count')
        doc = Document(path); paragraphs = doc.paragraphs
        expected = [
            ('' if numbered else '1. ') + f'line-{i} ' + ('drop' if strike else '~~drop~~') + ' end',
            'Heading ' + ('gone' if strike else '~~gone~~'),
            'alpha bold + ital + code + ' + ('cross' if strike else '~~cross~~') + '!',
            'bullet ' + ('old' if strike else '~~old~~'), 'left ~~bad ~~ right', '~single~ remains',
            '~~a **nested** b~~' if strike else '~~a nested b~~', '~~unclosed', r'\~~escaped~~',
            '~~~triple~~~', 'quote ' + ('q' if strike else '~~q~~'),
            ('' if numbered else '1. ') + ('🙂' if strike else '~~🙂~~'), ''
        ]
        require([p.text for p in paragraphs] == expected, f'Text or paragraph boundary {index}')
        styles = ['List Paragraph' if numbered else 'Normal', 'Heading 1', 'Normal', 'List Paragraph',
                  'Normal', 'Normal', 'Normal', 'Normal', 'Normal', 'Normal', 'Quote',
                  'List Paragraph' if numbered else 'Normal', 'Normal']
        require([p.style.name for p in paragraphs] == styles, f'Block styles {index}')
        for pindex, literal in ((0, 'drop'), (1, 'gone'), (2, 'cross'), (3, 'old'), (10, 'q'), (11, '🙂')):
            matches = [run for run in paragraphs[pindex].runs if run.text == literal and run.font.strike is True]
            require(bool(matches) == strike, f'Strike value {index}:{pindex}')
        for p in paragraphs:
            require(all(run.font.strike is not True for run in p.runs) if not strike else True, 'Default gained strike')
        require(any(r.text == 'bold' and r.bold is True for r in paragraphs[2].runs), 'Bold changed')
        require(any(r.text == 'ital' and r.italic is True for r in paragraphs[2].runs), 'Italic changed')
        require(any(r.text == 'code' and r.font.name == 'monospace' for r in paragraphs[2].runs), 'Code changed')
        require(any(r.text == 'nested' and r.bold is True for r in paragraphs[6].runs) == (not strike), 'Nested fallback')
        for pindex in (0, 11):
            props = paragraphs[pindex]._p.pPr
            num = props.numPr if props is not None else None
            require((num is not None and num.numId.val == 2 and num.ilvl.val == 0) if numbered else num is None, 'Numbering changed')
    require(len(combos) == 4 and set(combos.values()) == {64}, 'Missing mode combination')
    require({p.name for p in folder.iterdir()} == expected_files and all(p.is_file() and not p.is_symlink() for p in folder.iterdir()), 'Extra or symlink artifacts')
    return predicates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=ARTIFACTS)
    args = parser.parse_args()
    print(f'R62 independent python-docx/ZIP/XML: {check(args.artifacts)} predicates across 256 real files; no Word rendering')


if __name__ == '__main__': main()
