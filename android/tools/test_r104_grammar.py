#!/usr/bin/env python3
"""Controls for `check_r104_grammar.py`.

The independent lane is only worth something if the thing doing the independent deciding is itself tested. These
cases exercise the reference tokenizer, parser and evaluator directly — not through the workbook — so that a
mistake in the checker (precedence, a sign rule, a type rule, a bound) fails here instead of quietly agreeing with
an equally wrong Kotlin implementation.

The expectations are Excel's documented behaviour for the sixteen functions and the four-value type model the
product documents; where Excel's own answer is locale-, build- or collation-dependent the case asserts a refusal
instead, because "undecided" is the honest answer and a guess is not.
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_r104_grammar import Refused, decide, equal_values, ordering, tokenize  # noqa: E402

GRID = {
    (1, 1): ('num', 10.0),
    (1, 2): ('num', 3.5),
    (1, 3): ('txt', 'apple'),
    (1, 4): ('txt', 'Apple'),
    (1, 6): ('bool', True),
    (1, 8): ('num', -2.5),
    (1, 9): ('txt', '  padded   text '),
    (2, 1): ('num', 5.0),
    (3, 1): ('err', '#N/A'),
    (3, 2): ('txt', ''),
}
CELL = (2, 1)
ANCHOR = (2, 1)


def answer(formula: str, cell=CELL, anchor=ANCHOR):
    return decide(formula, GRID, cell, anchor)


def shape(formula: str, **kwargs) -> str:
    return answer(formula, **kwargs)[0]


class ArithmeticTests(unittest.TestCase):
    def test_precedence(self):
        self.assertEqual('TRUE', shape('1+2*3=7'))
        self.assertEqual('TRUE', shape('2*3+4=10'))
        self.assertEqual('TRUE', shape('(1+2)*3=9'))

    def test_left_associativity(self):
        self.assertEqual('TRUE', shape('8-3-2=3'))
        self.assertEqual('TRUE', shape('8/4/2=1'))

    def test_unary_minus(self):
        self.assertEqual('TRUE', shape('-2*-3=6'))
        self.assertEqual('TRUE', shape('--1=1'))
        self.assertEqual('TRUE', shape('+1=1'))

    def test_division_by_zero_is_refused(self):
        verdict, reason = answer('1/0>0')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('#DIV/0!', reason)


class RoundingTests(unittest.TestCase):
    def test_round_halves_away_from_zero(self):
        self.assertEqual('TRUE', shape('ROUND(2.5,0)=3'))
        self.assertEqual('TRUE', shape('ROUND(-2.5,0)=-3'))
        self.assertEqual('TRUE', shape('ROUND(0.5,0)=1'))

    def test_round_to_a_negative_place(self):
        self.assertEqual('TRUE', shape('ROUND(1234,-2)=1200'))
        self.assertEqual('TRUE', shape('ROUND(1250,-2)=1300'))

    def test_round_keeps_the_requested_scale(self):
        self.assertEqual('TRUE', shape('ROUND(2.346,2)=2.35'))
        self.assertEqual('TRUE', shape('ROUND(2.344,2)=2.34'))

    def test_round_reads_the_typed_decimal_not_the_binary_double(self):
        # 2.675 as a double is 2.67499999999999982236..., so half-away-from-zero on the double alone gives 2.67.
        # Excel rounds the fifteen significant decimal digits it keeps and answers 2.68; `r73_formula_cases.py`
        # pins the same expectation independently.
        self.assertEqual('TRUE', shape('ROUND(2.675,2)=2.68'))
        self.assertEqual('TRUE', shape('ROUND(-2.675,2)=-2.68'))
        self.assertEqual('TRUE', shape('ROUND(1.005,2)=1.01'))


class ModTests(unittest.TestCase):
    def test_sign_follows_the_divisor(self):
        self.assertEqual('TRUE', shape('MOD(-7,3)=2'))
        self.assertEqual('TRUE', shape('MOD(7,-3)=-2'))
        self.assertEqual('TRUE', shape('MOD(7,3)=1'))

    def test_zero_divisor_is_an_error_not_a_guess(self):
        verdict, reason = answer('MOD(7,0)=1')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('#DIV/0!', reason)


class TypeRuleTests(unittest.TestCase):
    def test_text_never_equals_a_number(self):
        self.assertEqual('FALSE', shape('"5"=5'))
        self.assertEqual('TRUE', shape('"5">5'))

    def test_numeric_text_is_not_coerced_in_arithmetic(self):
        verdict, reason = answer('"5"+0=5')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('is not a number', reason)

    def test_text_comparison_is_case_insensitive(self):
        self.assertEqual('TRUE', shape('A3="APPLE"'))
        self.assertEqual('TRUE', shape('A4="apple"'))

    def test_blank_reads_as_zero_against_a_number(self):
        self.assertEqual('TRUE', shape('A5=0'))
        self.assertEqual(True, equal_values(('blank', ''), ('num', 0.0)))

    def test_ordering_text_of_different_lengths_is_undecided(self):
        self.assertIsNone(ordering(('txt', 'apple'), ('txt', 'banana')))


class FunctionTests(unittest.TestCase):
    def test_text_functions(self):
        self.assertEqual('TRUE', shape('TRIM(A9)="padded text"'))
        self.assertEqual('TRUE', shape('LEN(TRIM(A9))=11'))
        self.assertEqual('TRUE', shape('LEFT(A3,3)="app"'))
        self.assertEqual('TRUE', shape('RIGHT(A3,3)="ple"'))
        self.assertEqual('TRUE', shape('UPPER(A3)="APPLE"'))
        self.assertEqual('TRUE', shape('LOWER(A4)="apple"'))

    def test_left_beyond_the_text_returns_the_text(self):
        self.assertEqual('TRUE', shape('LEFT(A3,99)=A3'))

    def test_negative_count_is_refused(self):
        verdict, reason = answer('LEFT(A3,-1)=""')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('whole number', reason)

    def test_predicates(self):
        self.assertEqual('TRUE', shape('ISBLANK(A5)'))
        self.assertEqual('FALSE', shape('ISBLANK(A1)'))
        self.assertEqual('TRUE', shape('ISNUMBER(A1)'))
        self.assertEqual('FALSE', shape('ISNUMBER(A3)'))
        self.assertEqual('TRUE', shape('ISTEXT(A3)'))
        self.assertEqual('FALSE', shape('ISTEXT(A1)'))

    def test_boolean_functions(self):
        self.assertEqual('TRUE', shape('AND(A1>5,A2<5)'))
        self.assertEqual('FALSE', shape('AND(A1>5,A2>5)'))
        self.assertEqual('TRUE', shape('OR(A1>100,A2>1)'))
        self.assertEqual('FALSE', shape('NOT(A1>5)'))
        self.assertEqual('TRUE', shape('OR(AND(A1>5,A2<5),NOT(A1>5))'))

    def test_text_inside_a_boolean_function_is_refused(self):
        verdict, reason = answer('AND(A3)')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('is not a condition', reason)

    def test_row_uses_the_cell_under_test(self):
        self.assertEqual('TRUE', shape('ROW()=5', cell=(1, 5)))
        self.assertEqual('TRUE', shape('MOD(ROW(),2)=0', cell=(1, 4)))


class ShiftTests(unittest.TestCase):
    def test_relative_reference_walks_down_the_block(self):
        # One rule over F1:F3 anchors at F1, so `A1` reads A1, A2, A3 as the scan descends.
        self.assertEqual('TRUE', shape('ISNUMBER(A1)', cell=(6, 1), anchor=(6, 1)))
        self.assertEqual('TRUE', shape('ISNUMBER(A1)', cell=(6, 2), anchor=(6, 1)))
        self.assertEqual('FALSE', shape('ISNUMBER(A1)', cell=(6, 3), anchor=(6, 1)))

    def test_a_locked_reference_does_not_move(self):
        self.assertEqual('TRUE', shape('$A$1=10', cell=(7, 3), anchor=(7, 1)))
        self.assertEqual('TRUE', shape('$A$1=10', cell=(7, 1), anchor=(7, 1)))

    def test_a_locked_column_moves_only_in_rows(self):
        # `$A1` on a block anchored at row 1 reads row 1 for row 1, and row 2 for row 2.
        self.assertEqual('TRUE', shape('$A1=10', cell=(7, 1), anchor=(7, 1)))
        self.assertEqual('TRUE', shape('$A1=3.5', cell=(7, 2), anchor=(7, 1)))


class RefusalTests(unittest.TestCase):
    def assertRefused(self, formula: str, fragment: str) -> None:
        verdict, reason = answer(formula)
        self.assertEqual('UNDECIDED', verdict, formula)
        self.assertIn(fragment, reason, formula)

    def test_outside_the_grammar(self):
        self.assertRefused('FOO(A1,A2)>0', 'not a function')
        self.assertRefused('A1:A3>0', 'a range is not a single value')
        self.assertRefused('Sheet2!A1>0', "'!'")
        self.assertRefused('1<2<3', 'unexpected')
        self.assertRefused('(1+2=3', "missing ')'")
        self.assertRefused('1&2=3', "'&'")
        self.assertRefused('10%>0', "'%'")
        self.assertRefused('"abc', 'unterminated')
        self.assertRefused('A1>', 'ends where a value was expected')
        self.assertRefused('foo>0', 'nor a cell reference')
        self.assertRefused('A0>0', 'nor a cell reference')
        self.assertRefused('A1048577>0', 'nor a cell reference')

    def test_bounds(self):
        self.assertRefused('AND()', 'takes 1 to 8')
        self.assertRefused('AND(1,2,3,4,5,6,7,8,9)', 'more than 8 arguments')
        self.assertRefused('(' * 17 + '1=1' + ')' * 17, 'nesting deeper than 16')
        self.assertRefused('1+' * 600 + '1', 'more than 512 tokens')

    def test_a_formula_longer_than_the_model_allows_is_refused(self):
        # The workbook model refuses more than 8192 characters at construction, so this bound is only reachable
        # through the checker's own entry point.
        with self.assertRaises(Refused) as caught:
            tokenize('1+' * 4200 + '1')
        self.assertIn('8192', str(caught.exception))

    def test_error_values_propagate_as_refusals(self):
        self.assertRefused('C1=1', 'cannot compare')
        self.assertRefused('LEN(C1)=1', 'error #N/A')


class TokenizerTests(unittest.TestCase):
    def test_quoted_quotes_are_one_quote(self):
        self.assertEqual([('str', 'a"b')], tokenize('"a""b"'))

    def test_exponents_and_leading_dots(self):
        self.assertEqual([('num', 1000.0)], tokenize('1e3'))
        self.assertEqual([('num', 0.5)], tokenize('.5'))
        self.assertEqual([('num', 2.5)], tokenize('2.5e0'))

    def test_whitespace_is_not_a_token(self):
        self.assertEqual([('num', 1.0), ('sym', '+'), ('num', 2.0)], tokenize(' 1 + 2 '))

    def test_an_unexpected_character_is_named(self):
        with self.assertRaises(Refused) as caught:
            tokenize('1#2')
        self.assertIn('#', str(caught.exception))


if __name__ == '__main__':
    unittest.main()
