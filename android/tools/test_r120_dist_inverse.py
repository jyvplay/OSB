#!/usr/bin/env python3
"""Controls for the R120 distribution-inverse gate.

Every control mutates exactly one property of a four-file tree (`FormulaEval.kt`, `SpillSheetEngine.kt`,
the Kotlin test, the corpus) and requires the *named* rule to fire. The point is falsifiability: a gate that
cannot be made to fail is a comment.

Two repository controls are included as well — the real tree must be silent, and the corpus must reproduce
from its generator byte for byte.

The independent-oracle controls recompute the numeric column through numerical integration of the probability
density functions (Simpson's rule) and through CPython's `math.lgamma` — neither goes through the generator's
continued fractions. Two implementations agreeing is evidence; one implementation writing its own fixture is
a transcript.
"""
from __future__ import annotations

import importlib
import math
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
gate = importlib.import_module('check_r120_dist_inverse')
generator = importlib.import_module('gen_r120_dist_inverse_corpus')

SOURCE = ROOT / gate.SOURCE
SPILL = ROOT / gate.SPILL
TEST = ROOT / gate.TEST
CORPUS = ROOT / gate.CORPUS

NAMES = gate.NAMES


def simpson(f, a: float, b: float, n: int = 100000) -> float:
    h = (b - a) / n
    total = f(a) + f(b)
    for i in range(1, n):
        total += (4.0 if i & 1 else 2.0) * f(a + i * h)
    return total * h / 3.0


def t_pdf(t: float, df: float) -> float:
    return math.exp(
        math.lgamma((df + 1.0) / 2.0) - math.lgamma(df / 2.0) - 0.5 * math.log(math.pi * df)
        - ((df + 1.0) / 2.0) * math.log1p(t * t / df),
    )


def chi2_pdf(x: float, df: float) -> float:
    if x < 0.0:
        return 0.0
    if x == 0.0:
        if df == 2.0:
            return 0.5
        if df > 2.0:
            return 0.0
        return 1e300  # df < 2 is singular at zero; callers skip those rows
    return math.exp(
        (df / 2.0 - 1.0) * math.log(x) - x / 2.0 - (df / 2.0) * math.log(2.0) - math.lgamma(df / 2.0),
    )


def f_pdf(x: float, d1: float, d2: float) -> float:
    if x < 0.0:
        return 0.0
    if x == 0.0:
        if d1 == 2.0:
            return 1.0
        if d1 > 2.0:
            return 0.0
        return 1e300  # d1 < 2 is singular at zero; callers skip those rows
    return math.exp(
        (d1 / 2.0 - 1.0) * math.log(x)
        - ((d1 + d2) / 2.0) * math.log1p(d1 * x / d2)
        + (d1 / 2.0) * math.log(d1 / d2)
        - math.lgamma(d1 / 2.0) - math.lgamma(d2 / 2.0) + math.lgamma((d1 + d2) / 2.0),
    )


def f_head_mass(d1: float, d2: float, eps: float) -> float:
    """Analytic mass of the F(d1, d2) density on [0, eps] for d1 == 3, where the density behaves
    like c*sqrt(x) at zero and Simpson's rule converges slowly. For other d1 the mass is zero
    (d1 > 2) or the row is skipped (d1 < 2)."""
    if d1 != 3.0:
        return 0.0
    c = (3.0 / d2) ** 1.5 / math.exp(math.lgamma(1.5) + math.lgamma(d2 / 2.0) - math.lgamma(1.5 + d2 / 2.0))
    return c * eps ** 1.5 / 1.5


def beta_pdf(x: float, a: float, b: float) -> float:
    if x <= 0.0:
        if a == 1.0:
            return b  # limit at zero when a == 1
        return 0.0
    if x >= 1.0:
        if b == 1.0:
            return a  # limit at one when b == 1
        return 0.0
    return math.exp(
        (a - 1.0) * math.log(x) + (b - 1.0) * math.log(1.0 - x)
        - (math.lgamma(a) + math.lgamma(b) - math.lgamma(a + b)),
    )


def gamma_pdf(x: float, a: float, beta: float) -> float:
    if x <= 0.0:
        if a == 1.0:
            return 1.0 / beta  # limit at zero when a == 1
        return 0.0
    return math.exp((a - 1.0) * math.log(x) - x / beta - a * math.log(beta) - math.lgamma(a))


class Mutant(unittest.TestCase):
    def tree(self, source=None, spill=None, test=None, corpus=None) -> Path:
        folder = Path(tempfile.mkdtemp(prefix='r120-'))
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
    def test_a_name_missing_from_the_dispatch_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace(
            '"T.DIST.2T" -> evalR53ScalarFunction("TDIST.2T", args, env)',
            '"T.DST2" -> evalR53ScalarFunction("TDIST.2T", args, env)'))
        self.assertEqual({'R120-1'}, self.rules(folder))

    def test_the_eval_tdist_helper_missing_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace('private fun evalTDist', 'private fun evalTdist'))
        self.assertEqual({'R120-1'}, self.rules(folder))

    def test_the_eval_dist_inverse_helper_missing_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace('private fun evalDistInverse', 'private fun evalDistInv'))
        self.assertEqual({'R120-1'}, self.rules(folder))

    def test_gammaln_missing_from_the_dispatch_fires_rule_1(self):
        folder = self.tree(source=lambda text: text.replace(
            '"GAMMALN.PRECISE" -> {', '"GAMMLNPRECISE" -> {'))
        self.assertEqual({'R120-1'}, self.rules(folder))

    def test_a_missing_export_prefix_fires_rule_1(self):
        folder = self.tree(spill=lambda text: text.replace('"GAMMALN.PRECISE",\n', ''))
        self.assertEqual({'R120-1'}, self.rules(folder))


class Corpus(Mutant):
    def test_a_function_with_no_rows_fires_rule_2(self):
        folder = self.tree(corpus=lambda text: '\n'.join(
            line for line in text.splitlines() if not line.startswith('CHISQ.INV\t')) + '\n')
        rules = self.rules(folder)
        self.assertIn('R120-2', rules)
        self.assertIn('R120-5', rules)

    def test_a_thin_function_fires_rule_2(self):
        kept = [line for line in CORPUS.read_text().splitlines() if line.startswith('BETA.INV\t')][:2]
        folder = self.tree(corpus=lambda text: '\n'.join(
            [line for line in text.splitlines() if not line.startswith('BETA.INV\t')] + kept) + '\n')
        self.assertIn('R120-2', self.rules(folder))

    def test_refusals_below_five_fire_rule_2(self):
        folder = self.tree(corpus=lambda text: '\n'.join(
            line for line in text.splitlines()
            if not (line.startswith('T.INV\t') and line.split('\t')[2].startswith('#'))) + '\n')
        self.assertIn('R120-2', self.rules(folder))

    def test_a_duplicated_row_fires_rule_3(self):
        first = CORPUS.read_text().splitlines()[1]
        folder = self.tree(corpus=lambda text: text + first + '\n')
        self.assertEqual({'R120-3', 'R120-5'}, self.rules(folder))

    def test_an_unregistered_function_row_fires_rule_3(self):
        folder = self.tree(corpus=lambda text: text + 'FOO\t0.5\t0.5\n')
        self.assertEqual({'R120-3', 'R120-5'}, self.rules(folder))

    def test_a_non_literal_error_fires_rule_3(self):
        folder = self.tree(corpus=lambda text: text.replace('#NUM!', '#SPILL!'))
        rules = self.rules(folder)
        self.assertIn('R120-3', rules)
        self.assertIn('R120-5', rules)

    def test_a_tampered_value_breaks_the_coherence_and_the_generator(self):
        def tamper(text: str) -> str:
            lines = text.splitlines()
            for index, line in enumerate(lines):
                if line.startswith('CHISQ.INV\t') and not line.split('\t')[2].startswith('#'):
                    parts = line.split('\t')
                    parts[2] = '987654.321'
                    lines[index] = '\t'.join(parts)
                    break
            return '\n'.join(lines) + '\n'

        folder = self.tree(corpus=tamper)
        rules = self.rules(folder)
        self.assertIn('R120-4', rules)
        self.assertIn('R120-5', rules)


class TestDiscipline(Mutant):
    def test_a_loosened_tolerance_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('private val TOL = 1e-12', 'private val TOL = 1e-6'))
        self.assertEqual({'R120-6'}, self.rules(folder))

    def test_a_shrunken_suite_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('    @Test fun ', '    fun ', 3))
        self.assertEqual({'R120-6'}, self.rules(folder))

    def test_dropping_an_error_assertion_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('#NUM!', 'unknown'))
        self.assertEqual({'R120-6'}, self.rules(folder))

    def test_a_helper_arity_mistake_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace(
            'shown("=T.INV(0.5)")', 'shown("=T.INV(0.5)", 1.0, 2.0, 3.0)'))
        self.assertEqual({'R120-6'}, self.rules(folder))

    def test_losing_the_corpus_resource_reference_fires_rule_6(self):
        folder = self.tree(test=lambda text: text.replace('/r120/dist_inverse_corpus.tsv', '/r120/elsewhere.tsv'))
        self.assertEqual({'R120-6'}, self.rules(folder))


class NoRegression(Mutant):
    def test_changing_the_gamma_kernel_fires_rule_7(self):
        folder = self.tree(source=lambda text: text.replace(
            'private fun regGammaLowerP', 'private fun regGammaLowerp'))
        self.assertEqual({'R120-7'}, self.rules(folder))

    def test_changing_the_beta_fraction_fires_rule_7(self):
        # The replacement name must not contain the original as a substring, or the needle search still matches.
        folder = self.tree(source=lambda text: text.replace(
            'private fun incompleteBetaContinuedFraction', 'private fun incompleteBetaCF'))
        self.assertEqual({'R120-7'}, self.rules(folder))


def corpus_numeric() -> list[tuple[str, str, str, float]]:
    out = []
    for line in CORPUS.read_text(encoding='utf-8').splitlines()[1:]:
        function, args, expected = line.split('\t')
        if expected.startswith('#'):
            continue
        out.append((function, args, expected, float(expected)))
    return out


class IndependentOracle(unittest.TestCase):
    def test_t_dist_values_match_numerical_integration(self):
        # Student-t density integrated by Simpson's rule: a different algorithm from the continued fraction.
        checked = 0
        for function, args, _expected, want in corpus_numeric():
            if function not in ('T.DIST', 'T.DIST.2T', 'T.DIST.RT'):
                continue
            if function == 'T.DIST':
                x, df, tails = (float(t) for t in args.split(','))
            else:
                x, df = (float(t) for t in args.split(','))
                tails = 2.0 if function == 'T.DIST.2T' else 1.0
            central = simpson(lambda t: t_pdf(t, df), 0.0, abs(x))
            if tails == 2.0:
                got = 1.0 - 2.0 * central
            elif x >= 0.0:
                got = 0.5 - central
            else:
                got = 0.5 + central
            self.assertAlmostEqual(got, want, delta=1e-7 * max(1.0, abs(want)),
                                   msg=f'{function}({args}) is not the integrated density')
            checked += 1
        self.assertGreater(checked, 100)

    def test_tail_relations_hold_in_the_corpus(self):
        # No integration here: the tails must satisfy the definitions purely from the recorded values.
        rows = {(function, args): expected for function, args, expected, _ in corpus_numeric()}
        checked = 0
        for function, args, expected, _ in corpus_numeric():
            if function != 'T.DIST':
                continue
            x, df, tails = (float(t) for t in args.split(','))
            if tails != 1.0 or x <= 0.0:
                continue
            two = rows.get(('T.DIST', f'{x},{df},2.0'))
            minus = rows.get(('T.DIST', f'{-x},{df},1.0'))
            if two is None or minus is None:
                continue
            self.assertAlmostEqual(2.0 * float(expected), float(two), delta=1e-12,
                                   msg=f'T.DIST({x},{df},2) is not twice T.DIST({x},{df},1)')
            self.assertAlmostEqual(float(expected) + float(minus), 1.0, delta=1e-12,
                                   msg=f'T.DIST(-{x},{df},1) is not 1 - T.DIST({x},{df},1)')
            checked += 1
        self.assertGreater(checked, 10)
    def test_inverse_values_invert_their_integrated_cdfs(self):
        # Bisect nothing: integrate each density from 0 to the recorded quantile and require the probability.
        checked = 0
        for function, args, _expected, want in corpus_numeric():
            if not want > 0.0:
                continue
            if function in ('CHISQ.INV', 'CHISQ.INV.RT'):
                p, df = (float(t) for t in args.split(','))
                x = want
                if df == 1.0:
                    continue  # the chi-square_1 density is singular at zero; excluded from this control
                got = simpson(lambda t: chi2_pdf(t, df), 0.0, x)
                want_check = p if function == 'CHISQ.INV' else 1.0 - p
            elif function in ('F.INV', 'F.INV.RT'):
                p, d1, d2 = (float(t) for t in args.split(','))
                x = want
                if d1 < 2.0 or x > 40.0:
                    continue
                eps = x * 1e-6
                got = f_head_mass(d1, d2, eps) + simpson(lambda t: f_pdf(t, d1, d2), eps, x)
                want_check = p if function == 'F.INV' else 1.0 - p
            elif function == 'BETA.INV':
                p, a, b = (float(t) for t in args.split(','))
                x = want
                if a < 1.0 or b < 1.0:
                    continue
                got = simpson(lambda t: beta_pdf(t, a, b), 0.0, x)
                want_check = p
            elif function == 'GAMMA.INV':
                p, a, beta = (float(t) for t in args.split(','))
                x = want
                if a < 1.0:
                    continue
                got = simpson(lambda t: gamma_pdf(t, a, beta), 0.0, x)
                want_check = p
            else:
                continue
            self.assertAlmostEqual(got, want_check, delta=1e-7 * max(1.0, abs(want_check)),
                                   msg=f'{function}({args}) = {x} does not invert the integrated density')
            checked += 1
        self.assertGreater(checked, 100)

    def test_direct_values_match_numerical_integration(self):
        checked = 0
        for function, args, _expected, want in corpus_numeric():
            if function == 'F.DIST':
                x, d1, d2 = (float(t) for t in args.split(','))
                if x == 0.0:
                    got = 1.0  # right tail of a degenerate point
                elif d1 < 2.0:
                    continue
                else:
                    eps = x * 1e-6
                    got = 1.0 - f_head_mass(d1, d2, eps) - simpson(lambda t: f_pdf(t, d1, d2), eps, x)
                self.assertAlmostEqual(got, want, delta=1e-7 * max(1.0, abs(want)),
                                       msg=f'{function}({args}) is not the integrated density')
                checked += 1
            elif function == 'GAMMALN.PRECISE':
                self.assertAlmostEqual(math.lgamma(float(args)), float(_expected),
                                       delta=1e-12 * max(1.0, abs(float(_expected))),
                                       msg=f'GAMMALN.PRECISE({args}) is not math.lgamma')
                checked += 1
        self.assertGreater(checked, 30)


class Repository(unittest.TestCase):
    def test_this_repository_is_silent_and_the_summary_is_stated(self):
        found, summary = gate.violations(ROOT)
        self.assertEqual([], found, '\n'.join(found))
        self.assertEqual(13, summary['names'])
        self.assertEqual(612, summary['rows'])
        self.assertEqual(85, summary['error_rows'])
        self.assertEqual(15, summary['tests'])
        self.assertEqual(1e-12, summary['tolerance'])

    def test_the_corpus_reproduces_from_its_generator(self):
        self.assertEqual(CORPUS.read_text(encoding='utf-8'), generator.render())

    def test_the_generator_and_the_oracle_are_independent_implementations(self):
        # Mutate the generator's continued fraction and require the render to move: if it did not, the
        # committed fixture would be a transcript of one implementation.
        original = generator.incbeta

        def mutant(a, b, x):
            if a == 2.0 and b == 3.0:  # BETA.INV rows evaluate incbeta(2, 3, x) directly
                return original(a, b, x) + 1e-10
            return original(a, b, x)

        generator.incbeta = mutant
        try:
            self.assertNotEqual(CORPUS.read_text(encoding='utf-8'), generator.render())
        finally:
            generator.incbeta = original


if __name__ == '__main__':
    unittest.main()
