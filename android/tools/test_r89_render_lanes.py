#!/usr/bin/env python3
"""Negative controls for the R89 third-party lanes: docx-preview run styling and exceljs fonts.

The checkers normally spawn Node. These controls run that once, then tamper one CSS property or one font
field and assert that the same code refuses the tampered dump.

Skips when the R89 artifacts, Node or `tools/r87node/node_modules` are absent.

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r89_render_lanes -v
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
R89 = ROOT / 'testing/build/r89/artifacts'
NODE = shutil.which('node')
MODULES = ROOT / 'tools/r87node/node_modules'

STATE: dict[str, object] = {}


def setUpModule():
    if NODE is None:
        raise unittest.SkipTest('node is not on PATH')
    if not MODULES.is_dir():
        raise unittest.SkipTest('tools/r87node/node_modules is absent; run `npm ci --prefix tools/r87node`')
    if not (R89 / 'runs.tsv').is_file():
        raise unittest.SkipTest('R89 artifacts are absent; run tools/run_r89_checks.py')
    workdir = Path(tempfile.mkdtemp(prefix='r89-render-controls-'))
    output = workdir / 'render.json'
    subprocess.run([NODE, str(ROOT / 'tools/r87node/render_docx.mjs'), str(R89), str(output)],
                   check=True, capture_output=True)
    STATE['workdir'] = workdir
    STATE['render'] = json.loads(output.read_text())
    STATE['xlsx'] = sheet_checker.read(R89, NODE, workdir)


def tearDownModule():
    shutil.rmtree(STATE.get('workdir', ''), ignore_errors=True)


class RunRenderControls(unittest.TestCase):
    def setUp(self):
        self.workdir = Path(STATE['workdir'])
        self.dump = copy.deepcopy(STATE['render'])

    def fail_with(self, dump: dict) -> None:
        path = self.workdir / 'render-tampered.json'
        path.write_text(json.dumps(dump))
        args = SimpleNamespace(r89=R89, node=NODE, renderer_json=[['r89', str(path)]])
        with self.assertRaises(render_checker.Fail):
            render_checker.verify(args)

    def span(self, dump: dict, name: str, index: int = 0) -> dict:
        document = next(entry for entry in dump['files'] if entry['name'] == name)
        return document['paragraphs'][0]['runs'][index]

    def test_undisturbed_dump_passes(self):
        path = self.workdir / 'render-ok.json'
        path.write_text(json.dumps(self.dump))
        args = SimpleNamespace(r89=R89, node=NODE, renderer_json=[['r89', str(path)]])
        totals = render_checker.verify(args)
        self.assertEqual(totals['r89_runs'], 10)
        self.assertEqual(totals['r89_colours'], 5)
        self.assertEqual(totals['r89_sizes'], 6)

    def test_changed_colour_is_rejected(self):
        self.span(self.dump, 'color.docx')['style']['color'] = 'rgb(255, 0, 1)'
        self.fail_with(self.dump)

    def test_dropped_colour_is_rejected(self):
        self.span(self.dump, 'color.docx')['style'].pop('color', None)
        self.fail_with(self.dump)

    def test_changed_size_is_rejected(self):
        self.span(self.dump, 'size.docx')['style']['font-size'] = '23pt'
        self.fail_with(self.dump)

    def test_dropped_size_is_rejected(self):
        self.span(self.dump, 'size.docx')['style'].pop('font-size', None)
        self.fail_with(self.dump)

    def test_invented_colour_is_rejected(self):
        self.span(self.dump, 'plain.docx')['style']['color'] = 'rgb(0, 0, 0)'
        self.fail_with(self.dump)

    def test_flipped_bold_is_rejected(self):
        self.span(self.dump, 'color.docx', 2)['style'].pop('font-weight', None)
        self.fail_with(self.dump)

    def test_dropped_run_is_rejected(self):
        document = next(entry for entry in self.dump['files'] if entry['name'] == 'color.docx')
        document['paragraphs'][0]['runs'].pop()
        self.fail_with(self.dump)


class FontReaderControls(unittest.TestCase):
    def setUp(self):
        self.dump = copy.deepcopy(STATE['xlsx'])

    def cell(self, dump: dict, name: str, index: int) -> dict:
        return next(entry for entry in dump['files'] if entry['name'] == name)['sheets'][0]['cells'][index]

    def test_undisturbed_dump_passes(self):
        totals = sheet_checker.check_r89(self.dump, R89, 8)
        self.assertEqual(totals['workbooks'], 3)
        self.assertEqual(totals['cells'], 8)
        self.assertEqual(totals['sized'], 5)
        self.assertEqual(totals['rounded'], 1)

    def test_changed_size_is_rejected(self):
        self.cell(self.dump, 'font-size.xlsx', 1)['size'] = 22
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r89(self.dump, R89, 8)

    def test_dropped_size_is_rejected(self):
        self.cell(self.dump, 'font-size.xlsx', 0)['size'] = None
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r89(self.dump, R89, 8)

    def test_changed_colour_is_rejected(self):
        self.cell(self.dump, 'font-mixed.xlsx', 0)['color'] = 'FFC00001'
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r89(self.dump, R89, 8)

    def test_flipped_bold_is_rejected(self):
        self.cell(self.dump, 'font-mixed.xlsx', 0)['bold'] = False
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r89(self.dump, R89, 8)

    def test_dropped_workbook_is_rejected(self):
        self.dump['files'] = [entry for entry in self.dump['files'] if entry['name'] != 'font-mixed.xlsx']
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.check_r89(self.dump, R89, 8)


if __name__ == '__main__':
    unittest.main()
