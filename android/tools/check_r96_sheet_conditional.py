#!/usr/bin/env python3
"""Independent verification for R96 conditional formatting (`<conditionalFormatting>`, `<cfRule>`, §18.3.1.18/§18.3.1.10).

Two layers:

1. **raw OPC** — every declared worksheet must carry exactly the declared blocks, in document order, with the
   declared `sqref`, the declared number of `<cfRule>` children and, for each rule, exactly the attributes the
   intent table declares (absent means absent) in CT_CfRule schema order (`type`, `dxfId`, `priority`,
   `stopIfTrue`, `percent`, `bottom`, `operator`, `rank`). `<formula>` children must match the recorded formulas
   text-for-text, and `<colorScale>`/`<dataBar>` must carry the declared `cfvo` list and colours. A sheet that
   declares no rules must carry no block at all. Every `dxfId` a rule names must resolve inside the workbook's
   `<dxfs count>` table, which is the failure that makes Excel offer a repair. Blocks must sit after
   `</sheetData>` and before the first of `dataValidations`/`hyperlinks`/`pageMargins`, per CT_Worksheet.
2. **openpyxl 3.1.5** — `worksheet.conditional_formatting` must agree on the blocks, their order, their `sqref`
   and every rule field. openpyxl rehydrates `stopIfTrue="0"` as `False` and a missing one as `None`, so the
   distinction the intent table draws between absent and explicit zero is preserved on the reader side too.

A conditional format is a statement about *unseen* data (this cell is empty; this cell is above average). Reading
it back proves the file says what the writer meant; it proves nothing about how a spreadsheet engine paints it,
which needs Excel, LibreOffice or a renderer — none of which this host has. Host-only utility:

    python3 tools/check_r96_sheet_conditional.py testing/build/r96/artifacts [--require-openpyxl]
"""
from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
PR = '{http://schemas.openxmlformats.org/package/2006/relationships}'

FIELDS = 18
RULE_ATTRIBUTES = ('type', 'dxfId', 'priority', 'stopIfTrue', 'percent', 'bottom', 'operator', 'rank')
SCALE_KINDS = {'colorScale', 'dataBar'}
COLUMNS = {'type': 6, 'dxfId': 16, 'priority': 7, 'stopIfTrue': 8, 'percent': 11, 'bottom': 12,
           'operator': 9, 'rank': 10}


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path) -> list[list[str]]:
    require(path.is_file(), f'missing intent table {path}')
    rows: list[list[str]] = []
    for number, line in enumerate(path.read_text(encoding='utf-8').split('\n'), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == FIELDS, f'{path.name} line {number}: {len(parts)} fields, expected {FIELDS}')
        rows.append(parts)
    require(rows, f'{path.name} is empty')
    return rows


def number(text: str, where: str) -> float:
    try:
        return float(text)
    except ValueError:
        raise Fail(f'{where}: {text!r} is not a number')


def sheet_parts(archive: zipfile.ZipFile, where: str) -> dict[str, str]:
    """Resolve sheet name -> part name through the package relationships, not by guessing part numbers."""
    book = ElementTree.fromstring(archive.read('xl/workbook.xml'))
    rels = ElementTree.fromstring(archive.read('xl/_rels/workbook.xml.rels'))
    targets = {rel.get('Id'): rel.get('Target') for rel in rels}
    mapping: dict[str, str] = {}
    for sheet in book.find(f'{S}sheets'):
        target = targets.get(sheet.get(f'{R}id'))
        require(bool(target), f'{where}: sheet {sheet.get("name")} has no resolvable relationship')
        require(bool(target.startswith('worksheets/')), f'{where}: unexpected sheet target {target}')
        mapping[sheet.get('name')] = 'xl/' + target
    return mapping


def attribute_sequence(raw: str, tag: str, where: str) -> list[list[str]]:
    """The order attributes appear in the document, which ElementTree does not reliably expose."""
    sequence = []
    for match in re.finditer(rf'<{tag}\b([^>]*)>', raw):
        sequence.append(re.findall(r'([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=', match.group(1)))
    require(bool(sequence), f'{where}: no <{tag}> found in the raw part')
    return sequence


def verify_raw(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    totals = {'sheets': 0, 'rule_sheets': 0, 'plain_sheets': 0, 'rules': 0, 'blocks': 0, 'dxf_refs': 0}
    grouped: dict[tuple[str, str], list[list[str]]] = {}
    for row in rows:
        grouped.setdefault((row[0], row[1]), []).append(row)
    files = sorted({key[0] for key in grouped})
    for name in files:
        path = folder / name
        require(path.is_file(), f'missing package {path}')
        with zipfile.ZipFile(path) as archive:
            parts = sheet_parts(archive, name)
            styles = ElementTree.fromstring(archive.read('xl/styles.xml'))
            dxfs = styles.find(f'{S}dxfs')
            count = int(dxfs.get('count', '0')) if dxfs is not None else 0
            if dxfs is not None:
                require(len(dxfs.findall(f'{S}dxf')) == count, f'{name}: dxfs count {count} disagrees with children')
            declared = {key[1] for key in grouped if key[0] == name}
            require(declared <= set(parts), f'{name}: intent names unknown sheets {sorted(declared - set(parts))}')
            for sheet in sorted(declared):
                where = f'{name}:{sheet}'
                raw = archive.read(parts[sheet]).decode('utf-8')
                try:
                    root = ElementTree.fromstring(raw)
                except ElementTree.ParseError as error:
                    raise Fail(f'{where}: the worksheet part is not well-formed XML: {error}')
                blocks = root.findall(f'{S}conditionalFormatting')
                block_rows = grouped[(name, sheet)]
                has = block_rows[0][2]
                if has == '0':
                    require(not blocks, f'{where}: declares no rules but carries {len(blocks)} block(s)')
                    require('conditionalFormatting' not in raw, f'{where}: the name appears in a rule-free sheet')
                    totals['plain_sheets'] += 1
                    totals['sheets'] += 1
                    continue
                require(has == '1', f'{where}: hasRules is {has!r}')
                totals['rule_sheets'] += 1
                totals['sheets'] += 1
                # 1. block count, order and sqref.
                for row in block_rows:
                    require(re.fullmatch(r'\d+', row[3]) is not None,
                            f'{where}: block index {row[3]!r} is not a number for a sheet that declares rules')
                indices = sorted({int(row[3]) for row in block_rows})
                require(indices == list(range(len(indices))), f'{where}: block indices {indices} are not dense')
                require(len(blocks) == len(indices),
                        f'{where}: {len(blocks)} blocks written, {len(indices)} declared')
                expected_sqref = [next(r[4] for r in block_rows if r[3] == str(i)) for i in indices]
                actual_sqref = [block.get('sqref') for block in blocks]
                require(actual_sqref == expected_sqref,
                        f'{where}: sqref {actual_sqref} != declared {expected_sqref}')
                for block in blocks:
                    require(set(block.attrib) == {'sqref'},
                            f'{where}: a block carries unmodelled attributes {sorted(block.attrib)}')
                totals['blocks'] += len(blocks)
                # 2. placement inside CT_Worksheet.
                children = [child.tag.split('}')[1] for child in root]
                first = children.index('conditionalFormatting')
                require(children.index('sheetData') < first, f'{where}: a block precedes <sheetData>')
                for follower in ('dataValidations', 'hyperlinks', 'printOptions', 'pageMargins'):
                    if follower in children:
                        require(first < children.index(follower),
                                f'{where}: a block must precede <{follower}> (CT_Worksheet order)')
                # 3. per-rule attributes, order, values, formulas and scale content.
                raw_rules = attribute_sequence(raw, 'cfRule', where)
                require(len(raw_rules) == len(block_rows),
                        f'{where}: {len(raw_rules)} <cfRule> elements, {len(block_rows)} declared')
                for rule, names, row in zip(root.iter(f'{S}cfRule'), raw_rules, block_rows):
                    cells = dict(rule.attrib)
                    expected = [n for n in RULE_ATTRIBUTES if row[COLUMNS[n]] != '-']
                    require(names == expected, f'{where}: cfRule attributes {names} != declared {expected}')
                    require(set(cells) == set(expected), f'{where}: cfRule carries {sorted(cells)}')
                    require(cells['type'] == row[6], f'{where}: type {cells["type"]} != {row[6]}')
                    require(int(cells['priority']) == int(row[7]), f'{where}: priority {cells["priority"]} != {row[7]}')
                    for flag, column in (('stopIfTrue', 8), ('percent', 11), ('bottom', 12)):
                        if row[column] == '1':
                            require(cells.get(flag) in ('1', 'true'), f'{where}: {flag} should be set, is {cells.get(flag)}')
                        elif row[column] == '0':
                            require(cells.get(flag) in ('0', 'false'), f'{where}: {flag} should be explicit 0')
                        else:
                            require(flag not in cells, f'{where}: {flag} should be absent, is {cells.get(flag)}')
                    if row[9] != '-':
                        require(cells.get('operator') == row[9], f'{where}: operator {cells.get("operator")} != {row[9]}')
                    if row[10] != '-':
                        require(int(cells['rank']) == int(row[10]), f'{where}: rank {cells["rank"]} != {row[10]}')
                    if row[16] != '-':
                        require(int(cells['dxfId']) == int(row[16]), f'{where}: dxfId {cells["dxfId"]} != {row[16]}')
                        require(int(cells['dxfId']) < count,
                                f'{where}: dxfId {cells["dxfId"]} names no <dxf> in a {count}-entry table')
                        totals['dxf_refs'] += 1
                    else:
                        require('dxfId' not in cells, f'{where}: a rule without a format carries dxfId')
                    formulas = [node.text or '' for node in rule.findall(f'{S}formula')]
                    expected_formulas = [] if row[13] == '-' else row[13].split('\x1f')
                    require(formulas == expected_formulas,
                            f'{where}: formulas {formulas} != declared {expected_formulas}')
                    if row[6] in SCALE_KINDS:
                        container = rule.find(f'{S}' + row[6])
                        require(container is not None, f'{where}: {row[6]} has no <{row[6]}> container')
                        cvfos = container.findall(f'{S}cfvo')
                        declared_cvfo = [] if row[14] == '-' else row[14].split(',')
                        require(len(cvfos) == len(declared_cvfo),
                                f'{where}: {len(cvfos)} cfvo, {len(declared_cvfo)} declared')
                        for cfvo, spec in zip(cvfos, declared_cvfo):
                            kind, _, value = spec.partition(':')
                            require(cfvo.get('type') == kind, f'{where}: cfvo type {cfvo.get("type")} != {kind}')
                            if value == '-':
                                require(cfvo.get('val') is None, f'{where}: cfvo {kind} must carry no val')
                            else:
                                require(cfvo.get('val') is not None, f'{where}: cfvo {kind} lost its val')
                                require(abs(number(cfvo.get('val'), where) - number(value, where)) < 1e-9,
                                        f'{where}: cfvo val {cfvo.get("val")} != {value}')
                        colours = container.findall(f'{S}color')
                        declared_colours = [] if row[15] == '-' else row[15].split(',')
                        require(len(colours) == len(declared_colours),
                                f'{where}: {len(colours)} colours, {len(declared_colours)} declared')
                        for colour, wanted in zip(colours, declared_colours):
                            rgb = colour.get('rgb')
                            require(bool(rgb) and re.fullmatch(r'FF[0-9A-F]{6}', rgb) is not None,
                                    f'{where}: colour {rgb} is not FF + 6 upper-case hex')
                            require(rgb[2:] == wanted, f'{where}: colour {rgb[2:]} != {wanted}')
                    else:
                        require(not rule.findall(f'{S}colorScale') and not rule.findall(f'{S}dataBar'),
                                f'{where}: a {row[6]} rule carries a scale container')
                    totals['rules'] += 1
                # 4. Priorities are unique inside a sheet: a duplicate silently changes which rule wins.
                priorities = [int(node.get('priority')) for node in root.iter(f'{S}cfRule')]
                require(len(set(priorities)) == len(priorities), f'{where}: duplicate priorities {priorities}')
    return totals


def verify_openpyxl(folder: Path, rows: list[list[str]]) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415

    totals = {'reader_sheets': 0, 'reader_rules': 0}
    grouped: dict[tuple[str, str], list[list[str]]] = {}
    for row in rows:
        grouped.setdefault((row[0], row[1]), []).append(row)
    for name in sorted({key[0] for key in grouped}):
        workbook = openpyxl.load_workbook(folder / name)
        for sheet in sorted({key[1] for key in grouped if key[0] == name}):
            where = f'{name}:{sheet}'
            block_rows = grouped[(name, sheet)]
            blocks = list(workbook[sheet].conditional_formatting)
            if block_rows[0][2] == '0':
                require(not blocks, f'{where}: openpyxl read {len(blocks)} block(s) from a rule-free sheet')
                totals['reader_sheets'] += 1
                continue
            indices = sorted({int(row[3]) for row in block_rows})
            require(len(blocks) == len(indices),
                    f'{where}: openpyxl read {len(blocks)} blocks, {len(indices)} declared')
            expected_sqref = [next(r[4] for r in block_rows if r[3] == str(i)) for i in indices]
            # MultiCellRange is a set-like container: openpyxl reorders its tokens, so the reader layer compares
            # the token set while the raw layer above already proved the written order and spelling.
            actual_sqref = [sorted(str(block.sqref).split()) for block in blocks]
            require(actual_sqref == [sorted(tokens.split()) for tokens in expected_sqref],
                    f'{where}: openpyxl sqref {actual_sqref} != {expected_sqref}')
            for block, index in zip(blocks, indices):
                wanted = [row for row in block_rows if row[3] == str(index)]
                require(len(block.rules) == len(wanted),
                        f'{where}: openpyxl read {len(block.rules)} rules, {len(wanted)} declared')
                for rule, row in zip(block.rules, wanted):
                    require(rule.type == row[6], f'{where}: openpyxl type {rule.type} != {row[6]}')
                    require(int(rule.priority) == int(row[7]), f'{where}: openpyxl priority {rule.priority}')
                    require(rule.operator == (None if row[9] == '-' else row[9]),
                            f'{where}: openpyxl operator {rule.operator} != {row[9]}')
                    for field, column in (('stopIfTrue', 8), ('percent', 11), ('bottom', 12)):
                        want = None if row[column] == '-' else row[column] == '1'
                        require(getattr(rule, field) == want,
                                f'{where}: openpyxl {field} {getattr(rule, field)} != {want}')
                    if row[10] != '-':
                        require(int(rule.rank) == int(row[10]), f'{where}: openpyxl rank {rule.rank}')
                    formula = [str(item) for item in (rule.formula or [])]
                    expected_formulas = [] if row[13] == '-' else row[13].split('\x1f')
                    require(formula == expected_formulas,
                            f'{where}: openpyxl formulas {formula} != {expected_formulas}')
                    if row[6] in SCALE_KINDS:
                        container = getattr(rule, row[6])
                        declared_cvfo = [] if row[14] == '-' else row[14].split(',')
                        require(len(container.cfvo) == len(declared_cvfo),
                                f'{where}: openpyxl {len(container.cfvo)} cfvo != {len(declared_cvfo)}')
                        for cfvo, spec in zip(container.cfvo, declared_cvfo):
                            kind, _, value = spec.partition(':')
                            require(cfvo.type == kind, f'{where}: openpyxl cfvo {cfvo.type} != {kind}')
                            if value == '-':
                                require(cfvo.val is None, f'{where}: openpyxl cfvo {kind} invented a val')
                            else:
                                require(abs(float(cfvo.val) - number(value, where)) < 1e-9,
                                        f'{where}: openpyxl cfvo val {cfvo.val} != {value}')
                        declared_colours = [] if row[15] == '-' else row[15].split(',')
                        # A colour scale holds a list of colours; a data bar holds exactly one, not a list.
                        written = container.color if isinstance(container.color, (list, tuple)) else [container.color]
                        require(len(written) == len(declared_colours),
                                f'{where}: openpyxl {len(written)} colours != {len(declared_colours)}')
                        for colour, wanted in zip(written, declared_colours):
                            rgb = getattr(colour, 'rgb', None)
                            require(bool(rgb), f'{where}: openpyxl colour has no rgb')
                            require(str(rgb)[-6:] == wanted, f'{where}: openpyxl colour {rgb} != {wanted}')
                    totals['reader_rules'] += 1
            totals['reader_sheets'] += 1
    return totals


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    require(folder.is_dir(), f'no artifact folder {folder}')
    rows = intent_table(folder / 'conditional.tsv')
    totals = verify_raw(folder, rows)
    require(totals['rules'] > 0, 'the intent table declares no rules at all')
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        totals.update(verify_openpyxl(folder, rows))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R96 conditional-format verification FAILED: {failure}')
        return 1
    except (zipfile.BadZipFile, KeyError) as error:
        print(f'R96 conditional-format verification FAILED: unreadable package: {error}')
        return 1
    message = (f'R96 conditional formatting: {totals["rules"]} rule(s) in {totals["blocks"]} block(s) over '
               f'{totals["rule_sheets"]} sheet(s), {totals["plain_sheets"]} rule-free sheet(s), '
               f'{totals["dxf_refs"]} resolved dxfId reference(s)')
    if 'reader_rules' in totals:
        message += f'; openpyxl agreed on {totals["reader_rules"]} rule(s) in {totals["reader_sheets"]} sheet(s)'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
