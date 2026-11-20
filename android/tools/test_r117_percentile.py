#!/usr/bin/env python3
"""Controls for the R117 percentile gate.

Every control mutates exactly one property of a four-file tree (`FormulaEval.kt`, `SpillSheetEngine.kt`, the Kotlin
test, the corpus) and requires the *named* rule to fire. The point is falsifiability: a gate that cannot be made to
fail is a comment.

Two repository controls are included as well — the real tree must be silent, and the corpus must reproduce from its
generator byte for byte — because a fixture that drifted from its generator is the failure mode that would let the
Kotlin test keep passing against stale numbers.

The independent-oracle control recomputes every numeric percentile row through CPython's `statistics.quantiles`
(inclusive and exclusive) and every `A`-family row through a separate transcription of the counting rules, neither
of which goes through the generator's writer.
"""
from __future__ import annotations

import importlib
import math
import shutil
import statistics
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
gate = importlib.import_module('check_r117_percentile')
generator = importlib.import_module('gen_r117_percentile_corpus')

SOURCE = ROOT / gate.SOURCE
SPILL = ROOT / gate.SPILL
TEST = ROOT / gate.TEST
CORPUS = ROOT / gate.CORPUS


class Mutant(unittest.TestCase):
    def tree(self, source=None, spill=None, test=None, corpus=None) -> Path:
        folder = Path(tempfile.mkdtemp(prefix='r117-'))
        self.addCleanup(lambda: shutil.rmtree(folder, ignore_errors=True))
        for relative, base, replacement in (
            (gate.SOURCE, SOURCE, source),
            (gate.SPILL, SPILL, spill),
            (gate.TEST, TEST, test),
            (gate.CORPUS, CORPUS, corpus),
        ):
            path = folder / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            text = base.read_text(encoding='utf-8')
            path.write_text(replacement(text) if replacement else text, encoding='utf-8')
        return folder

    def rules(self, folder: Path) -> set[str]:
        found, _ = gate.violations(folder)
        return {item.split(':')[0] for item in found}


class Registration(Mutant):
    def test_a_name_missing_from_the_dispatch_branch_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace('"PERCENTILE.EXC",', ''))
        self.assertEqual({'R117-1'}, self.rules(folder))

    def test_a_second_dispatch_call_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace(
            '            else -> {\n                val lexicalClosure',
            '            "AVERAGEA" -> evalAggregateA(name, args, env)\n            else -> {\n                val lexicalClosure'))
        self.assertEqual({'R117-1'}, self.rules(folder))

    def test_a_missing_helper_definition_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace(
            'private fun evalAggregateA', 'private fun evalAggregateB'))
        self.assertEqual({'R117-1'}, self.rules(folder))

    def test_a_missing_export_prefix_fires_rule_1(self):
        folder = self.tree(spill=lambda text: text.replace(
            '"PERCENTILE.INC", "PERCENTILE.EXC", "QUARTILE.INC", "QUARTILE.EXC",', ''))
        self.assertEqual({'R117-1'}, self.rules(folder))


class Corpus(Mutant):
    def test_a_function_with_no_rows_fires_rule_2(self):
        folder = self.tree(corpus=lambda text: '\n'.join(
            line for line in text.splitlines() if not line.startswith('STDEVPA\t')) + '\n')
        rules = self.rules(folder)
        self.assertIn('R117-2', rules)
        self.assertIn('R117-5', rules)

    def test_a_thin_function_fires_rule_2(self):
        kept = [line for line in CORPUS.read_text().splitlines() if line.startswith('MAXA\t')][:2]
        folder = self.tree(corpus=lambda text: '\n'.join(
            [line for line in text.splitlines() if not line.startswith('MAXA\t')] + kept) + '\n')
        self.assertIn('R117-2', self.rules(folder))

    def test_an_exclusive_function_without_refusals_fires_rule_2(self):
        folder = self.tree(corpus=lambda text: '\n'.join(
            line for line in text.splitlines()
            if not (line.startswith('PERCENTILE.EXC\t') and line.split('\t')[3] == '#NUM!')) + '\n')
        self.assertIn('R117-2', self.rules(folder))

    def test_a_duplicated_row_fires_rule_3(self):
        first = CORPUS.read_text().splitlines()[1]
        folder = self.tree(corpus=lambda text: text + first + '\n')
        self.assertEqual({'R117-3', 'R117-5'}, self.rules(folder))

    def test_an_unregistered_function_row_fires_rule_3(self):
        folder = self.tree(corpus=lambda text: text + 'FOO\t1,2\t0.5\t1.5\n')
        self.assertEqual({'R117-3', 'R117-5'}, self.rules(folder))

    def test_a_non_literal_error_fires_rule_3(self):
        folder = self.tree(corpus=lambda text: text.replace('#DIV/0!', '#SPILL!'))
        self.assertEqual({'R117-3', 'R117-5'}, self.rules(folder))

    def test_a_tampered_value_breaks_the_identities_and_the_generator(self):
        def tamper(text: str) -> str:
            lines = text.splitlines()
            for index, line in enumerate(lines):
                if line.startswith('PERCENTILE.INC\t'):
                    parts = line.split('\t')
                    parts[3] = '987654.321'
                    lines[index] = '\t'.join(parts)
                    break
            return '\n'.join(lines) + '\n'

        folder = self.tree(corpus=tamper)
        rules = self.rules(folder)
        self.assertIn('R117-4', rules)
        self.assertIn('R117-5', rules)

    def test_a_dropped_corpus_file_is_a_hard_stop(self):
        folder = self.tree()
        (folder / gate.CORPUS).unlink()
        with self.assertRaises(SystemExit):
            gate.violations(folder)


class TestDiscipline(Mutant):
    def test_a_loosened_tolerance_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('private val TOL = 1e-12', 'private val TOL = 1e-6'))
        self.assertEqual({'R117-6'}, self.rules(folder))

    def test_a_shrunken_suite_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('    @Test fun ', '    fun ', 3))
        self.assertEqual({'R117-6'}, self.rules(folder))

    def test_dropping_an_error_assertion_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('#N/A', 'unknown'))
        self.assertEqual({'R117-6'}, self.rules(folder))

    def test_a_helper_arity_mistake_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace(
            'shown("=PERCENTILE.INC(A1,A2,A2)", 1.0, 2.0)', 'shown("=PERCENTILE.INC(A1,A2,A2)", 1.0, 2.0, 3.0)'))
        self.assertEqual({'R117-6'}, self.rules(folder))

    def test_losing_the_corpus_resource_reference_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('/r117/percentile_corpus.tsv', '/r117/elsewhere.tsv'))
        self.assertEqual({'R117-6'}, self.rules(folder))


class NoRegression(Mutant):
    def test_changing_the_legacy_rank_formula_fires_rule_7(self):
        folder = self.tree(source=lambda text: text.replace(
            'val pos = if (exclusive) k * (sorted.size + 1) - 1.0 else k * (sorted.size - 1)',
            'val pos = k * (sorted.size - 1)'))
        self.assertEqual({'R117-7'}, self.rules(folder))

    def test_dropping_the_mode_kernel_fires_rule_7(self):
        folder = self.tree(source=lambda text: text.replace('        "MODE.SNGL" -> {', '        "MODE.SNGL2" -> {'))
        self.assertEqual({'R117-7'}, self.rules(folder))


# k -> (n, i): the cut point index statistics.quantiles returns for that rank.
CUT_POINTS = {0.05: (20, 1), 0.1: (10, 1), 0.25: (4, 1), 0.3: (10, 3), 0.5: (4, 2),
              0.7: (10, 7), 0.75: (4, 3), 0.9: (10, 9), 0.95: (20, 19)}


def oracle_percentile(data: list[float], k: float, exclusive: bool) -> float:
    ordered = sorted(data)
    if k == 0.0:
        return ordered[0]
    if k == 1.0:
        return ordered[-1]
    n, i = CUT_POINTS[k]
    return statistics.quantiles(data, n=n, method='exclusive' if exclusive else 'inclusive')[i - 1]


def oracle_a(name: str, tokens: list[str]) -> float:
    values: list[float] = []
    for token in tokens:
        if token == 'EMPTY':
            continue
        if token == 'TRUE':
            values.append(1.0)
        elif token == 'FALSE':
            values.append(0.0)
        elif token.startswith('"'):
            values.append(0.0)
        else:
            values.append(float(token))
    if name == 'AVERAGEA':
        return sum(values) / len(values)
    if name == 'MAXA':
        return max(values) if values else 0.0
    if name == 'MINA':
        return min(values) if values else 0.0
    mean = sum(values) / len(values)
    divisor = len(values) - 1 if name in ('VARA', 'STDEVA') else len(values)
    var = sum((v - mean) ** 2 for v in values) / divisor
    return math.sqrt(var) if name.startswith('STDEV') else var


class Repository(unittest.TestCase):
    def test_this_repository_is_silent_and_the_summary_is_stated(self):
        found, summary = gate.violations(ROOT)
        self.assertEqual([], found, '\n'.join(found))
        self.assertEqual(13, summary['names'])
        self.assertEqual(776, summary['rows'])
        self.assertEqual(156, summary['error_rows'])
        self.assertEqual(15, summary['tests'])
        self.assertEqual(1e-12, summary['tolerance'])

    def test_the_corpus_reproduces_from_its_generator(self):
        self.assertEqual(CORPUS.read_text(encoding='utf-8'), generator.render())

    def test_every_percentile_value_is_the_statistics_library_value(self):
        # An independent recomputation of the numeric percentile column through statistics.quantiles,
        # not through the generator's writer. Refusal rows are the spreadsheet-specific part and are
        # asserted by the corpus itself; statistics.quantiles extrapolates where Excel refuses.
        checked = 0
        for line in CORPUS.read_text(encoding='utf-8').splitlines()[1:]:
            function, data, k, expected = line.split('\t')
            if function not in gate.PERCENTILE_NAMES or expected.startswith('#'):
                continue
            values = [float(token) for token in data.split(',')]
            if len(values) < 2:
                continue
            rank = float(k)
            if function.startswith('QUARTILE'):
                rank = float(k) * 0.25
            oracle = oracle_percentile(values, rank, function.endswith('.EXC'))
            self.assertAlmostEqual(oracle, float(expected), delta=1e-9 * max(1.0, abs(oracle)),
                                   msg=f'{function}({data}, {k}) is not the statistics value')
            checked += 1
        self.assertGreater(checked, 400)

    def test_every_a_family_value_is_the_counting_rules_value(self):
        checked = 0
        for line in CORPUS.read_text(encoding='utf-8').splitlines()[1:]:
            function, data, _k, expected = line.split('\t')
            if function not in gate.A_NAMES or expected.startswith('#'):
                continue
            tokens = data.split(',')
            if function in ('VARA', 'STDEVA'):
                counted = [t for t in tokens if t != 'EMPTY']
                if len(counted) < 2:
                    continue
            oracle = oracle_a(function, tokens)
            self.assertAlmostEqual(oracle, float(expected), delta=1e-12 * max(1.0, abs(oracle)),
                                   msg=f'{function}({data}) is not the counting-rules value')
            checked += 1
        self.assertGreater(checked, 100)


if __name__ == '__main__':
    unittest.main()
