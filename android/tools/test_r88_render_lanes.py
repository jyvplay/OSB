#!/usr/bin/env python3
"""Negative controls for the R88 third-party lanes: docx-preview borders and exceljs gridlines.

The checkers normally spawn Node. These controls run that once, then tamper one CSS property or one
`showGridLines` field and assert that the same code refuses the tampered dump. Nothing re-renders, so the
tampered dump is the only thing under test.

Skips when the R88 artifacts, Node or `tools/r87node/node_modules` are absent (CI installs them).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r88_render_lanes -v
"""
from __future__ import annotations

import copy
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

import check_r87_docx_render as render_checker
import check_r87_sheet_exceljs as sheet_checker

ROOT = Path(__file__).resolve().parents[1]
R88 = ROOT / 'testing/build/r88/artifacts'
NODE = shutil.which('node')
MODULES = ROOT / 'tools/r87node/node_modules'

STATE: dict[str, object] = {}


def setUpModule():
    if NODE is None:
        raise unittest.SkipTest('node is not on PATH')
    if not MODULES.is_dir():
        raise unittest.SkipTest('tools/r87node/node_modules is absent; run `npm ci --prefix tools/r87node`')
    if not (R88 / 'borders.tsv').is_file():
        raise unittest.SkipTest('R88 artifacts are absent; run tools/run_r88_checks.py')
    workdir = Path(tempfile.mkdtemp(prefix='r88-render-controls-'))
    output = workdir / 'render.json'
    subprocess.run([NODE, str(ROOT / 'tools/r87node/render_docx.mjs'), str(R88), str(output)],
                   check=True, capture_output=True)
    STATE['workdir'] = workdir
    STATE['render'] = json.loads(output.read_text())
    STATE['xlsx'] = sheet_checker.read(R88, NODE, workdir)


def tearDownModule():
    shutil.rmtree(STATE.get('workdir', ''), ignore_errors=True)


class BorderRenderControls(unittest.TestCase):
    def setUp(self):
        self.workdir = Path(STATE['workdir'])
        self.dump = copy.deepcopy(STATE['render'])

    def fail_with(self, dump: dict) -> None:
        path = self.workdir / 'render-tampered.json'
        path.write_text(json.dumps(dump))
        args = SimpleNamespace(r88=R88, node=NODE, renderer_json=[['r88', str(path)]])
        with self.assertRaises(render_checker.Fail):
            render_checker.verify(args)

    def paragraph(self, dump: dict, name: str, index: int = 0) -> dict:
        document = next(entry for entry in dump['files'] if entry['name'] == name)
        return document['paragraphs'][index]

    def test_undisturbed_dump_passes(self):
        path = self.workdir / 'render-ok.json'
        path.write_text(json.dumps(self.dump))
        args = SimpleNamespace(r88=R88, node=NODE, renderer_json=[['r88', str(path)]])
        totals = render_checker.verify(args)
        self.assertEqual(totals['r88_paragraphs'], 7)
        self.assertEqual(totals['r88_bordered'], 5)
        self.assertEqual(totals['r88_sides'], 12)
        # `between` and `bar` are declared but not rendered by docx-preview: counted, not waved through.
        self.assertEqual(totals['r88_unrendered'], 2)

    def test_changed_width_is_rejected(self):
        self.paragraph(self.dump, 'box.docx', 1)['style']['border-width'] = '2pt'
        self.fail_with(self.dump)

    def test_changed_colour_is_rejected(self):
        self.paragraph(self.dump, 'box.docx', 1)['style']['border-color'] = 'rgb(31, 78, 122)'
        self.fail_with(self.dump)

    def test_changed_style_is_rejected(self):
        self.paragraph(self.dump, 'box.docx', 1)['style']['border-style'] = 'dotted'
        self.fail_with(self.dump)

    def test_added_border_is_rejected(self):
        self.paragraph(self.dump, 'plain.docx', 1)['style']['border-top'] = '1pt solid rgb(0, 0, 0)'
        self.fail_with(self.dump)

    def test_removed_border_is_rejected(self):
        style = self.paragraph(self.dump, 'box.docx', 0)['style']
        style.pop('border-bottom', None)
        self.fail_with(self.dump)

    def test_swapped_sides_are_rejected(self):
        # `styles.docx` declares 1pt top, 3pt right, 1pt bottom, 1pt left.
        self.paragraph(self.dump, 'styles.docx')['style']['border-width'] = '1pt 1pt 1pt 3pt'
        self.fail_with(self.dump)

    def test_heading_kind_is_rejected(self):
        self.paragraph(self.dump, 'box.docx', 0)['classes'] = []
        self.fail_with(self.dump)


class GridlineReaderControls(unittest.TestCase):
    def setUp(self):
        self.dump = copy.deepcopy(STATE['xlsx'])

    def sheet(self, dump: dict, name: str, index: int) -> dict:
        return next(entry for entry in dump['files'] if entry['name'] == name)['sheets'][index]

    def test_undisturbed_dump_passes(self):
        totals = sheet_checker.check_r88(self.dump, R88)
        self.assertEqual(totals['workbooks'], 3)
        self.assertEqual(totals['sheets'], 6)
        self.assertEqual(totals['grid_off'], 3)

    def test_flipped_flag_is_rejected(self):
        self.sheet(self.dump, 'grid-off.xlsx', 0)['showGridLines'] = True
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r88(self.dump, R88)

    def test_dropped_flag_is_rejected(self):
        self.sheet(self.dump, 'grid-off.xlsx', 2)['showGridLines'] = None
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r88(self.dump, R88)

    def test_invented_flag_is_rejected(self):
        self.sheet(self.dump, 'grid-default.xlsx', 0)['showGridLines'] = False
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r88(self.dump, R88)

    def test_changed_zoom_is_rejected(self):
        self.sheet(self.dump, 'grid-off.xlsx', 2)['zoomScale'] = 125
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r88(self.dump, R88)

    def test_changed_state_is_rejected(self):
        self.sheet(self.dump, 'grid-mixed.xlsx', 1)['state'] = 'visible'
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r88(self.dump, R88)

    def test_dropped_workbook_is_rejected(self):
        self.dump['files'] = [entry for entry in self.dump['files'] if entry['name'] != 'grid-mixed.xlsx']
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r88(self.dump, R88)


if __name__ == '__main__':
    unittest.main()
