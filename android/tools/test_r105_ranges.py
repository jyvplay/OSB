#!/usr/bin/env python3
"""Controls for `check_r105_ranges.py`.

An independent lane that is itself untested is a second opinion from the same author. These cases drive the
reference tokenizer, parser and aggregate evaluator directly — not through the workbook — so a mistake in the
checker fails here instead of quietly agreeing with an equally wrong Kotlin implementation.

The expectations are the aggregate rules this product states: inside a range, text, booleans and blanks are ignored
by the arithmetic aggregates; `COUNTA` counts anything that is not blank; an error in a range propagates with its
cell; `AVERAGE` of no numbers is `#DIV/0!` and `MEDIAN` of no numbers is `#NUM!`; a scalar argument is classified by
type or coerced by the model's existing rule; a range used as a value, off grid, or above 100000 cells is refused.
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_r105_ranges import Refused, decide  # noqa: E402

GRID = {
    (1, 1): ('num', 1.0),
    (1, 2): ('num', 2.0),
    (1, 3): ('num', 3.0),
    (1, 4): ('txt', 'x'),
    (1, 6): ('bool', True),
    (1, 7): ('num', 0.0),
    (1, 8): ('num', -4.0),
    (1, 9): ('txt', ' 4 '),
    (2, 1): ('num', 10.0),
    (2, 2): ('num', 20.0),
    (2, 3): ('num', 30.0),
    (3, 1): ('err', '#N/A'),
    (4, 1): ('num', 2.0),
    (4, 2): ('num', 4.0),
    (4, 3): ('num', 6.0),
    (4, 4): ('num', 8.0),
    (5, 1): ('num', 2.0),
    (5, 2): ('num', 4.0),
    (5, 3): ('num', 6.0),
}
CELL = (2, 1)
ANCHOR = (2, 1)


def answer(formula: str, cell=CELL, anchor=ANCHOR):
    return decide(formula, GRID, cell, anchor)


def shape(formula: str, **kwargs) -> str:
    return answer(formula, **kwargs)[0]


class ScalarTests(unittest.TestCase):
    def test_literals(self):
        for formula, expected in [
            ('SUM(1,2,3)=6', 'TRUE'),
            ('AVERAGE(1,2,3)=2', 'TRUE'),
            ('MIN(3,1,2)=1', 'TRUE'),
            ('MAX(3,1,2)=3', 'TRUE'),
            ('MEDIAN(1,2,3)=2', 'TRUE'),
            ('MEDIAN(1,2,3,4)=2.5', 'TRUE'),
            ('COUNT(1,2,"x")=2', 'TRUE'),
            ('COUNTA(1,"x",A5)=2', 'TRUE'),
        ]:
            self.assertEqual(expected, shape(formula), formula)

    def test_a_boolean_scalar_is_one(self):
        self.assertEqual('TRUE', shape('SUM(TRUE)=1'))
        self.assertEqual('TRUE', shape('COUNTA(TRUE)=1'))
        # COUNT classifies a scalar by type, so a boolean is not a number for it.
        self.assertEqual('TRUE', shape('COUNT(TRUE)=0'))

    def test_a_text_scalar_is_refused_by_the_arithmetic_aggregates(self):
        verdict, reason = answer('SUM("x")=0')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('is not a number for SUM', reason)

    def test_a_blank_scalar_contributes_nothing(self):
        self.assertEqual('TRUE', shape('SUM(A5)=0'))
        self.assertEqual('TRUE', shape('COUNTA(A5)=0'))
        verdict, reason = answer('AVERAGE(A5)')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('#DIV/0!', reason)


class RangeTests(unittest.TestCase):
    def test_sums(self):
        self.assertEqual('TRUE', shape('SUM(A1:A3)=6'))
        self.assertEqual('TRUE', shape('SUM(B1:B3)=60'))
        self.assertEqual('TRUE', shape('SUM(A1:B3)=66'))
        self.assertEqual('TRUE', shape('SUM(A1:A3,B1:B3)=66'))

    def test_text_blank_and_boolean_inside_a_range_are_ignored(self):
        self.assertEqual('TRUE', shape('SUM(A1:A8)=2'))
        self.assertEqual('TRUE', shape('COUNT(A1:A8)=5'))
        self.assertEqual('TRUE', shape('COUNTA(A1:A8)=7'))
        self.assertEqual('TRUE', shape('MIN(A1:A8)=-4'))
        self.assertEqual('TRUE', shape('MAX(A1:A8)=3'))

    def test_median(self):
        self.assertEqual('TRUE', shape('MEDIAN(D1:D4)=5'))
        self.assertEqual('TRUE', shape('MEDIAN(E1:E3)=4'))
        self.assertEqual('TRUE', shape('MEDIAN(A1:A8)=1'))

    def test_a_reversed_range_is_normalized(self):
        self.assertEqual('TRUE', shape('SUM(A3:A1)=6'))
        self.assertEqual('TRUE', shape('SUM(B3:B1)=60'))

    def test_a_range_of_no_numbers(self):
        self.assertEqual('TRUE', shape('MIN(A4:A6)=0'))
        self.assertEqual('TRUE', shape('MAX(A4:A6)=0'))
        verdict, reason = answer('MEDIAN(A4:A6)')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('#NUM!', reason)

    def test_an_error_in_a_range_carries_its_cell(self):
        verdict, reason = answer('SUM(A1:C1)=0')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('#N/A in SUM at C1', reason)


class ShiftTests(unittest.TestCase):
    def test_a_range_moves_with_the_block(self):
        # One rule over F1:F3 anchors at F1, so `A1:A3` reads A2:A4 at F2 (2+3 = 5) and A3:A5 at F3 (3).
        self.assertEqual('TRUE', shape('SUM(A1:A3)>5', cell=(6, 1), anchor=(6, 1)))
        self.assertEqual('FALSE', shape('SUM(A1:A3)>5', cell=(6, 2), anchor=(6, 1)))
        self.assertEqual('TRUE', shape('SUM(A1:A3)=3', cell=(6, 3), anchor=(6, 1)))

    def test_a_locked_endpoint_does_not_move(self):
        self.assertEqual('TRUE', shape('SUM(A$1:A$3)>5', cell=(6, 6), anchor=(6, 4)))
        self.assertEqual('TRUE', shape('SUM(A$1:A$3)>5', cell=(6, 4), anchor=(6, 4)))

    def test_a_row_lock_without_a_column_lock_still_moves_rows(self):
        # At the anchor the range is A1:A3 (6); one row down it is A2:A4 (2 + 3 + text = 5).
        self.assertEqual('TRUE', shape('SUM($A1:$A3)=6', cell=(6, 7), anchor=(6, 7)))
        self.assertEqual('TRUE', shape('SUM($A1:$A3)=5', cell=(6, 8), anchor=(6, 7)))

    def test_a_range_that_leaves_the_sheet_is_refused(self):
        verdict, reason = answer('SUM(A1048575:A1048576)=0', cell=(13, 2), anchor=(13, 1))
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('shifts outside the sheet', reason)
        self.assertEqual('TRUE', shape('SUM(A1048575:A1048576)=0', cell=(13, 1), anchor=(13, 1)))


class RefusalTests(unittest.TestCase):
    def assertRefused(self, formula: str, fragment: str, **kwargs) -> None:
        verdict, reason = answer(formula, **kwargs)
        self.assertEqual('UNDECIDED', verdict, formula)
        self.assertIn(fragment, reason, formula)

    def test_a_range_is_not_a_value(self):
        self.assertRefused('A1:A3', 'a range is not a single value')
        self.assertRefused('A1:A3>0', 'a range is not a single value')
        self.assertRefused('ISBLANK(A1:A3)', 'a range is not a single value')
        self.assertRefused('SUM(A1:A3)+A1:A3=6', 'a range is not a single value')

    def test_broken_endpoints(self):
        self.assertRefused('SUM(A1:A)=0', "':' must be followed by a cell reference")
        self.assertRefused('SUM(A1:3)=0', "':' must be followed by a cell reference")
        self.assertRefused('SUM(:A1)=0', "unexpected ':'")
        self.assertRefused('SUM(A:A)=0', 'neither a function, a literal nor a cell reference')

    def test_bounds(self):
        self.assertRefused('SUM()=0', 'takes 1 to 8')
        self.assertRefused('SUM(1,2,3,4,5,6,7,8,9)=45', 'more than 8 arguments')
        self.assertRefused('SUM(A1:CV2500)=0', 'aggregate bound')
        # 800 cells is inside the bound, so the reader walks it; B1:B3 adds 60 to A1:A8's 2.
        self.assertEqual('TRUE', shape('SUM(A1:B400)=62'))

    def test_an_aggregate_may_be_nested(self):
        self.assertEqual('TRUE', shape('SUM(SUM(A1:A3),1)=7'))
        self.assertEqual('TRUE', shape('AND(SUM(A1:A3)=6,COUNT(A1:A3)=3)'))

    def test_a_range_in_a_non_aggregate_call_is_refused_by_name(self):
        verdict, reason = answer('LEN(A1:A3)=1')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('a range is not a single value', reason)

    def test_tokenizer_still_bounds_the_formula(self):
        with self.assertRaises(Refused):
            from check_r105_ranges import tokenize
            tokenize('1+' * 4200 + '1')


if __name__ == '__main__':
    unittest.main()
