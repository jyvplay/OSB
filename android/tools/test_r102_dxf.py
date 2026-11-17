#!/usr/bin/env python3
"""Negative controls for `tools/check_r102_dxf.py`.

Each test breaks one property a lane claims to protect and asserts that the lane notices. The undisturbed
artifacts are verified first, so a control can never pass because the lane was already failing.

What is attacked:

* **the file** — a changed font family, a dropped `<sz>`, a changed border style, a shuffled `CT_Dxf` section
  order, a dxf whose `numFmtId` is not declared in `<numFmts>`, and a rule pointing at a dxf that does not exist;
* **the tables** — a changed row, a dropped row, a short row;
* **the merge** — rows reordered, a later rule's field changed, the recorded detail altered, and a detail invented
  for a decoration-only format;
* **the foreign fixture** — the gradient fill, the vertical-text rotation, the unknown border style, the id-only
  `numFmt` and the dxf count, each removed or changed so the refusal it demonstrates would prove nothing.

    PYTHONPATH=tools python3 -m unittest tools.test_r102_dxf

Skips when `testing/build/r102/artifacts` is absent (it is produced by `tools/run_r102_checks.py`).
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

import check_r102_dxf as lane  # noqa: E402

ARTIFACTS = ROOT / 'testing/build/r102/artifacts'
HAVE = (ARTIFACTS / 'r102.xlsx').is_file() and (ARTIFACTS / 'formats.tsv').is_file()


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


@unittest.skipUnless(HAVE, 'R102 artifacts are absent')
class Controls(unittest.TestCase):
    def setUp(self) -> None:
        self._temporary = tempfile.TemporaryDirectory(prefix='r102-dxf-')
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
        raw, _, merged, foreign = lane.verify(self.folder, False)
        self.assertEqual((raw, merged, foreign), (6, 3, 5))

    def test_the_reader_lane_agrees_when_openpyxl_is_present(self) -> None:
        try:
            import openpyxl  # noqa: F401
        except ImportError:
            self.skipTest('openpyxl is not importable')
        raw, through, _, _ = lane.verify(self.folder, True)
        self.assertEqual(through, raw)

    # --- the file --------------------------------------------------------------------------------

    def test_a_changed_font_family_in_the_table_is_caught(self) -> None:
        replace_line(self.folder, 'formats.tsv', 'A1:A6\t1\t',
                     'A1:A6\t1\texpression\t0\t0|0|0|0|-|Arial|14.0|-|-|-|-|-|-|-\n')
        self.assertFails('raw lane, dxf 0')

    def test_a_dropped_font_size_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml', lambda text: text.replace('<sz val="14"/>', ''))
        self.assertFails('raw lane, dxf 0')

    def test_a_changed_border_style_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<bottom style="double"/>', '<bottom style="thick"/>'))
        self.assertFails('raw lane, dxf 2')

    def test_a_dropped_border_colour_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<color rgb="FF00FF00"/>', '', 1))
        self.assertFails('raw lane, dxf 2')

    def test_a_shuffled_section_order_is_caught(self) -> None:
        def shuffle(text: str) -> str:
            # Move the font section after the protection section inside the kitchen-sink dxf.
            start = text.rindex('<dxf>') + len('<dxf>')
            end = text.rindex('</dxf>')
            body = text[start:end]
            font_end = body.index('</font>') + len('</font>')
            font = body[:font_end]
            rest = body[font_end:]
            return text[:start] + rest + font + text[end:]
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml', shuffle)
        self.assertFails('out of CT_Dxf order')

    def test_a_dropped_numfmt_registration_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<numFmt numFmtId="164" formatCode="0.00%"/>', '', 1))
        self.assertFails('not declared in <numFmts>')

    def test_a_numfmt_id_collision_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<numFmt numFmtId="165" formatCode="#,##0.00"/>',
                                          '<numFmt numFmtId="164" formatCode="#,##0.00"/>'))
        self.assertFails('not declared in <numFmts>')

    def test_a_dangling_dxf_id_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/worksheets/sheet1.xml',
                lambda text: text.replace('dxfId="5"', 'dxfId="9"'))
        self.assertFails('dxfIds')

    def test_a_dropped_dxf_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<dxf><numFmt numFmtId="164" formatCode="0.00%"/></dxf>', '', 1))
        self.assertFails('<dxf> entries')

    def test_a_changed_alignment_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace('horizontal="center"', 'horizontal="right"', 1))
        self.assertFails('raw lane, dxf 3')

    def test_a_flipped_protection_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<protection locked="0" hidden="1"/>',
                                          '<protection locked="1" hidden="1"/>'))
        self.assertFails('raw lane, dxf 3')

    def test_a_changed_pattern_type_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace('patternType="darkGrid"', 'patternType="lightGrid"'))
        self.assertFails('raw lane, dxf 4')

    def test_a_changed_pattern_background_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<bgColor rgb="FFFFFF00"/>', '<bgColor rgb="FF00FF00"/>'))
        self.assertFails('raw lane, dxf 4')

    def test_a_dropped_diagonal_flag_is_caught(self) -> None:
        rewrite(self.folder, 'r102.xlsx', 'xl/styles.xml',
                lambda text: text.replace(' diagonalDown="1"', ''))
        self.assertFails('raw lane, dxf 5')

    def test_a_changed_underline_in_the_table_is_caught(self) -> None:
        replace_line(self.folder, 'formats.tsv', 'A1:A6\t6\t',
                     'A1:A6\t6\texpression\t5\t0|0|0|0|-|Courier New|11.5|C6EFCE|lightUp|FFFFFF|#,##0.00|'
                     'left:hair:0000FF,top:medium:FF0000,bottom:double:00FF00,diagonal:slantDashDot:808080/up/down|'
                     'right;bottom;-;-;-;0;-|1;-\n')
        self.assertFails('raw lane, dxf 5')

    # --- the tables ------------------------------------------------------------------------------

    def test_a_dropped_table_row_is_caught(self) -> None:
        edit_table(self.folder, 'formats.tsv',
                   lambda text: ''.join(line for line in text.splitlines(True) if not line.startswith('A1:A6\t6\t')))
        self.assertFails('<dxf> entries')

    def test_a_short_table_row_is_caught(self) -> None:
        edit_table(self.folder, 'formats.tsv',
                   lambda text: text.replace('A1:A6\t2\texpression\t1\t0|0|0|0|-|-|-|-|-|-|0.00%|-|-|-\n',
                                             'A1:A6\t2\texpression\n'))
        self.assertFails('five columns')

    # --- the merge -------------------------------------------------------------------------------

    def test_a_reordered_merge_is_caught(self) -> None:
        # Rules 1 and 2 set disjoint fields, so swapping them changes nothing; rule 3 is the one that *tries* to
        # override the family and the number format, and promoting it must change the merge.
        lines = (self.folder / 'merge.tsv').read_text(encoding='utf-8').splitlines(True)
        (self.folder / 'merge.tsv').write_text(''.join([lines[2], lines[0], lines[1]]), encoding='utf-8')
        self.assertFails('merge recomputed as')

    def test_a_changed_merge_row_is_caught(self) -> None:
        edit_table(self.folder, 'merge.tsv',
                   lambda text: text.replace('Georgia', 'Verdana'))
        self.assertFails('merge recomputed as')

    def test_an_altered_recorded_detail_is_caught(self) -> None:
        edit_table(self.folder, 'detail.tsv', lambda text: text.replace('Georgia', 'Verdana'))
        self.assertFails('merge recomputed as')

    def test_an_invented_detail_for_a_decoration_only_format_is_caught(self) -> None:
        edit_table(self.folder, 'detail.tsv',
                   lambda text: text.replace('A1\tplain\t-', 'A1\tplain\tCalibri|10.0|-|-|-|-|-|-'))
        self.assertFails('must record no detail')

    def test_a_short_detail_row_is_caught(self) -> None:
        edit_table(self.folder, 'merge.tsv',
                   lambda text: text.replace('Georgia|-|0.0|-|-|-|-|-', 'Georgia|-|0.0'))
        self.assertFails('eight fields')

    # --- the foreign fixture ---------------------------------------------------------------------

    def test_a_dropped_gradient_fill_is_caught(self) -> None:
        rewrite(self.folder, 'r102-foreign.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<fill><gradientFill degree="90"><stop position="0">'
                                          '<color rgb="FFFF0000"/></stop><stop position="1">'
                                          '<color rgb="FF0000FF"/></stop></gradientFill></fill>',
                                          '<fill><patternFill patternType="solid"><fgColor rgb="FFFF0000"/>'
                                          '</patternFill></fill>'))
        self.assertFails('gradient fill')

    def test_a_changed_vertical_text_rotation_is_caught(self) -> None:
        rewrite(self.folder, 'r102-foreign.xlsx', 'xl/styles.xml',
                lambda text: text.replace('textRotation="255"', 'textRotation="180"'))
        self.assertFails('vertical-text')

    def test_a_repaired_unknown_border_style_is_caught(self) -> None:
        rewrite(self.folder, 'r102-foreign.xlsx', 'xl/styles.xml',
                lambda text: text.replace('style="notAStyle"', 'style="thin"'))
        self.assertFails('unknown border style')

    def test_a_resolved_id_only_numfmt_is_caught(self) -> None:
        rewrite(self.folder, 'r102-foreign.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<numFmt numFmtId="164"/>', '<numFmt numFmtId="164" formatCode="0.000"/>'))
        self.assertFails('id-only numFmt')

    def test_a_dropped_foreign_dxf_is_caught(self) -> None:
        rewrite(self.folder, 'r102-foreign.xlsx', 'xl/styles.xml',
                lambda text: text.replace('<dxf><numFmt numFmtId="164"/></dxf>', ''))
        self.assertFails('carries 4 dxfs')


if __name__ == '__main__':
    unittest.main()
