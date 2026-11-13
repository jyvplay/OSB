#!/usr/bin/env python3
"""Independent verification for the R82 Writer paragraph-alignment packages.

`DocxPackage` may emit `w:jc` inside `w:pPr` for a heading or paragraph. This verifier opens every
package twice:

1. **raw OPC** — body paragraphs are parsed in document order: `w:jc` must be absent when no alignment
   is declared, must carry exactly the declared CT_Jc token otherwise, must be the last property
   element this writer emits (`w:pStyle`, `w:numPr`, then `w:jc`, per CT_PPr's sequence), and must not
   appear on list paragraphs, quotes, code blocks or table cells.
2. **python-docx 1.2** — an independent implementation whose `Paragraph.alignment` is a genuine second
   opinion on CT_Jc (it maps `both` to `JUSTIFY`). Paragraph order is compared one-to-one with the
   intent table the Kotlin probe wrote.

Host-only. python-docx is a reader, not Word: agreement here is package-structure evidence, never
rendering or pagination.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
INTENT_FIELDS = 4
TOKENS = {'left', 'center', 'right', 'both'}
PYTHON_DOCX = {'left': 'LEFT', 'center': 'CENTER', 'right': 'RIGHT', 'both': 'JUSTIFY'}
PROPERTY_ORDER = ['pStyle', 'keepNext', 'keepLines', 'pageBreakBefore', 'framePr', 'widowControl',
                  'numPr', 'suppressLineNumbers', 'pBdr', 'shd', 'tabs', 'suppressAutoHyphens',
                  'kinsoku', 'wordWrap', 'overflowPunct', 'topLinePunct', 'autoSpaceDE',
                  'autoSpaceDN', 'bidi', 'adjustRightInd', 'snapToGrid', 'spacing', 'ind',
                  'contextualSpacing', 'mirrorIndents', 'suppressOverlap', 'jc']


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path) -> dict[str, list[dict]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[dict]] = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        fields = line.split('\t')
        require(len(fields) == INTENT_FIELDS,
                f'intent line {index} has {len(fields)} fields, expected {INTENT_FIELDS}')
        alignment = fields[3]
        require(alignment == '-' or alignment in TOKENS,
                f'intent line {index} declares unknown alignment {alignment!r}')
        table.setdefault(fields[0], []).append({
            'index': int(fields[1]),
            'kind': fields[2],
            'alignment': None if alignment == '-' else alignment,
        })
    require(table, 'intent table is empty')
    for name, paragraphs in table.items():
        require([entry['index'] for entry in paragraphs] == list(range(len(paragraphs))),
                f'{name}: paragraph indices are not 0..{len(paragraphs) - 1}')
    return table


def check_package(path: Path, intent: list[dict]) -> None:
    with zipfile.ZipFile(path) as archive:
        require('word/document.xml' in archive.namelist(), f'{path.name}: missing word/document.xml')
        root = ElementTree.fromstring(archive.read('word/document.xml').decode('utf-8'))
        body = root.find(f'{W}body')
        require(body is not None, f'{path.name}: document has no body')
        paragraphs = [child for child in body if child.tag == f'{W}p']
        require(len(paragraphs) == len(intent),
                f'{path.name}: body paragraph count {len(paragraphs)} != declared {len(intent)}')

        # A table's cell paragraphs must never carry the block-level alignment this feature owns.
        for table in body.findall(f'{W}tbl'):
            require(not list(table.iter(f'{W}jc')),
                    f'{path.name}: a table cell carries w:jc, which this writer never emits')

        for paragraph, expected in zip(paragraphs, intent):
            properties = paragraph.find(f'{W}pPr')
            jc = None if properties is None else properties.find(f'{W}jc')
            declared = expected['alignment']
            if expected['kind'] not in ('heading', 'paragraph'):
                require(jc is None,
                        f'{path.name}: a {expected["kind"]} paragraph carries w:jc but only headings and '
                        f'paragraphs may declare alignment')
            if declared is None:
                require(jc is None,
                        f'{path.name}: paragraph {expected["index"]} carries w:jc but declares no alignment')
                continue
            require(jc is not None, f'{path.name}: paragraph {expected["index"]} is missing its w:jc')
            require(jc.get(f'{W}val') == declared,
                    f'{path.name}: paragraph {expected["index"]} w:jc {jc.get(f"{W}val")!r} != declared '
                    f'{declared!r}')
            require(properties is not None, f'{path.name}: w:jc without w:pPr')
            names = [child.tag.split('}')[-1] for child in properties]
            unknown = [name for name in names if name not in PROPERTY_ORDER]
            require(not unknown, f'{path.name}: unknown w:pPr children {unknown} at paragraph '
                                 f'{expected["index"]}')
            positions = [PROPERTY_ORDER.index(name) for name in names]
            require(positions == sorted(positions),
                    f'{path.name}: w:pPr children {names} are out of CT_PPr order at paragraph '
                    f'{expected["index"]}')
            require(names[-1] == 'jc',
                    f'{path.name}: w:jc must be the last w:pPr child, found {names}')


def check_with_python_docx(path: Path, intent: list[dict]) -> None:
    import docx  # imported lazily so the raw-OPC checks can run without it

    document = docx.Document(str(path))
    require(len(document.paragraphs) == len(intent),
            f'{path.name}: python-docx sees {len(document.paragraphs)} paragraphs, declared {len(intent)}')
    for paragraph, expected in zip(document.paragraphs, intent):
        actual = paragraph.alignment
        declared = expected['alignment']
        if declared is None:
            require(actual is None,
                    f'{path.name}: python-docx reports {actual} for a paragraph that declares no alignment')
            continue
        require(actual is not None, f'{path.name}: python-docx sees no alignment for paragraph '
                                    f'{expected["index"]}')
        name = str(actual).split('.')[-1].split(' ')[0]
        require(name == PYTHON_DOCX[declared],
                f'{path.name}: python-docx reports {actual} for a declared {declared!r} alignment')


def verify(directory: Path) -> dict:
    intent = intent_table(directory / 'align.tsv')
    paragraphs = 0
    for name, expected in intent.items():
        path = directory / f'{name}.docx'
        require(path.is_file(), f'missing package {path}')
        check_package(path, expected)
        check_with_python_docx(path, expected)
        paragraphs += len(expected)
    extra = {path.stem for path in directory.glob('*.docx')} - set(intent)
    require(not extra, f'unexpected packages without a declared intent: {sorted(extra)}')
    return {'documents': len(intent), 'paragraphs': paragraphs}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('--require-python-docx', action='store_true',
                        help='fail instead of downgrading when python-docx is unavailable')
    args = parser.parse_args(argv)
    try:
        import docx  # noqa: F401
    except Exception as error:
        if args.require_python_docx:
            print(f'R82 WRITER ALIGN CHECK FAILED: python-docx unavailable: {error}', file=sys.stderr)
            return 1
        print(f'python-docx unavailable ({error}); raw OPC checks only.', file=sys.stderr)
    try:
        report = verify(args.artifacts)
    except Fail as error:
        print(f'R82 WRITER ALIGN CHECK FAILED: {error}', file=sys.stderr)
        return 1
    print(
        f"R82 writer alignment: {report['documents']} packages and {report['paragraphs']} body "
        "paragraphs verified with python-docx 1.2 and raw OPC inspection. Not Word rendering or "
        "Android evidence."
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
