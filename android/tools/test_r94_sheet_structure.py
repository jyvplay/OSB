#!/usr/bin/env python3
"""Negative controls for the R94 merged-cell and hyperlink verifier.

Each test tampers exactly one thing — a worksheet part, a relationship part, the content types, or one intent
row — and asserts the verifier refuses it. The undisturbed artifacts are checked first, so a verifier that
rejects everything cannot pass.

Skips when the R94 artifacts are absent (CI regenerates them with `tools/run_r94_checks.py`).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r94_sheet_structure -v
"""
from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path

import check_r94_sheet_structure as checker

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / 'testing/build/r94/artifacts'
HAVE_OPENPYXL = importlib.util.find_spec('openpyxl') is not None
GRID = 'xl/worksheets/sheet1.xml'
BARE = 'xl/worksheets/sheet2.xml'
PLAIN = 'xl/worksheets/sheet1.xml'
GRID_RELS = 'xl/worksheets/_rels/sheet1.xml.rels'
BARE_RELS = 'xl/worksheets/_rels/sheet2.xml.rels'


def rewrite(source: Path, target: Path, entries: dict[str, str | None]) -> Path:
    """Copy `source` to `target`, applying `{entry: new text}`; a `None` value drops the entry."""
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as writer:
        written = set()
        for item in reader.infolist():
            if item.filename in entries and entries[item.filename] is None:
                written.add(item.filename)
                continue
            payload = reader.read(item.filename)
            if item.filename in entries:
                payload = entries[item.filename].encode('utf-8')
                written.add(item.filename)
            writer.writestr(item, payload)
        for name, text in entries.items():
            if text is not None and name not in written:
                writer.writestr(name, text.encode('utf-8'))
    return target


class StructureControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (ARTIFACTS / 'merges.tsv').is_file():
            raise unittest.SkipTest('R94 artifacts are absent; run tools/run_r94_checks.py')
        cls.workdir = Path(tempfile.mkdtemp(prefix='r94-struct-'))

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.workdir, ignore_errors=True)

    def setUp(self):
        self.folder = self.workdir / self._testMethodName
        shutil.copytree(ARTIFACTS, self.folder)

    # ---- helpers ---------------------------------------------------------------------------------------

    def text(self, name: str, entry: str) -> str:
        with zipfile.ZipFile(self.folder / name) as archive:
            return archive.read(entry).decode('utf-8')

    def patch(self, name: str, entries: dict[str, str | None]) -> None:
        rewrite(ARTIFACTS / name, self.folder / name, entries)

    def patch_text(self, name: str, entry: str, transform) -> None:
        self.patch(name, {entry: transform(self.text(name, entry))})

    def fail(self, require_openpyxl: bool = False) -> None:
        with self.assertRaises(checker.Fail):
            checker.verify(self.folder, require_openpyxl)

    def lines(self, name: str) -> list[str]:
        return (self.folder / name).read_text(encoding='utf-8').splitlines()

    def write_lines(self, name: str, lines: list[str]) -> None:
        (self.folder / name).write_text('\n'.join(lines) + '\n', encoding='utf-8')

    # ---- the undisturbed artifacts must pass -----------------------------------------------------------

    def test_undisturbed_artifacts_pass(self):
        totals = checker.verify(self.folder, HAVE_OPENPYXL)
        self.assertEqual(totals['merged_sheets'], 5)
        self.assertEqual(totals['links'], 6)
        self.assertEqual(totals['relationship_parts'], 2)
        self.assertEqual(totals['packages'], 2)
        if HAVE_OPENPYXL:
            self.assertEqual(totals['reader_sheets'], 5)
            self.assertEqual(totals['reader_links'], 6)

    def test_artifacts_absent_fail(self):
        with self.assertRaises(checker.Fail):
            checker.verify(self.workdir / 'absent', False)

    # ---- merged cells ----------------------------------------------------------------------------------

    def test_declared_merges_missing(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace(
            '<mergeCells count="2"><mergeCell ref="C1:D1"/><mergeCell ref="A3:A4"/></mergeCells>', ''))
        self.fail()

    def test_undeclared_merges_present(self):
        self.patch_text('r94.xlsx', BARE, lambda xml: xml.replace(
            '</sheetData>', '</sheetData><mergeCells count="1"><mergeCell ref="A1:B1"/></mergeCells>'))
        self.fail()

    def test_merge_count_wrong(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace('<mergeCells count="2">', '<mergeCells count="1">'))
        self.fail()

    def test_merge_ref_changed(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace('ref="C1:D1"', 'ref="C1:E1"'))
        self.fail()

    def test_merge_ref_order_swapped(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace(
            '<mergeCell ref="C1:D1"/><mergeCell ref="A3:A4"/>',
            '<mergeCell ref="A3:A4"/><mergeCell ref="C1:D1"/>'))
        self.fail()

    def test_single_cell_merge(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace('ref="A3:A4"', 'ref="A3:A3"'))
        self.fail()

    def test_overlapping_merges(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace(
            '<mergeCells count="2">', '<mergeCells count="3">').replace(
            '<mergeCell ref="A3:A4"/>', '<mergeCell ref="A3:A4"/><mergeCell ref="D1:E2"/>'))
        self.fail()

    def test_merges_out_of_sequence(self):
        def move(xml: str) -> str:
            block = '<mergeCells count="2"><mergeCell ref="C1:D1"/><mergeCell ref="A3:A4"/></mergeCells>'
            without = xml.replace(block, '')
            return without.replace('</worksheet>', block + '</worksheet>')
        self.patch_text('r94.xlsx', GRID, move)
        self.fail()

    # ---- hyperlinks and the relationship part ----------------------------------------------------------

    def test_hyperlink_dropped(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace(
            '<hyperlink ref="B1" r:id="rHl2"/>', ''))
        self.fail()

    def test_hyperlink_unknown_relationship(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace('r:id="rHl2"', 'r:id="rHl7"'))
        self.fail()

    def test_relationship_part_dropped(self):
        self.patch('r94.xlsx', {GRID_RELS: None})
        self.fail()

    def test_relationship_part_invented(self):
        self.patch('r94.xlsx', {BARE_RELS: (
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rHl1" Type="http://schemas.openxmlformats.org/officeDocument/2006/'
            'relationships/hyperlink" Target="https://example.com" TargetMode="External"/></Relationships>')})
        self.fail()

    def test_relationship_target_changed(self):
        self.patch_text('r94.xlsx', GRID_RELS, lambda xml: xml.replace(
            'Target="mailto:someone@example.com"', 'Target="mailto:other@example.com"'))
        self.fail()

    def test_relationship_target_mode_dropped(self):
        self.patch_text('r94.xlsx', GRID_RELS, lambda xml: xml.replace(
            ' TargetMode="External"/>', '/>').replace(' TargetMode="External" />', '/>'))
        self.fail()

    def test_relationship_type_changed(self):
        self.patch_text('r94.xlsx', GRID_RELS, lambda xml: xml.replace(
            'relationships/hyperlink"', 'relationships/drawing"'))
        self.fail()

    def test_duplicate_relationship_id(self):
        self.patch_text('r94.xlsx', GRID_RELS, lambda xml: xml.replace('Id="rHl2"', 'Id="rHl1"'))
        self.fail()

    def test_extra_relationship(self):
        extra = ('<Relationship Id="rHl3" Type="http://schemas.openxmlformats.org/officeDocument/2006/'
                 'relationships/hyperlink" Target="https://third.example" TargetMode="External"/>')
        self.patch_text('r94.xlsx', GRID_RELS, lambda xml: xml.replace('</Relationships>', extra + '</Relationships>'))
        self.fail()

    def test_internal_link_gains_an_id(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace(
            '<hyperlink ref="A2" location="\'Grid\'!B1"/>', '<hyperlink ref="A2" r:id="rHl1" location="\'Grid\'!B1"/>'))
        self.fail()

    def test_location_changed(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace("location=\"'Grid'!B1\"", 'location="B1"'))
        self.fail()

    def test_undeclared_tooltip(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace(
            '<hyperlink ref="B1" r:id="rHl2"/>', '<hyperlink ref="B1" r:id="rHl2" tooltip="hi"/>'))
        self.fail()

    def test_attribute_order_broken(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace(
            '<hyperlink ref="A1" r:id="rHl1" tooltip="open &amp; &lt;go&gt;" display="example"/>',
            '<hyperlink tooltip="open &amp; &lt;go&gt;" ref="A1" r:id="rHl1" display="example"/>'))
        self.fail()

    def test_missing_rels_namespace_declaration(self):
        self.patch_text('r94.xlsx', GRID, lambda xml: xml.replace(
            ' xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"', ''))
        self.fail()

    # ---- package level ---------------------------------------------------------------------------------

    def test_rels_default_content_type_removed(self):
        self.patch_text('r94.xlsx', '[Content_Types].xml', lambda xml: xml.replace(
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>', ''))
        self.fail()

    def test_sheet_override_removed(self):
        self.patch_text('r94.xlsx', '[Content_Types].xml', lambda xml: xml.replace(
            '<Override PartName="/xl/worksheets/sheet2.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>', ''))
        self.fail()

    # ---- intent layer ----------------------------------------------------------------------------------

    def test_merge_row_dropped(self):
        self.write_lines('merges.tsv', [line for line in self.lines('merges.tsv') if 'Bare' not in line])
        self.fail()

    def test_merge_intent_lie(self):
        self.write_lines('merges.tsv', [line.replace('C1:D1,A3:A4', 'C1:D1') for line in self.lines('merges.tsv')])
        self.fail()

    def test_link_row_dropped(self):
        self.write_lines('hyperlinks.tsv', [line for line in self.lines('hyperlinks.tsv') if 'mailto:' not in line])
        self.fail()

    def test_link_intent_lie(self):
        self.write_lines('hyperlinks.tsv', [line.replace('mailto:someone@example.com', 'mailto:x@example.com')
                                            for line in self.lines('hyperlinks.tsv')])
        self.fail()

    def test_tables_disagree(self):
        self.write_lines('merges.tsv', [line for line in self.lines('merges.tsv') if 'Plain' not in line])
        self.fail()

    def test_field_count_wrong(self):
        self.write_lines('merges.tsv', [self.lines('merges.tsv')[0] + '\textra'])
        self.fail()


if __name__ == '__main__':
    unittest.main()
