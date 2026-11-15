#!/usr/bin/env python3
"""Negative controls for the R87 third-party renderer and reader lanes.

The two checkers normally spawn Node. These controls take the dumps those runs produce, tamper one field of
one paragraph or workbook, and assert that the same checkers refuse the tampered data. Nothing here re-runs
the renderer, so a tampered dump is the only thing under test.

The controls skip when the artifacts, Node or `tools/r87node/node_modules` are absent (CI installs them).

Run with: PYTHONPATH=tools python3 -m unittest tools.test_r87_render_checks -v
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
R87 = ROOT / 'testing/build/r87writer/artifacts'
R84 = ROOT / 'testing/build/r84writer/artifacts'
R86 = ROOT / 'testing/build/r86sheet/artifacts'
NODE = shutil.which('node')
MODULES = ROOT / 'tools/r87node/node_modules'

STATE: dict[str, object] = {}


def setUpModule():
    if NODE is None:
        raise unittest.SkipTest('node is not on PATH')
    if not MODULES.is_dir():
        raise unittest.SkipTest('tools/r87node/node_modules is absent; run `npm ci --prefix tools/r87node`')
    if not (R87 / 'shade.tsv').is_file() or not (R84 / 'indent.tsv').is_file():
        raise unittest.SkipTest('R87/R84 writer artifacts are absent; run their runners first')
    workdir = Path(tempfile.mkdtemp(prefix='r87-render-controls-'))
    dumps = {}
    for lane, folder in (('r87', R87), ('r84', R84)):
        output = workdir / f'{lane}.json'
        subprocess.run([NODE, str(ROOT / 'tools/r87node/render_docx.mjs'), str(folder), str(output)],
                       check=True, capture_output=True)
        dumps[lane] = json.loads(output.read_text())
    STATE['workdir'] = workdir
    STATE['dumps'] = dumps
    STATE['xlsx'] = sheet_checker.read(R86, NODE, workdir) if (R86 / 'views.tsv').is_file() else None


def tearDownModule():
    shutil.rmtree(STATE.get('workdir', ''), ignore_errors=True)


class DocxRenderControls(unittest.TestCase):
    def setUp(self):
        self.workdir = Path(STATE['workdir'])
        self.dumps = copy.deepcopy(STATE['dumps'])

    def fail_with(self, lane: str, dump: dict) -> None:
        path = self.workdir / f'{lane}-tampered.json'
        path.write_text(json.dumps(dump))
        args = SimpleNamespace(r87=R87, r84=R84, r86=None, node=NODE,
                               renderer_json=[[lane, str(path)]])
        with self.assertRaises(render_checker.Fail):
            render_checker.verify(args)

    def paragraph(self, dump: dict, name: str, index: int = 0) -> dict:
        document = next(entry for entry in dump['files'] if entry['name'] == name)
        return document['paragraphs'][index]

    def test_undisturbed_dumps_pass(self):
        for lane in ('r87', 'r84'):
            path = self.workdir / f'{lane}-ok.json'
            path.write_text(json.dumps(self.dumps[lane]))
            args = SimpleNamespace(r87=R87 if lane == 'r87' else None,
                                   r84=R84 if lane == 'r84' else None, r86=None, node=NODE,
                                   renderer_json=[[lane, str(path)]])
            render_checker.verify(args)

    def test_changed_background_is_rejected(self):
        dump = self.dumps['r87']
        self.paragraph(dump, 'shaded.docx')['style']['background-color'] = 'rgb(1, 2, 3)'
        self.fail_with('r87', dump)

    def test_removed_background_is_rejected(self):
        dump = self.dumps['r87']
        self.paragraph(dump, 'shaded.docx')['style'].pop('background-color', None)
        self.fail_with('r87', dump)

    def test_changed_text_is_rejected(self):
        dump = self.dumps['r87']
        self.paragraph(dump, 'shaded.docx')['text'] = 'Callou'
        self.fail_with('r87', dump)

    def test_missing_tab_span_is_rejected(self):
        dump = self.dumps['r87']
        self.paragraph(dump, 'toc.docx')['tabSpans'] = 0
        self.fail_with('r87', dump)

    def test_invented_tab_span_is_rejected(self):
        dump = self.dumps['r87']
        self.paragraph(dump, 'plain.docx', 1)['tabSpans'] = 1
        self.fail_with('r87', dump)

    def test_lost_heading_class_is_rejected(self):
        dump = self.dumps['r87']
        self.paragraph(dump, 'shaded.docx')['classes'] = []
        self.fail_with('r87', dump)

    def test_extra_paragraph_is_rejected(self):
        dump = self.dumps['r87']
        document = next(entry for entry in dump['files'] if entry['name'] == 'plain.docx')
        document['paragraphs'].append(copy.deepcopy(document['paragraphs'][0]))
        self.fail_with('r87', dump)

    def test_missing_rendered_file_is_rejected(self):
        dump = self.dumps['r87']
        dump['files'] = [entry for entry in dump['files'] if entry['name'] != 'toc.docx']
        self.fail_with('r87', dump)

    def test_changed_indent_is_rejected(self):
        dump = self.dumps['r84']
        self.paragraph(dump, 'indent-left.docx')['style']['margin-inline-start'] = '18pt'
        self.fail_with('r84', dump)

    def test_changed_first_line_is_rejected(self):
        dump = self.dumps['r84']
        self.paragraph(dump, 'hanging.docx')['style']['text-indent'] = '12pt'
        self.fail_with('r84', dump)

    def test_dropped_indent_style_is_rejected(self):
        dump = self.dumps['r84']
        self.paragraph(dump, 'indent-right.docx')['style'] = {}
        self.fail_with('r84', dump)

    def test_invented_indent_is_rejected(self):
        dump = self.dumps['r84']
        self.paragraph(dump, 'default.docx', 1)['style']['margin-inline-start'] = '36pt'
        self.fail_with('r84', dump)


class SheetReaderControls(unittest.TestCase):
    def setUp(self):
        if STATE.get('xlsx') is None:
            self.skipTest('R86 sheet artifacts are absent; run tools/run_r86_sheet_views.py')
        self.workdir = Path(STATE['workdir'])
        self.dump = copy.deepcopy(STATE['xlsx'])

    def fail_with(self, dump: dict) -> None:
        path = self.workdir / 'exceljs-tampered.json'
        path.write_text(json.dumps(dump))
        with self.assertRaises(sheet_checker.Fail):
            sheet_checker.verify(R86, NODE or '', path)

    def workbook(self, dump: dict, name: str) -> dict:
        return next(entry for entry in dump['files'] if entry['name'] == name)

    def test_undisturbed_dump_passes(self):
        path = self.workdir / 'exceljs-ok.json'
        path.write_text(json.dumps(self.dump))
        totals = sheet_checker.verify(R86, NODE or '', path)
        self.assertEqual(totals['workbooks'], 7)
        self.assertEqual(totals['sheets'], 15)

    def test_changed_colour_is_rejected(self):
        dump = self.dump
        sheet = self.workbook(dump, 'tab-colour.xlsx')['sheets'][0]
        sheet['tabArgb'] = 'FF123456' if sheet['tabArgb'] != 'FF123456' else 'FF000000'
        self.fail_with(dump)

    def test_dropped_colour_is_rejected(self):
        dump = self.dump
        sheet = next(s for entry in dump['files'] for s in entry['sheets'] if s['tabArgb'])
        sheet['tabArgb'] = None
        self.fail_with(dump)

    def test_changed_zoom_is_rejected(self):
        dump = self.dump
        sheet = next(s for entry in dump['files'] for s in entry['sheets'] if s['zoomScale'])
        sheet['zoomScale'] = 100
        self.fail_with(dump)

    def test_changed_first_sheet_is_rejected(self):
        dump = self.dump
        workbook = self.workbook(dump, 'first-sheet.xlsx')
        workbook['firstSheet'] = 0 if workbook['firstSheet'] != 0 else 1
        self.fail_with(dump)

    def test_changed_sheet_state_is_rejected(self):
        dump = self.dump
        sheet = next(s for entry in dump['files'] for s in entry['sheets'] if s['state'] == 'hidden')
        sheet['state'] = 'visible'
        self.fail_with(dump)

    def test_missing_workbook_is_rejected(self):
        dump = self.dump
        dump['files'] = [entry for entry in dump['files'] if entry['name'] != 'zoom.xlsx']
        self.fail_with(dump)


if __name__ == '__main__':
    unittest.main()
