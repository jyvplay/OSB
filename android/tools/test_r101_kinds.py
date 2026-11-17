#!/usr/bin/env python3
"""Negative controls for `tools/check_r101_kinds.py`.

Each test breaks one property the lane claims to protect and asserts that the lane notices:

* the file: the `timePeriod` attribute, a self-closing attribute-only rule, the `<iconSet>` child's name, its
  flags *including their absence*, and its boundary count/type/value;
* the tables themselves (a dropped row, a short row, a missing workbook);
* the presets workbook (order and length);
* the evaluator: a boundary resolved from the wrong statistic, a date shifted by one day, a blank predicate that
  stops trimming, an error predicate that stops distinguishing an error, and a reversed icon set read forwards.

The undisturbed artifacts are verified first, so a control can never pass because the lane was already failing.

    PYTHONPATH=tools python3 -m unittest tools.test_r101_kinds

Skips when `testing/build/r101/artifacts` is absent (it is produced by `tools/run_r101_checks.py`).
"""
from __future__ import annotations

import shutil
import sys
import tempfile
import unittest
import zipfile
from datetime import timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))

import check_r101_kinds as lane  # noqa: E402

ARTIFACTS = ROOT / 'testing/build/r101/artifacts'
HAVE = (ARTIFACTS / 'r101.xlsx').is_file() and (ARTIFACTS / 'kinds.tsv').is_file()


def rewrite(folder: Path, book: str, part: str, transform) -> None:
    path = folder / book
    with zipfile.ZipFile(path) as archive:
        entries = {item.filename: archive.read(item.filename) for item in archive.infolist()}
    entries[part] = transform(entries[part].decode('utf-8')).encode('utf-8')
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, body in entries.items():
            archive.writestr(name, body)


def edit_table(folder: Path, name: str, transform) -> None:
    path = folder / name
    path.write_text(transform(path.read_text(encoding='utf-8')), encoding='utf-8')


def replace_line(folder: Path, name: str, key: str, replacement: str) -> None:
    def transform(text: str) -> str:
        out = []
        hit = False
        for line in text.splitlines(True):
            if line.startswith(key):
                out.append(replacement)
                hit = True
            else:
                out.append(line)
        if not hit:
            raise AssertionError(f'{key!r} not in {name}')
        return ''.join(out)
    edit_table(folder, name, transform)


@unittest.skipUnless(HAVE, 'R101 artifacts are absent')
class Controls(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = tempfile.TemporaryDirectory(prefix='r101-kinds-')
        self.folder = Path(self._temporary.name)
        shutil.copytree(ARTIFACTS, self.folder, dirs_exist_ok=True)

    def tearDown(self) -> None:
        self._temporary.cleanup()

    def assertFails(self, fragment: str, *, reader: bool = False) -> None:
        with self.assertRaises(lane.Fail) as caught:
            lane.verify(self.folder, reader)
        self.assertIn(fragment, str(caught.exception))

    # --- the undisturbed artifacts ----------------------------------------------------------------

    def test_the_undisturbed_artifacts_pass(self) -> None:
        raw, presets, _, verdicts = lane.verify(self.folder, False)
        self.assertEqual((raw, presets, verdicts), (20, 17, 51))

    def test_the_reader_lane_agrees_when_openpyxl_is_present(self) -> None:
        try:
            import openpyxl  # noqa: F401
        except ImportError:
            self.skipTest('openpyxl is not importable')
        raw, _, read, _ = lane.verify(self.folder, True)
        self.assertEqual(read, raw)

    # --- the file -------------------------------------------------------------------------------

    def test_a_changed_time_period_in_the_table_is_caught(self) -> None:
        replace_line(self.folder, 'kinds.tsv', 'A1:A9\t17\ttimePeriod\ttoday',
                     'A1:A9\t17\ttimePeriod\tyesterday\t-\t-\t-\t-\t-\tfill=FFC7CE\n')
        self.assertFails('timePeriod')

    def test_a_dropped_time_period_attribute_is_caught(self) -> None:
        rewrite(self.folder, 'r101.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace(' timePeriod="today"', ''))
        self.assertFails('timePeriod')

    def test_a_formula_beside_an_attribute_only_rule_is_caught(self) -> None:
        rewrite(self.folder, 'r101.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<cfRule type="containsBlanks" dxfId="1" priority="13"/>',
                                          '<cfRule type="containsBlanks" dxfId="1" priority="13"><formula>A1=""</formula></cfRule>'))
        self.assertFails('attributes only')

    def test_a_changed_icon_set_name_is_caught(self) -> None:
        replace_line(self.folder, 'kinds.tsv', 'A1:A9\t18\ticonSet',
                     'A1:A9\t18\ticonSet\t-\t3Arrows\t-\t-\t-\tpercent:33.0;percent:67.0\t-\n')
        self.assertFails('iconSet name')

    def test_a_changed_boundary_value_is_caught(self) -> None:
        rewrite(self.folder, 'r101.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<cfvo type="percent" val="33"/>', '<cfvo type="percent" val="34"/>'))
        self.assertFails('boundaries')

    def test_a_dropped_boundary_is_caught(self) -> None:
        rewrite(self.folder, 'r101.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<cfvo type="percent" val="33"/>', ''))
        self.assertFails('boundaries')

    def test_a_defaulted_flag_that_is_written_is_caught(self) -> None:
        rewrite(self.folder, 'r101.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<iconSet iconSet="3TrafficLights1">',
                                          '<iconSet iconSet="3TrafficLights1" showValue="1">'))
        self.assertFails('showValue')

    def test_a_dxf_on_the_format_less_icon_set_is_caught(self) -> None:
        rewrite(self.folder, 'r101.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<cfRule type="iconSet" priority="18">',
                                          '<cfRule type="iconSet" dxfId="0" priority="18">'))
        self.assertFails('declared as absent')

    def test_a_missing_dxf_is_caught(self) -> None:
        rewrite(self.folder, 'r101.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<cfRule type="containsErrors" dxfId="0" priority="15"/>',
                                          '<cfRule type="containsErrors" priority="15"/>'))
        self.assertFails('declared with no dxfId')

    def test_a_dropped_rule_is_caught(self) -> None:
        edit_table(self.folder, 'kinds.tsv',
                   lambda text: ''.join(line for line in text.splitlines(True)
                                        if not line.startswith('A1:A9\t12\t')))
        self.assertFails('uncovered')

    def test_a_short_table_row_is_caught(self) -> None:
        replace_line(self.folder, 'kinds.tsv', 'A1:A9\t11\tduplicateValues',
                     'A1:A9\t11\tduplicateValues\t-\t-\t-\t-\t-\tunderline\n')
        self.assertFails('fields, expected 10')

    # --- the presets workbook -------------------------------------------------------------------

    def test_a_missing_presets_workbook_is_caught(self) -> None:
        (self.folder / 'r101-presets.xlsx').unlink()
        self.assertFails('missing workbook')

    def test_a_swapped_preset_is_caught(self) -> None:
        rewrite(self.folder, 'r101-presets.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('priority="1" timePeriod="today"', 'priority="1" timePeriod="tomorrow"'))
        self.assertFails('preset 1 is')

    # --- the evaluator --------------------------------------------------------------------------

    def test_a_changed_verdict_row_is_caught(self) -> None:
        replace_line(self.folder, 'verdicts.tsv', 'E1\tmonday\t',
                     'E1\tmonday\tDECIDED|8|0|-|0;0;0;0;-;FFC7CE|-\n')
        self.assertFails('verdict mismatches')

    def test_a_percent_boundary_resolved_as_a_percentile_is_caught(self) -> None:
        # The two statistics agree on 1..9 at 33% but not at 67%, so swapping them must move at least one icon.
        original = lane.resolved_boundaries

        def as_percentile(rule, values):
            numbers = sorted(
                value[1] for cell in rule['cells']
                if (value := values.get(cell)) is not None and value[0] == 'n'
            )
            if not numbers:
                return None
            low, high = numbers[0], numbers[-1]
            out = []
            for kind, value in rule['boundaries']:
                if kind == 'percent':
                    out.append(lane.percentile(numbers, value))
                elif kind == 'num':
                    out.append(value)
                elif kind == 'min':
                    out.append(low)
                elif kind == 'max':
                    out.append(high)
                else:
                    out.append(lane.percentile(numbers, value))
            return out

        try:
            lane.resolved_boundaries = as_percentile
            with self.assertRaises(lane.Fail) as caught:
                lane.verify(self.folder, False)
            self.assertIn('verdict mismatches', str(caught.exception))
        finally:
            lane.resolved_boundaries = original

    def test_a_date_shifted_by_one_day_is_caught(self) -> None:
        original = lane.day_from_serial

        def shifted(serial, date1904):
            day = original(serial, date1904)
            return None if day is None else day + timedelta(days=1)

        try:
            lane.day_from_serial = shifted
            with self.assertRaises(lane.Fail) as caught:
                lane.verify(self.folder, False)
            self.assertIn('verdict mismatches', str(caught.exception))
        finally:
            lane.day_from_serial = original

    def test_a_blank_predicate_that_stops_trimming_is_caught(self) -> None:
        original = lane.text_of

        def untrimmed(value):
            if value is None:
                return ''
            kind, payload = value
            if kind == 't':
                return '\u200b' + payload          # never empty, never blank
            return original(value)

        try:
            lane.text_of = untrimmed
            with self.assertRaises(lane.Fail) as caught:
                lane.verify(self.folder, False)
            self.assertIn('verdict mismatches', str(caught.exception))
        finally:
            lane.text_of = original

    def test_an_error_predicate_that_ignores_errors_is_caught(self) -> None:
        original = lane.evaluate

        def blind(rules, values, ref, calendar):
            patched = []
            for rule in rules:
                copy = dict(rule)
                if copy['kind'] == 'containsErrors':
                    copy['kind'] = 'containsBlanks'
                patched.append(copy)
            return original(patched, values, ref, calendar)

        try:
            lane.evaluate = blind
            with self.assertRaises(lane.Fail) as caught:
                lane.verify(self.folder, False)
            self.assertIn('verdict mismatches', str(caught.exception))
        finally:
            lane.evaluate = original

    def test_a_reversed_icon_set_read_forwards_is_caught(self) -> None:
        edit_table(self.folder, 'rules.tsv',
                   lambda text: text.replace('D1:D9\t7\ticonSet\t-\t3Arrows\t1\t',
                                             'D1:D9\t7\ticonSet\t-\t3Arrows\t0\t'))
        self.assertFails('verdict mismatches')

    def test_a_changed_value_is_caught(self) -> None:
        replace_line(self.folder, 'values.tsv', 'B9\tn\t', 'B9\tn\t1.0\n')
        self.assertFails('verdict mismatches')


if __name__ == '__main__':
    unittest.main()
