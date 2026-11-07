#!/usr/bin/env python3
"""Independent python-docx plus ZIP/XML inspection of actual R60 DOCX files; not a renderer."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r60writer/artifacts'
WORD = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
REL = '{http://schemas.openxmlformats.org/package/2006/relationships}'
PARTS = {'[Content_Types].xml', '_rels/.rels', 'word/_rels/document.xml.rels',
         'word/styles.xml', 'word/numbering.xml', 'word/document.xml'}


def validate(directory=ARTIFACTS, count=128):
    try:
        from docx import Document
    except ImportError as exc:
        raise RuntimeError('python-docx==1.2.0 is required as an independent host reader') from exc
    directory = Path(directory)
    if not 1 <= count <= 128:
        raise ValueError('Unexpected corpus size')
    predicates = 0

    def require(condition, message):
        nonlocal predicates
        predicates += 1
        if not condition:
            raise ValueError(message)

    names = {f'doc-{i:03}-{mode}.docx' for i in range(count) for mode in ('default', 'numbered')}
    require({p.name for p in directory.glob('doc-*.docx')} == names, 'missing or unexpected corpus document')
    for i in range(count):
        for mode in ('default', 'numbered'):
            path = directory / f'doc-{i:03}-{mode}.docx'
            numbered = mode == 'numbered'
            with zipfile.ZipFile(path) as package:
                members = package.namelist()
                require(len(members) == len(set(members)) and set(members) == PARTS, 'unexpected, duplicated or missing package parts')
                numbering = ET.fromstring(package.read('word/numbering.xml'))
                nums = {n.attrib[WORD + 'numId']: n.find(WORD + 'abstractNumId').attrib[WORD + 'val']
                        for n in numbering.findall(WORD + 'num')}
                abstracts = {n.attrib[WORD + 'abstractNumId']: n for n in numbering.findall(WORD + 'abstractNum')}
                require(nums.get('2') in abstracts, 'numbered paragraph references a missing numbering definition')
                levels = abstracts[nums['2']].findall(WORD + 'lvl')
                zero = next((level for level in levels if level.attrib.get(WORD + 'ilvl') == '0'), None)
                require(zero is not None and zero.find(WORD + 'start').attrib.get(WORD + 'val') == '1', 'numbered start differs')
                require(zero.find(WORD + 'numFmt').attrib.get(WORD + 'val') == 'decimal', 'numbered format differs')
                require(zero.find(WORD + 'lvlText').attrib.get(WORD + 'val') == '%1.', 'numbered marker differs')
                rels = ET.fromstring(package.read('word/_rels/document.xml.rels'))
                require(any(r.attrib.get('Type', '').endswith('/numbering') and r.attrib.get('Target') == 'numbering.xml'
                            for r in rels.findall(REL + 'Relationship')), 'numbering relationship missing')
            doc = Document(path)
            paras = doc.paragraphs
            first = f'item-{i} bold α😀' if numbered else f'1. item-{i} bold α😀'
            second = f'second-{i}\tend' if numbered else f'1. second-{i}\tend'
            third = 'later' if numbered else '1. later'
            expected = [first, second, f'plain-{i}', third, 'bullet', '2. literal', '01. literal', '1. ',
                        '  1. nested', 'heading', '']
            require([p.text for p in paras] == expected, f'{mode} text or boundaries differ at {i}')
            expected_styles = ['List Paragraph' if numbered else 'Normal', 'List Paragraph' if numbered else 'Normal',
                               'Normal', 'List Paragraph' if numbered else 'Normal', 'List Paragraph',
                               'Normal', 'Normal', 'Normal', 'Normal', 'Heading 1', 'Normal']
            require([p.style.name for p in paras] == expected_styles, f'{mode} block styles differ at {i}')
            require([r.text for r in paras[0].runs] == [f'{"" if numbered else "1. "}item-{i} ', 'bold', ' α😀'], 'inline boundaries differ')
            require(paras[0].runs[1].bold is True and paras[0].runs[1].italic is not True, 'bold span lost or changed')
            for index in (0, 1, 3):
                num = paras[index]._p.pPr.numPr if paras[index]._p.pPr is not None else None
                require((num is not None and num.numId.val == 2 and num.ilvl.val == 0) if numbered else num is None,
                        f'{mode} numbered reference differs at {i}:{index}')
            bullet = paras[4]._p.pPr.numPr
            require(bullet is not None and bullet.numId.val == 1 and bullet.ilvl.val == 0, 'bullet changed into numbered item')
            for index in (2, 5, 6, 7, 8, 9, 10):
                props = paras[index]._p.pPr
                require(props is None or props.numPr is None, f'literal/heading unexpectedly numbered at {i}:{index}')
    return predicates


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=ARTIFACTS)
    args = parser.parse_args()
    print(f'R60 independent python-docx/ZIP/XML checks: {validate(args.directory)} predicates across 256 real DOCX files; no Word rendering')


if __name__ == '__main__':
    main()
