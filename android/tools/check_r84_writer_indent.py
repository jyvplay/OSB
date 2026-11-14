#!/usr/bin/env python3
"""Independent verification for the R84 Writer indentation and keep-flag packages.

`DocxPackage` may emit `w:ind` (`w:left` / `w:right` and one signed first-line measurement written as
`w:firstLine` or `w:hanging`) and `w:keepNext` / `w:keepLines` inside `w:pPr`. This verifier opens every
package twice:

1. **raw OPC** — body paragraphs are parsed in document order: `w:ind` and the keep flags must be absent when
   nothing is declared, must carry exactly the declared attributes/values otherwise, must never carry both
   `w:firstLine` and `w:hanging`, and must sit where CT_PPr puts them — keep flags directly after `w:pStyle`
   (when present), then `w:spacing`, then `w:ind`, then `w:jc`.
2. **python-docx 1.2** — an independent implementation whose `paragraph_format.left_indent`,
   `right_indent`, `first_line_indent` are real `Length` values (a negative `first_line_indent` is its
   representation of a hanging indent) and whose `keep_with_next` / `keep_together` are tri-state
   (`None` when the element is absent). Values are compared in twips.

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
INTENT_FIELDS = 8
MAX_INDENT_TWIPS = 31_680
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


def optional_int(text: str) -> int | None:
    return None if text == '-' else int(text)


def flag_value(text: str) -> bool | None:
    """`-` means the element is absent (inherit), `true` a bare element, `false` an explicit w:val="0"."""
    return None if text == '-' else text == 'true'


def intent_table(path: Path) -> dict[str, list[dict]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[dict]] = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        fields = line.split('\t')
        require(len(fields) == INTENT_FIELDS,
                f'intent line {index} has {len(fields)} fields, expected {INTENT_FIELDS}')
        left = optional_int(fields[3])
        right = optional_int(fields[4])
        first_line = optional_int(fields[5])
        for name, value in (('left', left), ('right', right), ('firstLine', first_line)):
            require(value is None or -MAX_INDENT_TWIPS <= value <= MAX_INDENT_TWIPS,
                    f'intent line {index} declares {name}={value} outside ±{MAX_INDENT_TWIPS}')
        for flag, text in (('keepNext', fields[6]), ('keepTogether', fields[7])):
            require(text in ('true', 'false', '-'), f'intent line {index} has a bad {flag} flag {text!r}')
        table.setdefault(fields[0], []).append({
            'index': int(fields[1]),
            'kind': fields[2],
            'left': left,
            'right': right,
            'first_line': first_line,
            'keep_next': flag_value(fields[6]),
            'keep_together': flag_value(fields[7]),
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
            for element in ('ind', 'keepNext', 'keepLines'):
                require(not list(table.iter(f'{W}{element}')),
                        f'{path.name}: a table cell carries w:{element}, which this writer never emits')

        for paragraph, expected in zip(paragraphs, intent):
            properties = paragraph.find(f'{W}pPr')
            indent = None if properties is None else properties.find(f'{W}ind')
            keep_next = None if properties is None else properties.find(f'{W}keepNext')
            keep_lines = None if properties is None else properties.find(f'{W}keepLines')
            declared = (expected['left'] is not None or expected['right'] is not None
                        or expected['first_line'] is not None)
            if expected['kind'] not in ('heading', 'paragraph'):
                require(indent is None and keep_next is None and keep_lines is None,
                        f'{path.name}: a {expected["kind"]} paragraph carries indent/keep flags, but only '
                        f'headings and paragraphs may declare them')
            for element, node, declared_flag in (
                ('keepNext', keep_next, expected['keep_next']),
                ('keepLines', keep_lines, expected['keep_together']),
            ):
                require((node is not None) == (declared_flag is not None),
                        f'{path.name}: paragraph {expected["index"]} w:{element} presence '
                        f'{node is not None} != declared {declared_flag!r}')
                if node is None:
                    continue
                names = {key.split('}')[-1] for key in node.attrib}
                require(names <= {'val'},
                        f'{path.name}: paragraph {expected["index"]} w:{element} carries '
                        f'undeclared attributes {sorted(names - {"val"})}')
                value = node.get(f'{W}val')
                if declared_flag:
                    require(value is None or value.lower() not in ('0', 'false', 'off'),
                            f'{path.name}: paragraph {expected["index"]} w:{element} declares '
                            f'w:val={value!r} but the intent says on')
                else:
                    require(value is not None and value.lower() in ('0', 'false', 'off'),
                            f'{path.name}: paragraph {expected["index"]} w:{element} is an explicit off but '
                            f'w:val is {value!r}; omission would inherit the style hierarchy instead')
            if not declared:
                require(indent is None,
                        f'{path.name}: paragraph {expected["index"]} carries w:ind but declares no indent')
                continue
            require(indent is not None, f'{path.name}: paragraph {expected["index"]} is missing w:ind')
            attributes = {key.split('}')[-1]: value for key, value in indent.attrib.items()}
            allowed = {'left', 'right', 'firstLine', 'hanging'}
            require(set(attributes) <= allowed,
                    f'{path.name}: paragraph {expected["index"]} declares undeclared indent attributes '
                    f'{sorted(set(attributes) - allowed)}')
            require(not ('firstLine' in attributes and 'hanging' in attributes),
                    f'{path.name}: paragraph {expected["index"]} declares both w:firstLine and w:hanging, '
                    f'which CT_Ind forbids')
            for name, key in (('left', 'left'), ('right', 'right')):
                value = expected[name]
                if value is None:
                    require(key not in attributes,
                            f'{path.name}: paragraph {expected["index"]} declares w:{key} but the intent '
                            f'says none')
                    continue
                require(attributes.get(key) == str(value),
                        f'{path.name}: paragraph {expected["index"]} w:{key} {attributes.get(key)!r} != '
                        f'declared {value}')
            first_line = expected['first_line']
            if first_line is None:
                require('firstLine' not in attributes and 'hanging' not in attributes,
                        f'{path.name}: paragraph {expected["index"]} declares a first-line indent that the '
                        f'intent does not')
            elif first_line >= 0:
                require(attributes.get('firstLine') == str(first_line),
                        f'{path.name}: paragraph {expected["index"]} w:firstLine '
                        f'{attributes.get("firstLine")!r} != declared {first_line}')
                require('hanging' not in attributes,
                        f'{path.name}: paragraph {expected["index"]} declares w:hanging for a non-negative '
                        f'first line')
            else:
                require(attributes.get('hanging') == str(-first_line),
                        f'{path.name}: paragraph {expected["index"]} w:hanging '
                        f'{attributes.get("hanging")!r} != declared {-first_line}')
                require('firstLine' not in attributes,
                        f'{path.name}: paragraph {expected["index"]} declares w:firstLine for a negative '
                        f'first line')
            names = [child.tag.split('}')[-1] for child in properties]
            unknown = [name for name in names if name not in PROPERTY_ORDER]
            require(not unknown, f'{path.name}: unknown w:pPr children {unknown} at paragraph '
                                 f'{expected["index"]}')
            positions = [PROPERTY_ORDER.index(name) for name in names]
            require(positions == sorted(positions),
                    f'{path.name}: w:pPr children {names} are out of CT_PPr order at paragraph '
                    f'{expected["index"]}')
            if 'jc' in names:
                require(names.index('ind') < names.index('jc'),
                        f'{path.name}: w:ind must precede w:jc, found {names}')
            if 'spacing' in names:
                require(names.index('spacing') < names.index('ind'),
                        f'{path.name}: w:spacing must precede w:ind, found {names}')


def check_with_python_docx(path: Path, intent: list[dict]) -> None:
    import docx  # imported lazily so the raw-OPC checks can run without it

    document = docx.Document(str(path))
    require(len(document.paragraphs) == len(intent),
            f'{path.name}: python-docx sees {len(document.paragraphs)} paragraphs, declared {len(intent)}')
    for paragraph, expected in zip(document.paragraphs, intent):
        formatting = paragraph.paragraph_format
        for name, actual in (('left', formatting.left_indent), ('right', formatting.right_indent),
                             ('first_line', formatting.first_line_indent)):
            declared = expected[name]
            if declared is None:
                require(actual is None,
                        f'{path.name}: python-docx reports {name}={actual} for paragraph '
                        f'{expected["index"]} which declares none')
                continue
            require(actual is not None,
                    f'{path.name}: python-docx sees no {name} for paragraph {expected["index"]}')
            require(int(actual.twips) == declared,
                    f'{path.name}: python-docx {name} {actual.twips} twips != declared {declared}')
        for name, actual, declared in (
            ('keep_with_next', formatting.keep_with_next, expected['keep_next']),
            ('keep_together', formatting.keep_together, expected['keep_together']),
        ):
            require(actual is declared,
                    f'{path.name}: python-docx reports {name}={actual!r} for paragraph '
                    f'{expected["index"]} which declares {declared!r}; None means inherited, True/False mean '
                    f'an explicit element')


def verify(directory: Path) -> dict:
    intent = intent_table(directory / 'indent.tsv')
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
            print(f'R84 WRITER INDENT CHECK FAILED: python-docx unavailable: {error}', file=sys.stderr)
            return 1
        print(f'python-docx unavailable ({error}); raw OPC checks only.', file=sys.stderr)
    try:
        report = verify(args.artifacts)
    except Fail as error:
        print(f'R84 WRITER INDENT CHECK FAILED: {error}', file=sys.stderr)
        return 1
    print(
        f"R84 writer indentation: {report['documents']} packages and {report['paragraphs']} body paragraphs "
        "verified with python-docx 1.2 and raw OPC inspection. Not Word rendering or pagination evidence."
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
