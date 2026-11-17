#!/usr/bin/env python3
"""Negative controls for `tools/check_r103_shift.py`.

Each test breaks one property a lane claims to protect and asserts that the lane notices. The undisturbed
artifacts are verified first, so a control can never pass because a lane was already failing.

What is attacked:

* **the file** — a formula altered in the table, a `$` marker dropped from the written XML, a rule dropped, a
  block's `sqref` renamed, and a marker removed from the `ISBLANK` call;
* **the tables** — a dropped rule row, a dropped value row, an emptied table, a short row;
* **the oracle's own inputs** — a value changed so the recorded verdict can no longer be reproduced, a locked
  reference made relative in the table, a per-token anchor collapsed into a single token, a boundary rule moved
  off row 1048576, and a recorded verdict altered in either direction (fired list, undecided marker).

    PYTHONPATH=tools python3 -m unittest tools.test_r103_shift

Skips when `testing/build/r103/artifacts` is absent (it is produced by `tools/run_r103_checks.py`).
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

import check_r103_shift as lane  # noqa: E402

ARTIFACTS = ROOT / 'testing/build/r103/artifacts'
HAVE = (ARTIFACTS / 'r103.xlsx').is_file() and (ARTIFACTS / 'rules.tsv').is_file()


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


@unittest.skipUnless(HAVE, 'R103 artifacts are absent')
class Controls(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = tempfile.TemporaryDirectory(prefix='r103-shift-')
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
        written, _, recomputed = lane.verify(self.folder, False)
        self.assertEqual((written, recomputed), (10, 106))

    def test_the_reader_lane_agrees_when_openpyxl_is_present(self) -> None:
        try:
            import openpyxl  # noqa: F401
        except ImportError:
            self.skipTest('openpyxl is not importable')
        written, through, _ = lane.verify(self.folder, True)
        self.assertEqual(through, written)

    # --- the file --------------------------------------------------------------------------------

    def test_a_changed_formula_in_the_table_is_caught(self) -> None:
        replace_line(self.folder, 'rules.tsv', 'A1:A9\t1\t', 'A1:A9\t1\texpression\t-\tA1>6\n')
        self.assertFails('table says')

    def test_a_dropped_absolute_marker_is_caught(self) -> None:
        rewrite(self.folder, 'r103.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<formula>$C$1&gt;5</formula>', '<formula>C1&gt;5</formula>'))
        self.assertFails('$C$1')

    def test_a_dropped_rule_is_caught(self) -> None:
        edit_table(self.folder, 'rules.tsv',
                   lambda text: ''.join(line for line in text.splitlines(True)
                                        if not line.startswith('K1:K3\t8\t')))
        self.assertFails('<cfRule> elements')

    def test_a_renamed_block_is_caught(self) -> None:
        edit_table(self.folder, 'rules.tsv', lambda text: text.replace('G1:G9 H1:H9', 'G1:G9'))
        self.assertFails('not in the table')

    def test_a_stripped_isblank_marker_is_caught(self) -> None:
        rewrite(self.folder, 'r103.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<formula>ISBLANK($I$1)</formula>', '<formula>ISBLANK(I1)</formula>'))
        self.assertFails('ISBLANK($I$1)')

    # --- the tables ------------------------------------------------------------------------------

    def test_a_dropped_value_row_is_caught(self) -> None:
        edit_table(self.folder, 'values.tsv',
                   lambda text: ''.join(line for line in text.splitlines(True) if not line.startswith('A2\t')))
        self.assertFails('A2:')

    def test_an_emptied_table_is_caught(self) -> None:
        (self.folder / 'values.tsv').write_text('', encoding='utf-8')
        self.assertFails('intent table is empty')

    def test_a_short_rule_row_is_caught(self) -> None:
        replace_line(self.folder, 'rules.tsv', 'A1:A9\t1\t', 'A1:A9\t1\texpression\n')
        self.assertFails('expected 5')

    # --- the oracle's inputs ---------------------------------------------------------------------

    def test_a_changed_value_is_caught(self) -> None:
        replace_line(self.folder, 'values.tsv', 'A2\t', 'A2\tn\t1.0\n')
        self.assertFails('A2:')

    def test_a_made_relative_reference_is_caught(self) -> None:
        # The recorded verdicts were produced with a locked column; reading it as relative moves the answers.
        edit_table(self.folder, 'rules.tsv', lambda text: text.replace('$D1>5', 'D1>5'))
        rewrite(self.folder, 'r103.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<formula>$D1&gt;5</formula>', '<formula>D1&gt;5</formula>'))
        self.assertFails('$D1')

    def test_a_collapsed_anchor_is_caught(self) -> None:
        # One token instead of two: the second column no longer has its own anchor.
        edit_table(self.folder, 'rules.tsv', lambda text: text.replace('G1:G9 H1:H9', 'G1:H9'))
        rewrite(self.folder, 'r103.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('sqref="G1:G9 H1:H9"', 'sqref="G1:H9"'))
        self.assertFails('H5:')

    def test_a_boundary_rule_moved_off_the_grid_edge_is_caught(self) -> None:
        edit_table(self.folder, 'rules.tsv', lambda text: text.replace('A1048576>0', 'A1>0'))
        rewrite(self.folder, 'r103.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('<formula>A1048576&gt;0</formula>', '<formula>A1&gt;0</formula>'))
        self.assertFails('oracle says')

    def test_a_changed_fired_list_is_caught(self) -> None:
        replace_line(self.folder, 'verdicts.tsv', 'E2\t', 'E2\t3\n')
        self.assertFails('E2:')

    def test_a_changed_undecided_marker_is_caught(self) -> None:
        replace_line(self.folder, 'verdicts.tsv', 'K1\t', 'K1\t-\n')
        self.assertFails('K1:')

    def test_an_invented_verdict_for_a_blank_cell_is_caught(self) -> None:
        edit_table(self.folder, 'verdicts.tsv',
                   lambda text: text + 'I4\t6\n')
        self.assertFails('I4:')

    def test_an_altered_operator_is_caught(self) -> None:
        edit_table(self.folder, 'rules.tsv', lambda text: text.replace('L1:L9\t9\tcellIs\tgreaterThan',
                                                                       'L1:L9\t9\tcellIs\tlessThan'))
        self.assertFails('L')

    def test_a_dropped_sqref_in_the_table_is_caught(self) -> None:
        edit_table(self.folder, 'rules.tsv', lambda text: text.replace('M1:M3', 'N1:N3'))
        self.assertFails('not in the table')


if __name__ == '__main__':
    unittest.main()
