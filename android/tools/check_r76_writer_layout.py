#!/usr/bin/env python3
"""Independent verification for the R76 Writer section-layout packages.

The production writer (`DocxPackage` with a `Layout`) emits real `.docx` files. This verifier
opens them twice:

1. **python-docx 1.2** — an independent implementation that reads `w:sectPr` into its section API
   (page width/height in EMU, orientation, margins) and exposes header/footer parts. Its numbers
   are compared with the intent table the Kotlin probe wrote.
2. **raw OPC** — the ZIP is inspected directly: content-type overrides must exist for every
   header/footer part, every reference must resolve to exactly one relationship, every target must
   be a part in the package, the field instructions must be the two defined ones, and the page
   geometry must be integral twips inside the supported window.

Host-only. python-docx is a reader, not Word: agreement here is evidence about the package
structure, never about pagination or layout rendering.
"""
from __future__ import annotations

import argparse
import posixpath
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
EMU_PER_TWIP = 635
HEADER_TYPE = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/header'
FOOTER_TYPE = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer'
HEADER_CT = 'application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml'
FOOTER_CT = 'application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml'
SETTINGS_TYPE = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings'
SETTINGS_CT = 'application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml'
SETTINGS_PART = 'word/settings.xml'


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path) -> dict[str, dict]:
    require(path.is_file(), f'missing intent table {path}')
    table = {}
    for index, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        fields = line.split('\t')
        require(len(fields) == 24, f'intent line {index} has {len(fields)} fields, expected 24')
        name = fields[0]
        table[name] = {
            'width': int(fields[1]), 'height': int(fields[2]), 'orientation': fields[3],
            'top': int(fields[4]), 'right': int(fields[5]), 'bottom': int(fields[6]), 'left': int(fields[7]),
            'header_distance': int(fields[8]), 'footer_distance': int(fields[9]),
            'header': fields[10], 'footer': fields[11],
            'columns': int(fields[12]), 'column_space': int(fields[13]),
            'page_number_start': int(fields[14]) if fields[14] else None,
            'section_type': fields[15],
            'first_header': fields[16], 'first_footer': fields[17],
            'even_header': fields[18], 'even_footer': fields[19],
            'line_count_by': int(fields[20]) if fields[20] else None,
            'line_start': int(fields[21]) if fields[21] else None,
            'line_restart': fields[22], 'v_align': fields[23],
        }
    require(table, 'intent table is empty')
    return table


def part_names(archive: zipfile.ZipFile) -> set[str]:
    return {name for name in archive.namelist() if not name.endswith('/')}


def check_package(path: Path, intent: dict) -> dict:
    with zipfile.ZipFile(path) as archive:
        names = part_names(archive)
        document = archive.read('word/document.xml').decode('utf-8')
        rels = archive.read('word/_rels/document.xml.rels').decode('utf-8')
        types = archive.read('[Content_Types].xml').decode('utf-8')
        root = ElementTree.fromstring(document)
        body = root.find(f'{W}body')
        require(body is not None, 'document has no body')
        sect = body.find(f'{W}sectPr')
        require(sect is not None, 'document has no sectPr')
        page = sect.find(f'{W}pgSz')
        margins = sect.find(f'{W}pgMar')
        require(page is not None and margins is not None, 'sectPr lacks pgSz or pgMar')

        width, height = int(page.get(f'{W}w')), int(page.get(f'{W}h'))
        orient = page.get(f'{W}orient', 'portrait')
        require((width, height) == (intent['width'], intent['height']),
                f'{path.name}: pgSz {width}x{height} != intent {intent["width"]}x{intent["height"]}')
        require(orient == ('landscape' if intent['orientation'] == 'LANDSCAPE' else 'portrait'),
                f'{path.name}: orient {orient!r} contradicts intent {intent["orientation"]}')
        for key, attribute in (('top', 'top'), ('right', 'right'), ('bottom', 'bottom'), ('left', 'left'),
                               ('header_distance', 'header'), ('footer_distance', 'footer')):
            require(int(margins.get(f'{W}{attribute}')) == intent[key],
                    f'{path.name}: margin {attribute} {margins.get(f"{W}{attribute}")} != {intent[key]}')

        # --- section type, columns and page-number restart -------------------------------------
        section_type = sect.find(f'{W}type')
        declared_type = intent['section_type']
        if declared_type == 'nextPage':
            if section_type is not None:
                raise Fail(f'{path.name}: a nextPage section must not carry w:type '
                           f'(found {section_type.get(f"{W}val")!r})')
        else:
            require(section_type is not None, f'{path.name}: missing w:type for a {declared_type} section')
            require(section_type.get(f'{W}val') == declared_type,
                    f'{path.name}: w:type {section_type.get(f"{W}val")!r} != declared {declared_type!r}')
        columns = sect.find(f'{W}cols')
        if intent['columns'] <= 1:
            if columns is not None:
                raise Fail(f'{path.name}: a one-column section must not carry w:cols (found {columns.attrib})')
        else:
            require(columns is not None, f'{path.name}: missing w:cols for {intent["columns"]} columns')
            require(columns.get(f'{W}num') == str(intent['columns']),
                    f'{path.name}: w:cols/@num {columns.get(f"{W}num")} != {intent["columns"]}')
            require(columns.get(f'{W}space') == str(intent['column_space']),
                    f'{path.name}: w:cols/@space {columns.get(f"{W}space")} != {intent["column_space"]}')
        numbering = sect.find(f'{W}pgNumType')
        if intent['page_number_start'] is None:
            if numbering is not None:
                raise Fail(f'{path.name}: unexpected w:pgNumType (found {numbering.attrib})')
        else:
            require(numbering is not None, f'{path.name}: missing w:pgNumType')
            require(numbering.get(f'{W}start') == str(intent['page_number_start']),
                    f'{path.name}: w:pgNumType/@start {numbering.get(f"{W}start")} != {intent["page_number_start"]}')
        numbering_lines = sect.find(f'{W}lnNumType')
        if intent['line_count_by'] is None:
            if numbering_lines is not None:
                raise Fail(f'{path.name}: unexpected w:lnNumType (found {numbering_lines.attrib})')
        else:
            require(numbering_lines is not None, f'{path.name}: missing w:lnNumType')
            require(numbering_lines.get(f'{W}countBy') == str(intent['line_count_by']),
                    f'{path.name}: w:lnNumType/@countBy {numbering_lines.get(f"{W}countBy")} '
                    f'!= {intent["line_count_by"]}')
            require(numbering_lines.get(f'{W}start') == str(intent['line_start']),
                    f'{path.name}: w:lnNumType/@start {numbering_lines.get(f"{W}start")} '
                    f'!= {intent["line_start"]}')
            declared_restart = intent['line_restart'] or 'newPage'
            if declared_restart == 'newPage':
                require(numbering_lines.get(f'{W}restart') is None,
                        f'{path.name}: the default restart must be omitted, found '
                        f'{numbering_lines.get(f"{W}restart")!r}')
            else:
                require(numbering_lines.get(f'{W}restart') == declared_restart,
                        f'{path.name}: w:lnNumType/@restart {numbering_lines.get(f"{W}restart")!r} '
                        f'!= {declared_restart!r}')
        v_align = sect.find(f'{W}vAlign')
        if intent['v_align'] == 'top':
            if v_align is not None:
                raise Fail(f'{path.name}: the default vertical alignment must be omitted '
                           f'(found {v_align.get(f"{W}val")!r})')
        else:
            require(v_align is not None, f'{path.name}: missing w:vAlign')
            require(v_align.get(f'{W}val') == intent['v_align'],
                    f'{path.name}: w:vAlign/@val {v_align.get(f"{W}val")!r} != {intent["v_align"]!r}')

        # Schema order for the children this writer emits (CT_SectPr).
        order = [child.tag for child in sect]
        expected_order = [f'{W}headerReference', f'{W}footerReference', f'{W}type', f'{W}pgSz',
                          f'{W}pgMar', f'{W}lnNumType', f'{W}pgNumType', f'{W}cols', f'{W}vAlign',
                          f'{W}titlePg']
        positions = [expected_order.index(tag) for tag in order if tag in expected_order]
        require(positions == sorted(positions),
                f'{path.name}: sectPr children out of CT_SectPr order: {[tag.split("}")[-1] for tag in order]}')

        # --- relationships -------------------------------------------------------------------
        rel_root = ElementTree.fromstring(rels)
        by_id = {}
        for relationship in rel_root:
            rel_id = relationship.get('Id')
            require(rel_id not in by_id, f'{path.name}: duplicate relationship id {rel_id}')
            by_id[rel_id] = relationship
            target = relationship.get('Target')
            require(target is not None, f'{path.name}: relationship {rel_id} has no target')
            if relationship.get('TargetMode') == 'External':
                continue
            resolved = posixpath.normpath(posixpath.join('word', target))
            require(resolved in names, f'{path.name}: relationship {rel_id} targets missing part {resolved}')

        # --- header/footer references, per reference type ---------------------------------------
        wants_first = bool(intent['first_header'] or intent['first_footer'])
        title_pg = sect.find(f'{W}titlePg')
        if wants_first:
            if title_pg is None:
                raise Fail(f'{path.name}: first-page parts declared but w:titlePg is absent')
        else:
            if title_pg is not None:
                raise Fail(f'{path.name}: w:titlePg present without a first-page part')
        found = {'header': None, 'footer': None, 'first_header': None, 'first_footer': None,
                 'even_header': None, 'even_footer': None}
        for kind, ref_type, tag, rel_type, content_type in (
            ('header', 'default', f'{W}headerReference', HEADER_TYPE, HEADER_CT),
            ('footer', 'default', f'{W}footerReference', FOOTER_TYPE, FOOTER_CT),
            ('first_header', 'first', f'{W}headerReference', HEADER_TYPE, HEADER_CT),
            ('first_footer', 'first', f'{W}footerReference', FOOTER_TYPE, FOOTER_CT),
            ('even_header', 'even', f'{W}headerReference', HEADER_TYPE, HEADER_CT),
            ('even_footer', 'even', f'{W}footerReference', FOOTER_TYPE, FOOTER_CT),
        ):
            references = [element for element in sect.findall(tag)
                          if (element.get(f'{W}type') or 'default') == ref_type]
            require(len(references) <= 1,
                    f'{path.name}: {len(references)} {ref_type} references of {tag.split("}")[-1]}')
            reference = references[0] if references else None
            # The message keeps the R76 wording for the default type so existing controls still
            # name the same failure; a first-page reference is labelled with its type.
            label = kind if ref_type == 'default' else f'{kind} (type {ref_type})'
            if intent[kind] == '':
                if reference is not None:
                    raise Fail(f'{path.name}: unexpected {label} reference')
                continue
            if reference is None:
                raise Fail(f'{path.name}: missing {label} reference')
            rel_id = reference.get(f'{R}id')
            require(rel_id in by_id, f'{path.name}: {kind} reference {rel_id} does not resolve')
            relationship = by_id[rel_id]
            require(relationship.get('Type') == rel_type,
                    f'{path.name}: {rel_id} is {relationship.get("Type")}, expected {rel_type}')
            target = posixpath.normpath(posixpath.join('word', relationship.get('Target')))
            require(f'PartName="/{target}"' in types and content_type in types,
                    f'{path.name}: no content-type override for {target}')
            payload = archive.read(target).decode('utf-8')
            require(payload.startswith('<?xml'), f'{path.name}: {target} is not an XML part')
            fields = re.findall(r'w:instr="([^"]*)"', payload)
            for instruction in fields:
                require(instruction.strip() in {'PAGE', 'NUMPAGES'},
                        f'{path.name}: unsupported field instruction {instruction!r}')
            tokens = re.findall(r'\{([A-Z]+)\}', intent[kind])
            require(sorted(field.strip() for field in fields) == sorted(tokens),
                    f'{path.name}: {kind} declares {tokens} but emits {fields}')
            # Reconstruct the running text exactly as a consumer would: each field becomes its
            # token, each w:tab/w:br its control character, then unescape and concatenate w:t.
            rebuilt = re.sub(r'<w:fldSimple w:instr=" PAGE ".*?</w:fldSimple>', '<w:t>{PAGE}</w:t>',
                             payload, flags=re.S)
            rebuilt = re.sub(r'<w:fldSimple w:instr=" NUMPAGES ".*?</w:fldSimple>',
                             '<w:t>{NUMPAGES}</w:t>', rebuilt, flags=re.S)
            rebuilt = rebuilt.replace('<w:tab/>', '\t').replace('<w:br/>', '\n')
            text = ''.join(re.findall(r'<w:t[^>]*>(.*?)</w:t>', rebuilt, flags=re.S))
            for entity, char in (('&lt;', '<'), ('&gt;', '>'), ('&quot;', '"'), ('&apos;', "'"), ('&amp;', '&')):
                text = text.replace(entity, char)
            require(text == intent[kind],
                    f'{path.name}: {kind} text {text!r} != declared {intent[kind]!r}')
            found[kind] = payload

        # --- even/odd headers need the document setting, or Word ignores the parts ---------------
        wants_even = bool(intent['even_header'] or intent['even_footer'])
        if SETTINGS_PART in names:
            if not wants_even:
                raise Fail(f'{path.name}: unexpected {SETTINGS_PART} without an even-page part')
            require(f'PartName="/{SETTINGS_PART}"' in types and SETTINGS_CT in types,
                    f'{path.name}: no content-type override for {SETTINGS_PART}')
            settings_rel = [element for element in rel_root
                            if element.get('Type') == SETTINGS_TYPE]
            require(len(settings_rel) == 1,
                    f'{path.name}: {len(settings_rel)} settings relationships, expected 1')
            require(posixpath.normpath(posixpath.join('word', settings_rel[0].get('Target'))) == SETTINGS_PART,
                    f'{path.name}: settings relationship targets {settings_rel[0].get("Target")}')
            settings = ElementTree.fromstring(archive.read(SETTINGS_PART).decode('utf-8'))
            require(settings.find(f'{W}evenAndOddHeaders') is not None,
                    f'{path.name}: {SETTINGS_PART} lacks w:evenAndOddHeaders')
        elif wants_even:
            raise Fail(f'{path.name}: even-page parts declared but {SETTINGS_PART} is absent')
    return found


def check_with_python_docx(path: Path, intent: dict) -> None:
    import docx  # imported lazily so the raw-OPC checks can run without it

    document = docx.Document(str(path))
    require(document.sections, f'{path.name}: python-docx found no section')
    section = document.sections[0]
    require(int(section.page_width) == intent['width'] * EMU_PER_TWIP,
            f'{path.name}: python-docx page_width {section.page_width} != '
            f'{intent["width"] * EMU_PER_TWIP} EMU')
    require(int(section.page_height) == intent['height'] * EMU_PER_TWIP,
            f'{path.name}: python-docx page_height {section.page_height} != '
            f'{intent["height"] * EMU_PER_TWIP} EMU')
    require(int(section.top_margin) == intent['top'] * EMU_PER_TWIP, f'{path.name}: top margin')
    require(int(section.left_margin) == intent['left'] * EMU_PER_TWIP, f'{path.name}: left margin')
    require(int(section.bottom_margin) == intent['bottom'] * EMU_PER_TWIP, f'{path.name}: bottom margin')
    require(int(section.right_margin) == intent['right'] * EMU_PER_TWIP, f'{path.name}: right margin')
    require(int(section.header_distance) == intent['header_distance'] * EMU_PER_TWIP,
            f'{path.name}: header distance')
    require(int(section.footer_distance) == intent['footer_distance'] * EMU_PER_TWIP,
            f'{path.name}: footer distance')
    landscape = 'LANDSCAPE' in str(section.orientation)
    require(landscape == (intent['orientation'] == 'LANDSCAPE'),
            f'{path.name}: python-docx orientation {section.orientation} contradicts intent')
    # python-docx reads w:type through its own enumeration; agreeing with it is a second reader's
    # opinion on the section break, not a Word render.
    start_type = str(section.start_type).split('.')[-1].split(' ')[0].lower()
    mapped = {'new_page': 'nextPage', 'continuous': 'continuous',
              'even_page': 'evenPage', 'odd_page': 'oddPage'}.get(start_type, start_type)
    require(mapped == intent['section_type'],
            f'{path.name}: python-docx start_type {section.start_type} != declared {intent["section_type"]}')
    if intent['header']:
        require(section.header is not None and not section.header.is_linked_to_previous,
                f'{path.name}: python-docx does not see a header for the section')
    if intent['footer']:
        require(section.footer is not None and not section.footer.is_linked_to_previous,
                f'{path.name}: python-docx does not see a footer for the section')
    wants_first = bool(intent['first_header'] or intent['first_footer'])
    require(bool(section.different_first_page_header_footer) == wants_first,
            f'{path.name}: python-docx different_first_page_header_footer '
            f'{section.different_first_page_header_footer} contradicts intent {wants_first}')
    for kind, container in (('first_header', section.first_page_header),
                            ('first_footer', section.first_page_footer),
                            ('even_header', section.even_page_header),
                            ('even_footer', section.even_page_footer)):
        if not intent[kind]:
            continue
        require(container is not None and not container.is_linked_to_previous,
                f'{path.name}: python-docx does not see the {kind} part')
        # python-docx reads paragraph text from w:r/w:t only, so a w:fldSimple field is invisible to
        # it; the literal text is what it must reproduce, and the raw OPC check above covers fields.
        expected = re.sub(r'\{[A-Z]+\}', '', intent[kind])
        seen = ''.join(paragraph.text for paragraph in container.paragraphs)
        require(seen == expected,
                f'{path.name}: python-docx {kind} text {seen!r} != expected literal {expected!r}')


def verify(directory: Path) -> dict:
    intent = intent_table(directory / 'layout.tsv')
    checked = 0
    for name, expected in intent.items():
        path = directory / f'{name}.docx'
        require(path.is_file(), f'missing package {path}')
        check_package(path, expected)
        check_with_python_docx(path, expected)
        checked += 1
    extra = {path.stem for path in directory.glob('*.docx')} - set(intent) - {'legacy'}
    require(not extra, f'unexpected packages without a declared intent: {sorted(extra)}')
    legacy = directory / 'legacy.docx'
    require(legacy.is_file(), 'missing legacy.docx')
    with zipfile.ZipFile(legacy) as archive:
        document = archive.read('word/document.xml').decode('utf-8')
        require('<w:sectPr/>' in document, 'the legacy document no longer carries an empty sectPr')
        require('header1.xml' not in archive.namelist() and 'footer1.xml' not in archive.namelist(),
                'the legacy document gained a header or footer part')
    return {'documents': checked + 1, 'layout_documents': checked}


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
            print(f'R76 WRITER LAYOUT CHECK FAILED: python-docx unavailable: {error}', file=sys.stderr)
            return 1
        print(f'python-docx unavailable ({error}); raw OPC checks only.', file=sys.stderr)
    try:
        report = verify(args.artifacts)
    except Fail as error:
        print(f'R76 WRITER LAYOUT CHECK FAILED: {error}', file=sys.stderr)
        return 1
    print(
        f"R76 writer layout: {report['documents']} packages ({report['layout_documents']} with page "
        "geometry) verified with python-docx 1.2 and raw OPC inspection; the legacy package keeps "
        "its empty sectPr. Not Word rendering or Android evidence."
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
