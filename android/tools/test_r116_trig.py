#!/usr/bin/env python3
"""Controls for the R116 trigonometry gate.

Every control mutates exactly one property of a three-file tree (`FormulaEval.kt`, the Kotlin test, the corpus) and
requires the *named* rule to fire. The point is falsifiability: a gate that cannot be made to fail is a comment.

Two repository controls are included as well — the real tree must be silent, and the corpus must reproduce from its
generator byte for byte — because a fixture that drifted from its generator is the failure mode that would let the
Kotlin test keep passing against stale numbers.
"""
from __future__ import annotations

import importlib
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
gate = importlib.import_module('check_r116_trig')
generator = importlib.import_module('gen_r116_trig_corpus')

SOURCE = ROOT / gate.SOURCE
TEST = ROOT / gate.TEST
CORPUS = ROOT / gate.CORPUS


class Mutant(unittest.TestCase):
    def tree(self, source=None, test=None, corpus=None) -> Path:
        folder = Path(tempfile.mkdtemp(prefix='r116-'))
        self.addCleanup(lambda: __import__('shutil').rmtree(folder, ignore_errors=True))
        for relative, base, replacement in (
            (gate.SOURCE, SOURCE, source),
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
        folder = self.tree(source=lambda text: text.replace('"SIN", "COS", "TAN",', '"SIN", "TAN",'))
        self.assertEqual({'R116-1'}, self.rules(folder))

    def test_a_name_missing_its_implementation_branch_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace('            "TAN" -> kotlin.math.tan(x)\n', ''))
        self.assertEqual({'R116-1'}, self.rules(folder))

    def test_a_duplicated_branch_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace(
            '            "ATAN" -> kotlin.math.atan(x)\n',
            '            "ATAN" -> kotlin.math.atan(x)\n            "ATAN" -> kotlin.math.atan(x)\n'))
        self.assertEqual({'R116-1'}, self.rules(folder))

    def test_a_second_dispatch_call_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace(
            '            else -> {\n                val lexicalClosure',
            '            "SIN" -> evalR116TrigFunction(name, args, env)\n            else -> {\n                val lexicalClosure'))
        self.assertEqual({'R116-1'}, self.rules(folder))


class Corpus(Mutant):
    def test_a_function_with_no_rows_fires_rule_2(self):
        folder = self.tree(corpus=lambda text: '\n'.join(
            line for line in text.splitlines() if not line.startswith('ASINH\t')) + '\n')
        rules = self.rules(folder)
        self.assertIn('R116-2', rules)
        self.assertIn('R116-5', rules)

    def test_a_thin_function_fires_rule_2(self):
        kept = [line for line in CORPUS.read_text().splitlines() if line.startswith('COSH\t')][:2]
        folder = self.tree(corpus=lambda text: '\n'.join(
            [line for line in text.splitlines() if not line.startswith('COSH\t')] + kept) + '\n')
        self.assertIn('R116-2', self.rules(folder))

    def test_a_duplicated_row_fires_rule_3(self):
        first = CORPUS.read_text().splitlines()[1]
        folder = self.tree(corpus=lambda text: text + first + '\n')
        self.assertEqual({'R116-3', 'R116-5'}, self.rules(folder))

    def test_an_unregistered_function_row_fires_rule_3(self):
        folder = self.tree(corpus=lambda text: text + 'FOO\t1\t\t1.0\n')
        self.assertEqual({'R116-3', 'R116-5'}, self.rules(folder))

    def test_a_non_literal_error_fires_rule_3(self):
        folder = self.tree(corpus=lambda text: text.replace('#NUM!', '#SPILL!'))
        self.assertEqual({'R116-3', 'R116-5'}, self.rules(folder))

    def test_a_tampered_value_breaks_the_identities_and_the_generator(self):
        def tamper(text: str) -> str:
            lines = text.splitlines()
            for index, line in enumerate(lines):
                if line.startswith('SIN\t'):
                    parts = line.split('\t')
                    parts[3] = '0.987654321'
                    lines[index] = '\t'.join(parts)
                    break
            return '\n'.join(lines) + '\n'

        folder = self.tree(corpus=tamper)
        rules = self.rules(folder)
        self.assertIn('R116-4', rules)
        self.assertIn('R116-5', rules)

    def test_a_tampered_pi_row_fires_rule_4(self):
        folder = self.tree(corpus=lambda text: '\n'.join(
            'PI\t\t\t3.0' if line.startswith('PI\t') else line for line in text.splitlines()) + '\n')
        self.assertIn('R116-4', self.rules(folder))

    def test_a_dropped_corpus_file_is_a_hard_stop(self):
        folder = self.tree()
        (folder / gate.CORPUS).unlink()
        with self.assertRaises(SystemExit):
            gate.violations(folder)


class TestDiscipline(Mutant):
    def test_a_loosened_tolerance_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('private val TOL = 1e-12', 'private val TOL = 1e-6'))
        self.assertEqual({'R116-6'}, self.rules(folder))

    def test_a_shrunken_suite_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('    @Test fun ', '    fun ', 3))
        self.assertEqual({'R116-6'}, self.rules(folder))

    def test_dropping_an_error_assertion_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('#NAME?', 'unknown'))
        self.assertEqual({'R116-6'}, self.rules(folder))

    def test_losing_the_corpus_resource_reference_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('/r116/trig_corpus.tsv', '/r116/elsewhere.tsv'))
        self.assertEqual({'R116-6'}, self.rules(folder))


class NoRegression(Mutant):
    def test_changing_the_acot_definition_fires_rule_7(self):
        folder = self.tree(source=lambda text: text.replace(
            '"ACOT" -> (Math.PI / 2.0) - kotlin.math.atan(x)', '"ACOT" -> kotlin.math.atan(1.0 / x)'))
        self.assertEqual({'R116-7'}, self.rules(folder))

    def test_dropping_the_reciprocal_branch_fires_rule_7(self):
        folder = self.tree(source=lambda text: text.replace(
            '"SEC", "CSC", "COT", "SECH", "CSCH", "COTH", "ACOT", "ACOTH",', '"SEC", "CSC", "COT",'))
        self.assertEqual({'R116-7'}, self.rules(folder))


class Repository(unittest.TestCase):
    def test_this_repository_is_silent_and_the_summary_is_stated(self):
        found, summary = gate.violations(ROOT)
        self.assertEqual([], found, '\n'.join(found))
        self.assertEqual(14, summary['names'])
        self.assertEqual(534, summary['rows'])
        self.assertEqual(10, summary['error_rows'])
        self.assertEqual(15, summary['tests'])
        self.assertEqual(1e-12, summary['tolerance'])

    def test_the_corpus_reproduces_from_its_generator(self):
        self.assertEqual(CORPUS.read_text(encoding='utf-8'), generator.render())

    def test_every_expected_value_is_the_platform_libm_value(self):
        # An independent recomputation of the numeric column straight from `math`, not through the generator's writer.
        checked = 0
        for line in CORPUS.read_text(encoding='utf-8').splitlines()[1:]:
            function, x, y, expected = line.split('\t')
            if expected.startswith('#'):
                continue
            oracle = {
                'SIN': lambda: __import__('math').sin(float(x)),
                'COS': lambda: __import__('math').cos(float(x)),
                'TAN': lambda: __import__('math').tan(float(x)),
                'ASIN': lambda: __import__('math').asin(float(x)),
                'ACOS': lambda: __import__('math').acos(float(x)),
                'ATAN': lambda: __import__('math').atan(float(x)),
                'ATAN2': lambda: __import__('math').atan2(float(y), float(x)),
                'SINH': lambda: __import__('math').sinh(float(x)),
                'COSH': lambda: __import__('math').cosh(float(x)),
                'TANH': lambda: __import__('math').tanh(float(x)),
                'ASINH': lambda: __import__('math').asinh(float(x)),
                'ACOSH': lambda: __import__('math').acosh(float(x)),
                'ATANH': lambda: __import__('math').atanh(float(x)),
                'PI': lambda: __import__('math').pi,
            }[function]()
            self.assertEqual(oracle, float(expected), f'{function}({x}, {y}) is not the libm value')
            checked += 1
        self.assertEqual(524, checked)


if __name__ == '__main__':
    unittest.main()
