#!/usr/bin/env python3
"""Independent verification for the R83 Writer paragraph-spacing packages.

`DocxPackage` may emit `w:spacing` (`w:before` / `w:after`) inside `w:pPr` for a heading or a
paragraph. This verifier opens every package twice:

1. **raw OPC** — body paragraphs are parsed in document order: `w:spacing` must be absent when no
   spacing is declared, must carry exactly the declared attributes otherwise (never an invented
   `w:line` or `w:lineRule`), and must sit where CT_PPr puts it — after `w:pStyle`/`w:numPr` and
   before `w:jc`.
2. **python-docx 1.2** — an independent implementation whose `paragraph_format.space_before` and
   `space_after` are real `Length` values, compared in twips with the intent table the Kotlin probe
   wrote.

Host-only. python-docx is a reader, not Word: agreement here is package-structure evidence, never
rendering.
"""
from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
INTENT_FIELDS = 5
PROPERTY_ORDER = ['pStyle', 'keepNext', 'keepLines', 'pageBreakBefore', 'framePr', 'widowControl',
                  'numPr', 'suppressLineNumbers', 'pBdr', 'shd', 'tabs', 'suppressAutoHyphens',
                  'kinsoku', 'wordWrap', 'overflowPunct', 'topLinePunct', 'autoSpaceDE',
                  'autoSpaceDN', 'bidi', 'adjustRightInd', 'snapToGrid', 'spacing', 'ind',
                  'contextualSpacing', 'mirrorIndents', 'suppressOverlap', 'jc']
MAX_SPACING_TWIPS = 31_680


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def optional_int(text: str) -> int | None:
    return None if text == '-' else int(text)


def intent_table(path: Path) -> dict[str, list[dict]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[dict]] = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        fields = line.split('\t')
        require(len(fields) == INTENT_FIELDS,
                f'intent line {index} has {len(fields)} fields, expected {INTENT_FIELDS}')
        before = optional_int(fields[3])
        after = optional_int(fields[4])
        for name, value in (('before', before), ('after', after)):
            require(value is None or 0 <= value <= MAX_SPACING_TWIPS,
                    f'intent line {index} declares spacing {name}={value} outside 0..{MAX_SPACING_TWIPS}')
        table.setdefault(fields[0], []).append({
            'index': int(fields[1]),
            'kind': fields[2],
            'before': before,
            'after': after,
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
        for table in body.findall(f'{W}tbl'):
            require(not list(table.iter(f'{W}spacing')),
                    f'{path.name}: a table cell carries w:spacing, which this writer never emits')

        for paragraph, expected in zip(paragraphs, intent):
            properties = paragraph.find(f'{W}pPr')
            spacing = None if properties is None else properties.find(f'{W}spacing')
            declared = expected['before'] is not None or expected['after'] is not None
            if expected['kind'] not in ('heading', 'paragraph'):
                require(spacing is None,
                        f'{path.name}: a {expected["kind"]} paragraph carries w:spacing but only headings '
                        f'and paragraphs may declare spacing')
            if not declared:
                require(spacing is None,
                        f'{path.name}: paragraph {expected["index"]} carries w:spacing but declares none')
                continue
            require(spacing is not None, f'{path.name}: paragraph {expected["index"]} is missing w:spacing')
            attributes = {key.split('}')[-1]: value for key, value in spacing.attrib.items()}
            allowed = {'before', 'after'}
            require(set(attributes) <= allowed,
                    f'{path.name}: paragraph {expected["index"]} declares undeclared spacing attributes '
                    f'{sorted(set(attributes) - allowed)}')
            for name in ('before', 'after'):
                value = expected[name]
                if value is None:
                    require(name not in attributes,
                            f'{path.name}: paragraph {expected["index"]} declares w:{name} but the intent '
                            f'says none')
                    continue
                require(name in attributes,
                        f'{path.name}: paragraph {expected["index"]} is missing w:{name}')
                require(attributes[name] == str(value),
                        f'{path.name}: paragraph {expected["index"]} w:{name} {attributes[name]!r} != '
                        f'declared {value}')
            names = [child.tag.split('}')[-1] for child in properties]
            unknown = [name for name in names if name not in PROPERTY_ORDER]
            require(not unknown, f'{path.name}: unknown w:pPr children {unknown} at paragraph '
                                 f'{expected["index"]}')
            positions = [PROPERTY_ORDER.index(name) for name in names]
            require(positions == sorted(positions),
                    f'{path.name}: w:pPr children {names} are out of CT_PPr order at paragraph '
                    f'{expected["index"]}')
            if 'jc' in names:
                require(names.index('spacing') < names.index('jc'),
                        f'{path.name}: w:spacing must precede w:jc, found {names}')


def check_with_python_docx(path: Path, intent: list[dict]) -> None:
    import docx  # imported lazily so the raw-OPC checks can run without it

    document = docx.Document(str(path))
    require(len(document.paragraphs) == len(intent),
            f'{path.name}: python-docx sees {len(document.paragraphs)} paragraphs, declared {len(intent)}')
    for paragraph, expected in zip(document.paragraphs, intent):
        formatting = paragraph.paragraph_format
        for name, actual in (('before', formatting.space_before), ('after', formatting.space_after)):
            declared = expected[name]
            if declared is None:
                require(actual is None,
                        f'{path.name}: python-docx reports space_{name}={actual} for paragraph '
                        f'{expected["index"]} which declares none')
                continue
            require(actual is not None,
                    f'{path.name}: python-docx sees no space_{name} for paragraph {expected["index"]}')
            require(int(actual.twips) == declared,
                    f'{path.name}: python-docx space_{name} {actual.twips} twips != declared {declared}')


def verify(directory: Path) -> dict:
    intent = intent_table(directory / 'spacing.tsv')
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
            print(f'R83 WRITER SPACING CHECK FAILED: python-docx unavailable: {error}', file=sys.stderr)
            return 1
        print(f'python-docx unavailable ({error}); raw OPC checks only.', file=sys.stderr)
    try:
        report = verify(args.artifacts)
    except Fail as error:
        print(f'R83 WRITER SPACING CHECK FAILED: {error}', file=sys.stderr)
        return 1
    print(
        f"R83 writer spacing: {report['documents']} packages and {report['paragraphs']} body paragraphs "
        "verified with python-docx 1.2 and raw OPC inspection. Not Word rendering or Android evidence."
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
