#!/usr/bin/env python3
"""Negative controls for the R93 AutoFilter verifiers.

Each test tampers exactly one thing — a worksheet part or one intent row — and asserts the verifier refuses it.
The undisturbed artifacts are checked first, so a verifier that rejects everything cannot pass, and a tamper
that leaves the bytes semantically identical (an explicit default) is rejected as well, because the point of the
byte layer is that the writer emits the minimal form.

Skips when the R93 artifacts are absent (CI regenerates them with `tools/run_r93_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r93_autofilter -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r93_filter_semantics as semantics
import check_r93_sheet_autofilter as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r93/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None

ROW_PART = 'xl/worksheets/sheet1.xml'      # af.xlsx!Rows   (four columns, A1:D10)
OR_PART = 'xl/worksheets/sheet2.xml'       # af.xlsx!Or     (one OR column, B2:D8)
LOCKED_PART = 'xl/worksheets/sheet3.xml'   # af.xlsx!Locked (protection + one filter + validations)
BARE_PART = 'xl/worksheets/sheet1.xml'     # af-range.xlsx!Bare (a column-less filter)
PLAIN_PART = 'xl/worksheets/sheet1.xml'    # af-plain.xlsx!Plain (no filter)
APPLIED_PART = 'xl/worksheets/sheet1.xml'  # af-applied.xlsx!Applied (table plus hidden rows)


def rewrite(source: Path, target: Path, entry: str, transform) -> Path:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        for item in reader.infolist():
            payload = reader.read(item.filename)
            if item.filename == entry:
                payload = transform(payload.decode('utf-8')).encode('utf-8')
            writer.writestr(item, payload)
    return target


class AutoFilterControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'autofilters.tsv').is_file():
            raise unittest.SkipTest('R93 artifacts are absent; run tools/run_r93_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r93-af-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    def patch(self, name: str, entry: str, transform) -> None:
        rewrite(ARTIFACTS / name, self.folder / name, entry, transform)

    def fail(self, require_openpyxl: bool = False) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, require_openpyxl)

    def intent(self) -> list[str]:
        return (self.folder / 'autofilters.tsv').read_text(encoding='utf-8').splitlines()

    def write_intent(self, lines: list[str]) -> None:
        (self.folder / 'autofilters.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')

    # ---- the undisturbed artifacts must pass, with and without the reader lane -------------------------

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['filtered_sheets'], 5)
        self.assertEqual(totals['columns'], 8)
        self.assertEqual(totals['hidden_rows'], 4)
        self.assertEqual(totals['undecided_rows'], 2)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_columns'], 8)
            self.assertEqual(totals['reader_sheets'], 6)

    def test_undisturbed_semantics_pass(self):
        totals = semantics.verify(self.folder / 'filter_verdicts.tsv')
        self.assertEqual(totals['rules'], 7)
        self.assertEqual(totals['verdicts'], 259)
        self.assertEqual(totals['decided'] + totals['undecided'], 259)
        applied = semantics.verify_applied(self.folder)
        self.assertEqual(applied['applied_sheets'], 1)
        self.assertEqual(applied['applied_hidden'], 4)
        self.assertEqual(applied['applied_undecided'], 2)

    def test_artifacts_absent_fail(self):
        with self.assertRaises(checker.Fail):
            checker.verify(self.workdir / 'absent', False)
        with self.assertRaises(semantics.Fail):
            semantics.verify(self.workdir / 'absent/filter_verdicts.tsv')

    # ---- worksheet byte layer --------------------------------------------------------------------------

    def test_declared_filter_missing(self):
        self.patch('af-range.xlsx', BARE_PART, lambda xml: xml.replace('<autoFilter ref="A1:C5"/>', ''))
        self.fail()

    def test_undeclared_filter_present(self):
        def add(xml: str) -> str:
            return xml.replace('</sheetData>', '</sheetData><autoFilter ref="A1:B2"/>')
        self.patch('af-plain.xlsx', PLAIN_PART, add)
        self.fail()

    def test_ref_changed(self):
        self.patch('af.xlsx', ROW_PART, lambda xml: xml.replace('ref="A1:D10"', 'ref="A1:E10"'))
        self.fail()

    def test_column_dropped(self):
        def drop(xml: str) -> str:
            start = xml.index('<filterColumn colId="2"')
            end = xml.index('</filterColumn>', start) + len('</filterColumn>')
            return xml[:start] + xml[end:]
        self.patch('af.xlsx', ROW_PART, drop)
        self.fail()

    def test_value_order_swapped(self):
        self.patch('af.xlsx', ROW_PART,
                   lambda xml: xml.replace('<filter val="red"/><filter val="green"/>',
                                           '<filter val="green"/><filter val="red"/>'))
        self.fail()

    def test_extra_value(self):
        self.patch('af.xlsx', ROW_PART,
                   lambda xml: xml.replace('<filter val="green"/>', '<filter val="green"/><filter val="blue"/>'))
        self.fail()

    def test_and_flag_removed(self):
        self.patch('af.xlsx', ROW_PART, lambda xml: xml.replace('<customFilters and="1">', '<customFilters>'))
        self.fail()

    def test_and_flag_flipped(self):
        self.patch('af.xlsx', ROW_PART, lambda xml: xml.replace('<customFilters and="1">', '<customFilters and="0">'))
        self.fail()

    def test_and_flag_on_single_criterion(self):
        self.patch('af.xlsx', ROW_PART, lambda xml: xml.replace('<customFilters><customFilter', '<customFilters and="1"><customFilter'))
        self.fail()

    def test_default_join_written_explicitly(self):
        self.patch('af.xlsx', OR_PART, lambda xml: xml.replace('<customFilters>', '<customFilters and="0">'))
        self.fail()

    def test_or_join_upgraded_to_and(self):
        self.patch('af.xlsx', OR_PART, lambda xml: xml.replace('<customFilters>', '<customFilters and="1">'))
        self.fail()

    def test_unmodelled_filters_attribute(self):
        self.patch('af.xlsx', ROW_PART, lambda xml: xml.replace('<filters><filter val="red"/>',
                                                                '<filters blank="1"><filter val="red"/>'))
        self.fail()

    def test_explicit_default_button_written(self):
        self.patch('af.xlsx', ROW_PART, lambda xml: xml.replace('<filterColumn colId="0">', '<filterColumn colId="0" hiddenButton="0">'))
        self.fail()

    def test_declared_button_flag_dropped(self):
        self.patch('af.xlsx', ROW_PART,
                   lambda xml: xml.replace('<filterColumn colId="3" hiddenButton="1" showButton="0">',
                                           '<filterColumn colId="3">'))
        self.fail()

    def test_unmodelled_criterion_kind(self):
        def inject(xml: str) -> str:
            return xml.replace('<filters><filter val="red"/>',
                               '<top10 val="1"/><filters><filter val="red"/>')
        self.patch('af.xlsx', ROW_PART, inject)
        self.fail()

    def test_both_criterion_kinds(self):
        def inject(xml: str) -> str:
            return xml.replace('</filters>',
                               '</filters><customFilters><customFilter operator="equal" val="red"/></customFilters>')
        self.patch('af.xlsx', ROW_PART, inject)
        self.fail()

    def test_filter_before_sheet_data(self):
        def move(xml: str) -> str:
            start = xml.index('<autoFilter')
            end = xml.index('</autoFilter>') + len('</autoFilter>')
            return (xml[:start] + xml[end:]).replace('<sheetData>', xml[start:end] + '<sheetData>', 1)
        self.patch('af.xlsx', OR_PART, move)
        self.fail()

    def test_worksheet_sequence_violated(self):
        def inject(xml: str) -> str:
            return xml.replace('</worksheet>', '<mergeCells count="1"><mergeCell ref="A1:A2"/></mergeCells></worksheet>')
        self.patch('af.xlsx', LOCKED_PART, inject)
        self.fail()

    def test_duplicate_filter_column(self):
        def duplicate(xml: str) -> str:
            start = xml.index('<filterColumn colId="0"')
            end = xml.index('</filterColumn>', start) + len('</filterColumn>')
            return xml.replace(xml[start:end], xml[start:end] + xml[start:end], 1)
        self.patch('af.xlsx', ROW_PART, duplicate)
        self.fail()

    def test_out_of_range_column(self):
        self.patch('af.xlsx', OR_PART, lambda xml: xml.replace('colId="1"', 'colId="3"'))
        self.fail()

    # ---- applied filters: what the record actually hides -----------------------------------------------

    def test_applied_table_cell_changed(self):
        lines = [line.replace('tred\x1fn5', 'tred\x1fn50') for line in self.applied()]
        self.write_applied(lines)
        with self.assertRaises(semantics.Fail):
            semantics.verify_applied(self.folder)

    def test_applied_hidden_lie_in_semantics_lane(self):
        lines = [line.replace('\t3,4,6,8\t', '\t3,4,6\t') for line in self.applied()]
        self.write_applied(lines)
        with self.assertRaises(semantics.Fail):
            semantics.verify_applied(self.folder)

    def test_applied_undecided_lie_in_semantics_lane(self):
        lines = [line.replace('\t5,7\t', '\t5\t') for line in self.applied()]
        self.write_applied(lines)
        with self.assertRaises(semantics.Fail):
            semantics.verify_applied(self.folder)

    def test_applied_without_a_filter(self):
        (self.folder / 'autofilters.tsv').write_text('', encoding='utf-8')
        with self.assertRaises(semantics.Fail):
            semantics.verify_applied(self.folder)

    def test_applied_missing_table_field(self):
        self.write_applied(['af-applied.xlsx\tApplied\t3,4,6,8\t5,7\t1,2,3,4,5,6,7,8'])
        with self.assertRaises(semantics.Fail):
            semantics.verify_applied(self.folder)


    def applied(self) -> list[str]:
        return (self.folder / 'applied_filters.tsv').read_text(encoding='utf-8').splitlines()

    def write_applied(self, lines: list[str]) -> None:
        (self.folder / 'applied_filters.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')

    def test_hidden_row_shown(self):
        self.patch('af-applied.xlsx', APPLIED_PART, lambda xml: xml.replace('<row r="3" hidden="1">', '<row r="3">'))
        self.fail()

    def test_undecided_row_hidden(self):
        self.patch('af-applied.xlsx', APPLIED_PART, lambda xml: xml.replace('<row r="5">', '<row r="5" hidden="1">'))
        self.fail()

    def test_header_row_hidden(self):
        self.patch('af-applied.xlsx', APPLIED_PART, lambda xml: xml.replace('<row r="1">', '<row r="1" hidden="1">'))
        self.fail()

    def test_hidden_row_added_without_a_reason(self):
        self.patch('af-applied.xlsx', APPLIED_PART, lambda xml: xml.replace('<row r="2">', '<row r="2" hidden="1">'))
        self.fail()

    def test_applied_intent_lie(self):
        self.write_applied(['af-applied.xlsx\tApplied\t3,4,6\t5,7\t1,2,3,4,5,6,7,8'])
        self.fail()

    def test_applied_intent_missing(self):
        (self.folder / 'applied_filters.tsv').unlink()
        self.fail()

    def test_applied_intent_field_count(self):
        self.write_applied(['af-applied.xlsx\tApplied\t3,4,6,8\t5,7'])
        self.fail()

    # ---- intent layer ----------------------------------------------------------------------------------

    def test_rows_dropped(self):
        self.write_intent([line for line in self.intent() if not line.startswith('af.xlsx\tRows\tA1:D10\t2')])
        self.fail()

    def test_column_count_wrong(self):
        lines = [line.replace('af.xlsx\tRows\tA1:D10\t0', 'af.xlsx\tRows\tA1:D10\t1') for line in self.intent()]
        self.write_intent(lines)
        self.fail()

    def test_button_flag_lie(self):
        lines = [line.replace('\ttrue\tfalse', '\tfalse\ttrue') for line in self.intent()]
        self.write_intent(lines)
        self.fail()

    def test_field_count_wrong(self):
        self.write_intent([self.intent()[0] + '\textra'])
        self.fail()

    # ---- predicate corpus ------------------------------------------------------------------------------

    def corpus(self) -> list[str]:
        return (self.folder / 'filter_verdicts.tsv').read_text(encoding='utf-8').splitlines()

    def write_corpus(self, lines: list[str]) -> None:
        (self.folder / 'filter_verdicts.tsv').write_text('\n'.join(lines) + '\n', encoding='utf-8')

    def semantics_fail(self) -> None:
        with self.assertRaises(semantics.Fail):
            semantics.verify(self.folder / 'filter_verdicts.tsv')

    def test_flipped_verdict(self):
        lines = [line.replace('\ttrue', '\tfalse') if line.endswith('\ttrue') else line for line in self.corpus()]
        self.write_corpus(lines)
        self.semantics_fail()

    def test_flipped_undecided(self):
        lines = ['\t'.join(row.split('\t')[:6] + ['true']) if row.endswith('\tundecided') else row
                 for row in self.corpus()]
        self.write_corpus(lines)
        self.semantics_fail()

    def test_criterion_changed(self):
        self.write_corpus([line.replace('greaterThan=3', 'greaterThan=4') for line in self.corpus()])
        self.semantics_fail()

    def test_join_changed(self):
        lines = [line.replace('\tand\t', '\tor\t') for line in self.corpus()]
        self.write_corpus(lines)
        self.semantics_fail()

    def test_malformed_corpus_row(self):
        self.write_corpus(['C1\tvalues\tred\t-\tnum'])
        self.semantics_fail()

    def test_glob_is_case_insensitive_and_ordered(self):
        self.assertTrue(semantics.glob('x*', 'XY'))
        self.assertFalse(semantics.glob('x*', 'y'))
        self.assertTrue(semantics.glob('a?c', 'abc'))
        self.assertFalse(semantics.glob('a?c', 'ac'))
        self.assertTrue(semantics.glob('*', ''))
        self.assertFalse(semantics.glob('', 'a'))
        self.assertEqual(semantics.number_spelling(3.0), '3')
        self.assertEqual(semantics.number_spelling(3.5), '3.5')
        self.assertIsNone(semantics.numeric_operand('x*'))
        self.assertIsNone(semantics.numeric_operand(''))
        self.assertEqual(semantics.numeric_operand(' 1e3 '), 1000.0)


if __name__ == '__main__':
    unittest.main()
