#!/usr/bin/env python3
"""Independent verification for the R91 SpreadsheetML data-validation records (`CT_DataValidation`).

`<dataValidations>` sits after `<sheetData>` in the CT_Worksheet sequence. This verifier checks the bytes
and then the object model:

1. **raw OPC** — every `<dataValidation>` in `xl/worksheets/sheetN.xml` must carry exactly the attributes the
   intent table declares, in Excel's own attribute order, with the `count` attribute matching the number of
   children, `operator` present only for the types the schema gives it to, and `formula2` present only for
   the two-operand operators;
2. **openpyxl 3.1.5** — `worksheet.data_validations.dataValidation` is a full model of the same record, and
   it must agree field for field, including the *inverted* `showDropDown` flag: the intent table declares
   "hide the dropdown" and OOXML spells that `showDropDown="1"`.

Host-only. openpyxl is a reader, not Excel: nothing here says a dropdown appeared or a message was shown.
"""
from __future__ import annotations

import argparse
import sys
import warnings
import zipfile
from pathlib import Path
from xml.etree import ElementTree

S = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
FIELDS = 17
# CT_DataValidation attribute order as Excel itself writes it; `imeMode` is not modelled and never emitted.
EXCEL_ORDER = ['type', 'errorStyle', 'operator', 'allowBlank', 'showDropDown', 'showInputMessage',
               'showErrorMessage', 'errorTitle', 'error', 'promptTitle', 'prompt', 'sqref']
OPERATORLESS = ('list', 'custom')


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_table(path: Path) -> dict[tuple[str, str], list[list[str]]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[tuple[str, str], list[list[str]]] = {}
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == FIELDS, f'{path.name} line {number}: {len(parts)} fields, expected {FIELDS}')
        table.setdefault((parts[0], parts[1]), []).append(parts)
    require(table, f'{path.name} is empty')
    return table


def unescape(value: str) -> str:
    return value.replace('\\t', '\t').replace('\\n', '\n')


def raw_rules(folder: Path, name: str, sheet_index: int) -> list[dict[str, str]]:
    with zipfile.ZipFile(folder / name) as archive:
        entry = f'xl/worksheets/sheet{sheet_index}.xml'
        require(entry in archive.namelist(), f'{name} has no {entry}')
        try:
            root = ElementTree.fromstring(archive.read(entry))
        except ElementTree.ParseError as error:
            raise Fail(f'{name}: {entry} is not well-formed XML: {error}') from error
    container = root.find(f'{S}dataValidations')
    if container is None:
        return []
    children = container.findall(f'{S}dataValidation')
    require(container.get('count') == str(len(children)),
            f'{name}: count={container.get("count")!r} does not match {len(children)} element(s)')
    rules = []
    for child in children:
        # ElementTree preserves document order of attributes, so the writer's order is observable here.
        order = list(child.attrib)
        declared = [name for name in order if name in EXCEL_ORDER]
        require(declared == sorted(declared, key=EXCEL_ORDER.index),
                f'{name}: CT_DataValidation attribute order violated: {order}')
        rule = dict(child.attrib)
        formulas = {leaf.tag.replace(S, ''): (leaf.text or '') for leaf in child}
        require(set(formulas) <= {'formula1', 'formula2'}, f'{name}: unexpected children {sorted(formulas)}')
        require([leaf.tag.replace(S, '') for leaf in child] == sorted(
            (leaf.tag.replace(S, '') for leaf in child), key=['formula1', 'formula2'].index),
            f'{name}: formula1 must precede formula2')
        rule.update(formulas)
        rules.append(rule)
    return rules


def verify_raw(folder: Path, table: dict[tuple[str, str], list[list[str]]]) -> dict[str, int]:
    on_disk = {path.name for path in folder.glob('*.xlsx')}
    declared_files = {name for name, _ in table}
    require(declared_files <= on_disk, f'declared workbooks missing from {folder}: {sorted(declared_files - on_disk)}')
    # The unvalidated workbook is the byte-compatibility control: no rule may appear out of nowhere.
    for name in sorted(on_disk - declared_files):
        require(not raw_rules(folder, name, 1), f'{name} is not in the intent table but carries a rule')
    rules = 0
    workbooks = 0
    for (name, sheet_name), rows in table.items():
        with zipfile.ZipFile(folder / name) as archive:
            workbook = ElementTree.fromstring(archive.read('xl/workbook.xml'))
        sheets = [element.get('name') for element in workbook.find(f'{S}sheets').findall(f'{S}sheet')]
        require(sheet_name in sheets, f'{name}: sheet {sheet_name!r} is absent from workbook.xml')
        observed = raw_rules(folder, name, sheets.index(sheet_name) + 1)
        require(len(observed) == len(rows),
                f'{name}:{sheet_name} carries {len(observed)} rule(s), the intent table declares {len(rows)}')
        for row, actual in zip(rows, observed):
            where = f'{name}:{sheet_name}[{row[2]}]'
            _, _, _, type_token, operator, formula1, formula2, allow, hide, show_error, style, \
                error_title, error, show_input, prompt_title, prompt, sqref = row
            require(actual.get('type') == type_token, f'{where}: type {actual.get("type")!r} != {type_token!r}')
            require(actual.get('sqref') == sqref, f'{where}: sqref {actual.get("sqref")!r} != {sqref!r}')
            require(actual.get('allowBlank') == ('1' if allow == 'true' else '0'), f'{where}: allowBlank')
            require(actual.get('showDropDown') == ('1' if hide == 'true' else '0'),
                    f'{where}: showDropDown must be the inversion of the declared hide flag')
            require(actual.get('showErrorMessage') == ('1' if show_error == 'true' else '0'),
                    f'{where}: showErrorMessage')
            require(actual.get('showInputMessage') == ('1' if show_input == 'true' else '0'),
                    f'{where}: showInputMessage')
            require(actual.get('errorStyle') == style, f'{where}: errorStyle {actual.get("errorStyle")!r}')
            if type_token in OPERATORLESS:
                require('operator' not in actual, f'{where}: {type_token} must not carry an operator')
            else:
                require(actual.get('operator') == operator,
                        f'{where}: operator {actual.get("operator")!r} != {operator!r}')
            require(actual.get('formula1') == formula1, f'{where}: formula1 {actual.get("formula1")!r}')
            if formula2 == '-':
                require('formula2' not in actual, f'{where}: unexpected formula2')
            else:
                require(actual.get('formula2') == unescape(formula2),
                        f'{where}: formula2 {actual.get("formula2")!r}')
            for attribute, expected in (('errorTitle', error_title), ('error', error), ('promptTitle', prompt_title),
                                        ('prompt', prompt)):
                if expected == '-':
                    require(attribute not in actual, f'{where}: unexpected {attribute}')
                else:
                    require(actual.get(attribute) == unescape(expected),
                            f'{where}: {attribute} {actual.get(attribute)!r} != {unescape(expected)!r}')
            rules += 1
        workbooks += 1
    return {'workbooks': workbooks, 'rules': rules}


def verify_openpyxl(folder: Path, table: dict[tuple[str, str], list[list[str]]]) -> dict[str, int]:
    import openpyxl  # noqa: PLC0415  (optional dependency, checked by the caller)

    rules = 0
    for (name, sheet_name), rows in table.items():
        with warnings.catch_warnings():
            warnings.simplefilter('ignore')   # "no default style" is expected for a minimal styles part
            book = openpyxl.load_workbook(folder / name)
        validations = list(book[sheet_name].data_validations.dataValidation)
        require(len(validations) == len(rows),
                f'openpyxl {name}:{sheet_name} reports {len(validations)} rule(s), expected {len(rows)}')
        for row, actual in zip(rows, validations):
            where = f'openpyxl {name}:{sheet_name}[{row[2]}]'
            _, _, _, type_token, operator, formula1, formula2, allow, hide, show_error, style, \
                error_title, error, show_input, prompt_title, prompt, sqref = row
            require(actual.type == type_token, f'{where}: openpyxl type {actual.type!r}')
            require(str(actual.sqref) == sqref, f'{where}: openpyxl sqref {str(actual.sqref)!r} != {sqref!r}')
            require(bool(actual.allowBlank) == (allow == 'true'), f'{where}: openpyxl allowBlank')
            require(bool(actual.showDropDown) == (hide == 'true'),
                    f'{where}: openpyxl showDropDown {actual.showDropDown!r} != declared hide={hide}')
            require(bool(actual.showErrorMessage) == (show_error == 'true'), f'{where}: openpyxl showErrorMessage')
            require(bool(actual.showInputMessage) == (show_input == 'true'), f'{where}: openpyxl showInputMessage')
            require(actual.errorStyle == style, f'{where}: openpyxl errorStyle {actual.errorStyle!r}')
            if type_token in OPERATORLESS:
                require(actual.operator in (None, 'between'),
                        f'{where}: openpyxl operator {actual.operator!r} for a schema-ignored operator')
            else:
                require(actual.operator == operator, f'{where}: openpyxl operator {actual.operator!r}')
            require(actual.formula1 == formula1, f'{where}: openpyxl formula1 {actual.formula1!r}')
            expected2 = None if formula2 == '-' else unescape(formula2)
            require(actual.formula2 == expected2, f'{where}: openpyxl formula2 {actual.formula2!r}')
            for attribute, expected in (('errorTitle', error_title), ('error', error), ('promptTitle', prompt_title),
                                        ('prompt', prompt)):
                want = None if expected == '-' else unescape(expected)
                got = getattr(actual, attribute)
                require(got == want, f'{where}: openpyxl {attribute} {got!r} != {want!r}')
            rules += 1
    return {'reader_rules': rules}


def verify(folder: Path, require_openpyxl: bool) -> dict[str, int]:
    table = intent_table(folder / 'validations.tsv')
    totals = verify_raw(folder, table)
    if require_openpyxl:
        try:
            import openpyxl  # noqa: F401,PLC0415
        except ImportError as error:  # pragma: no cover - environment guard
            raise Fail(f'--require-openpyxl was requested but openpyxl is missing: {error}')
        totals.update(verify_openpyxl(folder, table))
    return totals


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('folder', type=Path)
    parser.add_argument('--require-openpyxl', action='store_true')
    args = parser.parse_args(argv)
    try:
        totals = verify(args.folder, args.require_openpyxl)
    except Fail as failure:
        print(f'R91 data-validation verification FAILED: {failure}')
        return 1
    message = (f'R91 independent ZIP/XML checks: {totals["rules"]} data-validation rules in '
               f'{totals["workbooks"]} workbook(s) verified, including the inverted showDropDown flag')
    if 'reader_rules' in totals:
        message += f'; openpyxl agreed on {totals["reader_rules"]} rule(s) through its DataValidation model'
    print(message)
    return 0


if __name__ == '__main__':
    sys.exit(main())
