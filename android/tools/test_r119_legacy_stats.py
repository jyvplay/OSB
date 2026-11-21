#!/usr/bin/env python3
"""Controls for the R119 legacy-statistics gate.

Every control mutates exactly one property of a four-file tree (`FormulaEval.kt`, `SpillSheetEngine.kt`, the Kotlin
test, the corpus) and requires the *named* rule to fire. The point is falsifiability: a gate that cannot be made to
fail is a comment.

Two repository controls are included as well — the real tree must be silent, and the corpus must reproduce from its
generator byte for byte — because a fixture that drifted from its generator is the failure mode that would let the
Kotlin test keep passing against stale numbers.

The independent-oracle control recomputes every numeric row through CPython's `statistics` module (`variance`,
`pvariance`, `pstdev`, `linear_regression`) and a separate transcription of the pairing and mode rules, neither of
which goes through the generator's writer.
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
gate = importlib.import_module('check_r119_legacy_stats')
generator = importlib.import_module('gen_r119_legacy_stats_corpus')

SOURCE = ROOT / gate.SOURCE
SPILL = ROOT / gate.SPILL
TEST = ROOT / gate.TEST
CORPUS = ROOT / gate.CORPUS


class Mutant(unittest.TestCase):
    def tree(self, source=None, spill=None, test=None, corpus=None) -> Path:
        folder = Path(tempfile.mkdtemp(prefix='r119-'))
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
        folder = self.tree(source=lambda text: text.replace('"VAR" -> evalEssential("VAR.S", args, env)', '"VARX" -> evalEssential("VAR.S", args, env)'))
        self.assertEqual({'R119-1'}, self.rules(folder))

    def test_a_second_dispatch_call_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace(
            '            else -> {\n                val lexicalClosure',
            '            "FORECAST" -> evalForecast(args, env)\n            else -> {\n                val lexicalClosure'))
        self.assertEqual({'R119-1'}, self.rules(folder))

    def test_a_missing_helper_definition_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace(
            'private fun evalForecast', 'private fun evalFcast'))
        self.assertEqual({'R119-1'}, self.rules(folder))

    def test_a_missing_export_prefix_fires_rule_1(self):
        folder = self.tree(spill=lambda text: text.replace('"FORECAST.LINEAR",\n', ''))
        self.assertEqual({'R119-1'}, self.rules(folder))


class Corpus(Mutant):
    def test_a_function_with_no_rows_fires_rule_2(self):
        folder = self.tree(corpus=lambda text: '\n'.join(
            line for line in text.splitlines() if not line.startswith('COVARIANCE\t')) + '\n')
        rules = self.rules(folder)
        self.assertIn('R119-2', rules)
        self.assertIn('R119-5', rules)

    def test_a_thin_function_fires_rule_2(self):
        kept = [line for line in CORPUS.read_text().splitlines() if line.startswith('VAR\t')][:2]
        folder = self.tree(corpus=lambda text: '\n'.join(
            [line for line in text.splitlines() if not line.startswith('VAR\t')] + kept) + '\n')
        self.assertIn('R119-2', self.rules(folder))

    def test_mode_without_refusals_fires_rule_2(self):
        folder = self.tree(corpus=lambda text: '\n'.join(
            line for line in text.splitlines()
            if not (line.startswith('MODE\t') and line.split('\t')[4] == '#N/A')) + '\n')
        self.assertIn('R119-2', self.rules(folder))

    def test_forecast_without_refusals_fires_rule_2(self):
        folder = self.tree(corpus=lambda text: '\n'.join(
            line for line in text.splitlines()
            if not (line.startswith('FORECAST') and line.split('\t')[4] == '#DIV/0!')) + '\n')
        self.assertIn('R119-2', self.rules(folder))

    def test_a_duplicated_row_fires_rule_3(self):
        first = CORPUS.read_text().splitlines()[1]
        folder = self.tree(corpus=lambda text: text + first + '\n')
        self.assertEqual({'R119-3', 'R119-5'}, self.rules(folder))

    def test_an_unregistered_function_row_fires_rule_3(self):
        folder = self.tree(corpus=lambda text: text + 'FOO\t1,2\t\t\t1.5\n')
        self.assertEqual({'R119-3', 'R119-5'}, self.rules(folder))

    def test_a_non_literal_error_fires_rule_3(self):
        folder = self.tree(corpus=lambda text: text.replace('#DIV/0!', '#SPILL!'))
        rules = self.rules(folder)
        self.assertIn('R119-3', rules)
        self.assertIn('R119-5', rules)

    def test_a_tampered_value_breaks_the_identities_and_the_generator(self):
        def tamper(text: str) -> str:
            lines = text.splitlines()
            for index, line in enumerate(lines):
                if line.startswith('VAR\t'):
                    parts = line.split('\t')
                    parts[4] = '987654.321'
                    lines[index] = '\t'.join(parts)
                    break
            return '\n'.join(lines) + '\n'

        folder = self.tree(corpus=tamper)
        rules = self.rules(folder)
        self.assertIn('R119-4', rules)
        self.assertIn('R119-5', rules)

    def test_a_dropped_corpus_file_is_a_hard_stop(self):
        folder = self.tree()
        (folder / gate.CORPUS).unlink()
        with self.assertRaises(SystemExit):
            gate.violations(folder)


class TestDiscipline(Mutant):
    def test_a_loosened_tolerance_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('private val TOL = 1e-12', 'private val TOL = 1e-6'))
        self.assertEqual({'R119-6'}, self.rules(folder))

    def test_a_shrunken_suite_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('    @Test fun ', '    fun ', 4))
        self.assertEqual({'R119-6'}, self.rules(folder))

    def test_dropping_an_error_assertion_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('#N/A', 'unknown'))
        self.assertEqual({'R119-6'}, self.rules(folder))

    def test_a_helper_arity_mistake_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace(
            'shown("=MODE(1, 2, 3, 4)")', 'shown("=MODE(1, 2, 3, 4)", 1.0, 2.0, 3.0)'))
        self.assertEqual({'R119-6'}, self.rules(folder))

    def test_losing_the_corpus_resource_reference_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('/r119/legacy_stats_corpus.tsv', '/r119/elsewhere.tsv'))
        self.assertEqual({'R119-6'}, self.rules(folder))


class NoRegression(Mutant):
    def test_changing_the_mode_kernel_fires_rule_7(self):
        folder = self.tree(source=lambda text: text.replace('        "MODE.SNGL" -> {', '        "MODE.SNGL2" -> {'))
        self.assertEqual({'R119-7'}, self.rules(folder))

    def test_dropping_the_stdev_kernel_fires_rule_7(self):
        folder = self.tree(source=lambda text: text.replace(
            '"STDEV.S", "STDEV.P", "VAR.S", "VAR.P" -> {', '"STDEV.S", "STDEV.P" -> {'))
        self.assertEqual({'R119-7'}, self.rules(folder))


def oracle_numbers(tokens: list[str]) -> list[float]:
    return [float(t) for t in tokens if t not in ('EMPTY', 'TRUE', 'FALSE', '#N/A') and not t.startswith('"')]


def oracle_paired(a: str, b: str) -> tuple[list[float], list[float]]:
    ys: list[float] = []
    xs: list[float] = []
    for y, x in zip(a.split(','), b.split(',')):
        if y in ('EMPTY', 'TRUE', 'FALSE', '#N/A') or x in ('EMPTY', 'TRUE', 'FALSE', '#N/A'):
            continue
        if y.startswith('"') or x.startswith('"'):
            continue
        ys.append(float(y))
        xs.append(float(x))
    return ys, xs


def oracle_mode(values: list[float]) -> float:
    counts: dict[float, int] = {}
    for value in values:
        counts[value] = counts.get(value, 0) + 1
    best_value: float | None = None
    best_count = 1
    for value, count in counts.items():
        if count > best_count:
            best_value, best_count = value, count
    assert best_value is not None
    return best_value


class Repository(unittest.TestCase):
    def test_this_repository_is_silent_and_the_summary_is_stated(self):
        found, summary = gate.violations(ROOT)
        self.assertEqual([], found, '\n'.join(found))
        self.assertEqual(7, summary['names'])
        self.assertEqual(212, summary['rows'])
        self.assertEqual(50, summary['error_rows'])
        self.assertEqual(17, summary['tests'])
        self.assertEqual(1e-12, summary['tolerance'])

    def test_the_corpus_reproduces_from_its_generator(self):
        self.assertEqual(CORPUS.read_text(encoding='utf-8'), generator.render())

    def test_the_generator_and_the_oracle_are_independent_implementations(self):
        # The modal shortcut is a fixture generated by the code under test. Mutate the generator's counting rules
        # (parse numeric-looking text as a number, the D.6 shortcut) and require its render to move away from the
        # committed corpus: if it did not move, the fixture would be a transcript of one implementation.
        original = generator.numeric_values

        def mutant(tokens):
            out = []
            for token in tokens:
                if token.startswith('"'):
                    try:
                        out.append(float(token.strip('"')))
                        continue
                    except ValueError:
                        pass
                value = original([token])
                if isinstance(value, list):
                    out.extend(value)
                else:
                    return value
            return out

        generator.numeric_values = mutant
        try:
            self.assertNotEqual(CORPUS.read_text(encoding='utf-8'), generator.render())
        finally:
            generator.numeric_values = original

    def test_every_value_is_the_statistics_library_value(self):
        # An independent recomputation of the numeric column through the statistics module, not through the
        # generator's writer. Refusal rows are the spreadsheet-specific part and are asserted by the corpus itself.
        checked = 0
        for line in CORPUS.read_text(encoding='utf-8').splitlines()[1:]:
            function, a, b, c, expected = line.split('\t')
            if expected.startswith('#'):
                continue
            want = float(expected)
            if function == 'VAR':
                values = oracle_numbers(a.split(','))
                if len(values) < 2:
                    continue
                oracle = statistics.variance(values)
            elif function == 'VARP':
                oracle = statistics.pvariance(oracle_numbers(a.split(',')))
            elif function == 'STDEVP':
                oracle = statistics.pstdev(oracle_numbers(a.split(',')))
            elif function == 'MODE':
                oracle = oracle_mode(oracle_numbers(a.split(',')))
            elif function == 'COVARIANCE':
                ys, xs = oracle_paired(a, b)
                if len(xs) < 2:
                    continue
                oracle = statistics.covariance(xs, ys) * (len(xs) - 1) / len(xs)
            else:
                ys, xs = oracle_paired(a, b)
                if len(xs) < 2 or len(set(xs)) < 2:
                    continue
                fit = statistics.linear_regression(xs, ys)
                oracle = fit.intercept + fit.slope * float(c)
            self.assertAlmostEqual(oracle, want, delta=1e-9 * max(1.0, abs(oracle)),
                                   msg=f'{function}({a}, {b}, {c}) is not the statistics value')
            checked += 1
        self.assertGreater(checked, 100)

    def test_every_mode_value_is_a_most_frequent_value(self):
        checked = 0
        for line in CORPUS.read_text(encoding='utf-8').splitlines()[1:]:
            function, a, _b, _c, expected = line.split('\t')
            if function != 'MODE' or expected.startswith('#'):
                continue
            values = oracle_numbers(a.split(','))
            oracle = oracle_mode(values)
            self.assertAlmostEqual(oracle, float(expected), delta=1e-12,
                                   msg=f'MODE({a}) is not the first most-frequent value')
            checked += 1
        self.assertGreater(checked, 10)


if __name__ == '__main__':
    unittest.main()
