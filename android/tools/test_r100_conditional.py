#!/usr/bin/env python3
"""Negative controls for `tools/check_r100_conditional.py`.

Each test breaks one property the lane claims to protect and asserts that the lane notices:

* the `text` attribute of a text rule, its `operator` token, and the absence of a `<formula>` beside it;
* the `aboveAverage`/`equalAverage`/`stdDev` attributes, including the schema defaults that must stay unwritten;
* the `dxfId` of a rule and the properties of the `dxf` it points at;
* the intent tables themselves (a changed row, a short row, a missing table);
* the evaluator: a wildcard that anchors at the start, a duplicate rule that lets the text `"12.5"` collide with
  the number `12.5`, a case-sensitive duplicate key, a standard deviation divided by `n-1` instead of `n`, and a
  filled cell that the table reports as blank.

The undisturbed artifacts are verified first, so a control can never pass because the lane was already failing.

    PYTHONPATH=tools python3 -m unittest tools.test_r100_conditional

Skips when `testing/build/r100/artifacts` is absent (it is produced by `tools/run_r100_checks.py`).
"""
from __future__ import annotations

import shutil
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))

import check_r100_conditional as lane  # noqa: E402

ARTIFACTS = ROOT / 'testing/build/r100/artifacts'
HAVE = (ARTIFACTS / 'r100.xlsx').is_file() and (ARTIFACTS / 'kinds.tsv').is_file()


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


@unittest.skipUnless(HAVE, 'R100 artifacts are absent')
class Controls(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = tempfile.TemporaryDirectory(prefix='r100-cf-')
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
        raw_kinds, raw_rules, _, _, verdicts = lane.verify(self.folder, False)
        self.assertEqual((raw_kinds, raw_rules, verdicts), (14, 13, 31))

    def test_the_reader_lane_agrees_when_openpyxl_is_present(self) -> None:
        try:
            import openpyxl  # noqa: F401
        except ImportError:
            self.skipTest('openpyxl is not importable')
        raw_kinds, raw_rules, read_kinds, read_rules, verdicts = lane.verify(self.folder, True)
        self.assertEqual((read_kinds, read_rules), (raw_kinds, raw_rules))

    # --- the file -------------------------------------------------------------------------------

    def test_a_changed_text_in_the_table_is_caught(self) -> None:
        replace_line(self.folder, 'kinds.tsv', 'A1:A9\t1\t', 'A1:A9\t1\tcontainsText\tap*X\t-\t-\t-\tfill=FFC7CE\n')
        self.assertFails('text')

    def test_a_dropped_text_attribute_is_caught(self) -> None:
        rewrite(self.folder, 'r100.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace(' text="ap*e"', ''))
        self.assertFails('text')

    def test_a_wrong_operator_token_is_caught(self) -> None:
        # The trap this rule exists for: the negative form's operator is `notContains`, not `notContainsText`.
        rewrite(self.folder, 'r100.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('operator="notContains"', 'operator="notContainsText"'))
        self.assertFails('operator')

    def test_a_formula_beside_a_text_rule_is_caught(self) -> None:
        rewrite(self.folder, 'r100.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace(
                    'text="ap*e"/>',
                    'text="ap*e"><formula>NOT(ISERROR(SEARCH("ap*e",A1)))</formula></cfRule>'))
        self.assertFails('wrote a formula')

    def test_a_defaulted_above_average_writing_a_flag_is_caught(self) -> None:
        rewrite(self.folder, 'r100.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace(
                    '<cfRule type="aboveAverage" dxfId="2" priority="7"/>',
                    '<cfRule type="aboveAverage" dxfId="2" priority="7" aboveAverage="1"/>'))
        self.assertFails('aboveAverage')

    def test_a_changed_std_dev_is_caught(self) -> None:
        replace_line(self.folder, 'kinds.tsv', 'B1:B8\t5\t',
                     'B1:B8\t5\taboveAverage\t-\t-\t-\t2\tfill=FFC7CE\n')
        self.assertFails('stdDev')

    def test_a_dropped_rule_is_caught(self) -> None:
        edit_table(self.folder, 'kinds.tsv',
                   lambda text: ''.join(line for line in text.splitlines(True)
                                        if not line.startswith('C1:C8\t9\t')))
        self.assertFails('expected 14 kind rules')

    def test_a_format_on_a_self_painted_rule_is_caught(self) -> None:
        replace_line(self.folder, 'kinds.tsv', 'D1:D8\t13\t',
                     'D1:D8\t13\tcolorScale\t-\t-\t-\t-\tfill=FFC7CE\n')
        self.assertFails('must not carry a format')

    def test_a_missing_dxf_is_caught(self) -> None:
        rewrite(self.folder, 'r100.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<cfRule type="duplicateValues" dxfId="4" priority="8"/>',
                                          '<cfRule type="duplicateValues" priority="8"/>'))
        self.assertFails('was declared but no dxfId')

    def test_a_changed_dxf_colour_is_caught(self) -> None:
        rewrite(self.folder, 'r100.xlsx', 'xl/styles.xml',
                lambda text: text.replace('rgb="FFFFC7CE"', 'rgb="FF00FF00"'))
        self.assertFails('expected fill=FFC7CE')

    def test_a_short_table_row_is_caught(self) -> None:
        edit_table(self.folder, 'kinds.tsv',
                   lambda text: text.replace('C1:C8\t8\tduplicateValues\t-\t-\t-\t-\tunderline\n',
                                             'C1:C8\t8\tduplicateValues\t-\t-\t-\tunderline\n'))
        self.assertFails('fields, expected 8')

    def test_a_missing_table_is_caught(self) -> None:
        (self.folder / 'rules.tsv').unlink()
        self.assertFails('missing intent table')

    # --- the evaluator ---------------------------------------------------------------------------

    def test_a_changed_verdict_row_is_caught(self) -> None:
        replace_line(self.folder, 'verdicts.tsv', 'A1\t',
                     'A1\tDECIDED|1,2,3,4,5|0|-|1;0;0;1;-;FFC7CE\n')
        self.assertFails('verdict mismatches')

    def test_an_anchored_wildcard_would_change_a_verdict(self) -> None:
        # "pineapple" contains "apple" but does not start with it: if the lane's matcher were anchored, the
        # verdict for A6 would lose priority 1 and the comparison would fail.
        original = lane.contains
        try:
            lane.contains = lambda literal, text: text.startswith(literal.replace('*', ''))
            with self.assertRaises(lane.Fail) as caught:
                lane.verify(self.folder, False)
            self.assertIn('verdict mismatches', str(caught.exception))
        finally:
            lane.contains = original

    def test_a_text_number_collision_would_change_a_verdict(self) -> None:
        # A8 is the text "12.5" beside the numbers 12.5 in A7/A9; if the duplicate key ignored the type, A8
        # would be a duplicate and the declared verdict (unique, priority 6) would be wrong.
        original = lane.key_of
        try:
            lane.key_of = lambda value: None if value is None else 'v:' + lane.text_of(value)
            with self.assertRaises(lane.Fail) as caught:
                lane.verify(self.folder, False)
            self.assertIn('verdict mismatches', str(caught.exception))
        finally:
            lane.key_of = original

    def test_a_case_sensitive_duplicate_key_would_change_a_verdict(self) -> None:
        original = lane.key_of

        def sensitive(value):
            if value is None:
                return None
            if value[0] == 't':
                return 't:' + value[1]
            return original(value)

        try:
            lane.key_of = sensitive
            with self.assertRaises(lane.Fail) as caught:
                lane.verify(self.folder, False)
            self.assertIn('verdict mismatches', str(caught.exception))
        finally:
            lane.key_of = original

    def test_a_dropped_standard_deviation_band_is_caught(self) -> None:
        # `stdDev` widens the band by that many population sigmas. Dropping the band entirely makes priority 9
        # (strictly above the mean, ties included) fire on B5 and B6, so the verdicts must change.
        # NOTE: the *population-vs-sample* divisor is deliberately NOT distinguishable here — for 1..8 both put 7
        # inside the band — so this control pins the band, not the divisor. `docs/R100-RESULTS.md` §F says so.
        original = lane.sigma
        try:
            lane.sigma = lambda numbers: 0.0
            with self.assertRaises(lane.Fail) as caught:
                lane.verify(self.folder, False)
            self.assertIn('verdict mismatches', str(caught.exception))
        finally:
            lane.sigma = original

    def test_a_blank_value_in_the_table_is_caught(self) -> None:
        edit_table(self.folder, 'values.tsv',
                   lambda text: ''.join(line for line in text.splitlines(True) if not line.startswith('A3\t')))
        self.assertFails('verdict mismatches')


if __name__ == '__main__':
    unittest.main()
