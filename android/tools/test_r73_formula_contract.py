#!/usr/bin/env python3
"""Host-only guards for the R73 formula slice: wiring, corpus hygiene, oracle independence."""
from __future__ import annotations

import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
sys.path.insert(0, str(TOOLS))

import r73_formula_cases

DOC = ROOT / 'format/ooxml/src/main/kotlin/dev/fusion/ooxml'
FORMULA = DOC / 'FormulaEval.kt'
RENDER = DOC / 'NumberFormatRender.kt'
SECTIONS = DOC / 'NumFmtSections.kt'

ESSENTIALS = {
    'ROUNDUP', 'ROUNDDOWN', 'TRUNC', 'LOG', 'PRODUCT', 'REPT', 'REPLACE', 'SEARCH', 'VALUE',
    'TEXT', 'TIME', 'HOUR', 'MINUTE', 'SECOND', 'DATEVALUE', 'DAYS', 'DAYS360', 'YEARFRAC',
    'WEEKNUM', 'ISNA', 'ISNONTEXT', 'ISEVEN', 'ISODD', 'NA', 'ERROR.TYPE', 'TYPE',
    'COUNTBLANK', 'STDEV.S', 'STDEV.P', 'VAR.S', 'VAR.P', 'NORM.INV', 'BINOM.INV',
    'MODE.SNGL', 'MAXIFS', 'MINIFS', 'LOOKUP', 'ISREF', 'AGGREGATE',
}


def essential_names() -> set[str]:
    source = FORMULA.read_text(encoding='utf-8')
    match = re.search(r'ESSENTIAL_NAMES[^=]*=\s*setOf\((.*?)\)\n', source, re.S)
    if not match:
        raise AssertionError('ESSENTIAL_NAMES set not found')
    return set(re.findall(r'"([^"]+)"', match.group(1)))


class WiringChecks(unittest.TestCase):
    def test_every_formula_source_set_also_compiles_the_renderer(self):
        gradle = (ROOT / 'testing/build.gradle.kts').read_text(encoding='utf-8')
        formula = gradle.count('"**/FormulaEval.kt"')
        render = gradle.count('"**/NumberFormatRender.kt"')
        sections = gradle.count('"**/NumFmtSections.kt"')
        self.assertGreaterEqual(formula, 9)
        self.assertEqual(formula, render, 'every FormulaEval include list needs NumberFormatRender.kt')
        self.assertGreaterEqual(sections, render, 'NumberFormatRender.kt needs NumFmtSections.kt')
        self.assertIn('r73FormulaContracts', gradle)

    def test_host_runners_compile_the_renderer_with_the_engine(self):
        missing = []
        for path in sorted(TOOLS.glob('run_*.py')):
            text = path.read_text(encoding='utf-8')
            if 'FormulaEval' not in text:
                continue
            if 'NumberFormatRender' not in text or 'NumFmtSections' not in text:
                missing.append(path.name)
        self.assertEqual(missing, [], 'runners must add NumberFormatRender.kt and NumFmtSections.kt')

    def test_dynamic_array_batch_is_wired_and_broadcasts(self):
        source = FORMULA.read_text(encoding='utf-8')
        self.assertIn('"SORT", "SORTBY", "UNIQUE", "FILTER" -> evalR73Grid(name, call.args, env)', source)
        self.assertIn('private fun evalR73Grid(', source)
        self.assertIn('is Expr.Binary -> broadcastGrid(e, call.args, env)'.replace(', call.args, env', ', env'), source)
        self.assertIn('private fun broadcastGrid(', source)
        self.assertIn('evalBinary(e.op, l, rr)', source)
        # SORT indexes columns when sorting rows and rows when sorting by column.
        self.assertIn('val limit = if (byCol) source.size else source[0].size', source)

    def test_renderer_is_used_by_the_text_function(self):
        source = FORMULA.read_text(encoding='utf-8')
        self.assertIn('NumberFormatRender.renderNumber', source)
        self.assertIn('NumberFormatRender.renderText', source)
        self.assertTrue(SECTIONS.is_file())

    def test_essential_table_matches_the_declared_surface(self):
        self.assertEqual(essential_names(), ESSENTIALS)
        source = FORMULA.read_text(encoding='utf-8')
        body = source[source.index('private fun evalEssential('):]
        body = body[:body.index('\n    private fun evalDateFunction(')]
        for name in sorted(ESSENTIALS):
            self.assertIn(f'"{name}"', body, f'{name} has no evaluation arm')
        self.assertIn('in ESSENTIAL_NAMES -> evalEssential(name, args, env)', source)


class CorpusChecks(unittest.TestCase):
    def test_cases_are_unique_and_well_formed(self):
        cases = r73_formula_cases.formula_cases()
        self.assertGreaterEqual(len(cases), 340)
        ids = [case[0] for case in cases]
        self.assertEqual(len(ids), len(set(ids)), 'duplicate case ids hide mismatches')
        for case_id, formula, *_ in cases:
            self.assertTrue(formula.startswith('='), case_id)
            self.assertNotIn('\t', case_id)
            self.assertNotIn('\t', formula)

    def test_build_refuses_to_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            r73_formula_cases.build(target)
            with self.assertRaises(SystemExit):
                r73_formula_cases.build(target)

    def test_verify_detects_a_doctored_observation(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory)
            r73_formula_cases.build(target)
            rows = [line.split('\t') for line in
                    (target / 'expected.tsv').read_text(encoding='utf-8').splitlines()]
            candidate = target / 'candidate.txt'

            def write(spoiled: str | None) -> None:
                lines = []
                for row in rows:
                    if row[0] == spoiled:
                        value = 'n:999.0'
                    else:
                        value = row[2] if row[1] == 'G' else f'{row[1]}:{row[2]}'
                    lines.append(f'{row[0]}|{value}|{value}')
                candidate.write_text('\n'.join(lines) + '\n', encoding='utf-8')

            write(None)
            self.assertEqual(r73_formula_cases.verify(target), 0)
            write(rows[0][0])
            self.assertEqual(r73_formula_cases.verify(target), 1)
            proc = subprocess.run([sys.executable, str(TOOLS / 'r73_formula_cases.py'), 'verify', str(target)],
                                  capture_output=True, text=True)
            self.assertNotEqual(proc.returncode, 0)
            self.assertIn('MISMATCH ' + rows[0][0], proc.stdout)

    def test_oracle_is_not_the_kotlin_source(self):
        oracle = (TOOLS / 'r73_formula_cases.py').read_text(encoding='utf-8')
        self.assertNotIn('dev.fusion', oracle)
        self.assertNotIn('subprocess', oracle, 'the oracle must not execute the engine')
        self.assertNotIn('formualizer', oracle, 'the oracle must not reuse the third engine')

    def test_independent_rounding_and_date_helpers_are_exercised(self):
        self.assertEqual(r73_formula_cases.excel_round(2.675, 2, up=True, down=False), 2.68)
        self.assertEqual(r73_formula_cases.excel_round(-2.675, 2, up=True, down=False), -2.68)
        self.assertEqual(r73_formula_cases.days360(
            r73_formula_cases.dt.date(2023, 1, 1), r73_formula_cases.dt.date(2023, 1, 31), False), 30)
        self.assertEqual(r73_formula_cases.days360(
            r73_formula_cases.dt.date(2023, 2, 28), r73_formula_cases.dt.date(2023, 3, 31), False), 30)

    def test_reference_differential_skips_rather_than_claims(self):
        checker = (TOOLS / 'check_r73_formula_reference.py').read_text(encoding='utf-8')
        self.assertIn('unsupported += 1', checker)
        self.assertIn('DECLARED_DIFFERENCES', checker)
        self.assertIn('Not Excel/LibreOffice evidence', checker)


if __name__ == '__main__':
    unittest.main()
