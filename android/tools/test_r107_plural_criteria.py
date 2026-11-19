#!/usr/bin/env python3
"""Controls for `check_r107_plural_criteria.py`.

An independent lane that is itself untested is a second opinion from the same author. These cases drive the
reference plural evaluation directly — `decide`, `cell_at` and the pair walk — so a mistake in the checker fails
here instead of quietly agreeing with an equally wrong Kotlin implementation.

The expectations are the rules this product states: a position matches when every pair holds at that position; the
pairs are aligned by their own row-major positions, so a range's position is free while its shape is not; an
unpaired argument is refused; an error under a criterion is refused even when an earlier criterion already failed,
while an error in the accumulated range is only read where that range is read; `SUMIFS` accumulates numbers only
and `AVERAGEIFS` of no matching number is `#DIV/0!`.
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_r107_plural_criteria import PLURAL_CRITERIA_FUNCTIONS, Refused, cell_at, decide  # noqa: E402

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
    (2, 4): ('num', 40.0),
    (2, 5): ('num', 50.0),
    (3, 1): ('num', 1.0),
    (3, 2): ('num', 2.0),
    (3, 3): ('num', 3.0),
    (3, 4): ('num', 4.0),
    (3, 5): ('num', 5.0),
    (4, 1): ('txt', 'apple'),
    (4, 2): ('txt', 'Apricot'),
    (4, 3): ('txt', 'banana'),
    (4, 4): ('txt', ''),
    (5, 1): ('txt', 'draft'),
    (5, 2): ('txt', 'Draft'),
    (5, 3): ('txt', 'final'),
    (5, 4): ('txt', 'FINAL '),
    (8, 1): ('err', '#N/A'),
    (9, 1): ('txt', 'a*b'),
    (9, 2): ('txt', 'a?b'),
    (10, 1): ('txt', '>2'),
    (11, 1): ('txt', 'low'),
    (11, 2): ('txt', 'high'),
    (11, 3): ('txt', 'high'),
    (11, 4): ('txt', 'low'),
    (11, 5): ('txt', 'high'),
}
CELL = (2, 1)
ANCHOR = (2, 1)


def answer(formula: str, cell=CELL, anchor=ANCHOR):
    return decide(formula, GRID, cell, anchor)


def shape(formula: str, **kwargs) -> str:
    return answer(formula, **kwargs)[0]


class ShapeTests(unittest.TestCase):
    def test_cell_at_walks_a_range_in_row_major_order(self):
        # A 1x3 range from C1: positions 0,1,2 are C1, C2, C3.
        self.assertEqual((3, 1), cell_at((3, 1, 1, 3), 0))
        self.assertEqual((3, 2), cell_at((3, 1, 1, 3), 1))
        self.assertEqual((3, 3), cell_at((3, 1, 1, 3), 2))
        # A 2x2 range from A1: 0,1 are the first row, 2,3 the second.
        self.assertEqual((1, 1), cell_at((1, 1, 2, 2), 0))
        self.assertEqual((2, 1), cell_at((1, 1, 2, 2), 1))
        self.assertEqual((1, 2), cell_at((1, 1, 2, 2), 2))
        self.assertEqual((2, 2), cell_at((1, 1, 2, 2), 3))

    def test_the_three_plural_functions_are_dispatched(self):
        self.assertEqual({'COUNTIFS', 'SUMIFS', 'AVERAGEIFS'}, PLURAL_CRITERIA_FUNCTIONS)


class ConjunctionTests(unittest.TestCase):
    def test_one_pair_is_the_singular_answer(self):
        for formula in ('C1:C5', 'E1:E4', 'A1:A8'):
            for criterion in ('">2"', '"<>"', '""', '"a*"'):
                countifs = shape(f'COUNTIFS({formula},{criterion})')
                countif = shape(f'COUNTIF({formula},{criterion})')
                self.assertEqual(countif, countifs, f'{formula} {criterion}')
                self.assertEqual('TRUE', shape(f'COUNTIFS({formula},{criterion})=COUNTIF({formula},{criterion})'))

    def test_every_pair_must_hold(self):
        self.assertEqual('TRUE', shape('COUNTIFS(C1:C5,">1",C1:C5,"<4")=2'))
        self.assertEqual('TRUE', shape('COUNTIFS(C1:C5,">1",C1:C5,"<4",C1:C5,"<>2")=1'))
        # A pair that cannot hold makes the whole conjunction empty.
        self.assertEqual('TRUE', shape('COUNTIFS(C1:C5,">1",C1:C5,"<0")=0'))

    def test_pairs_are_matched_by_position_not_by_value(self):
        # K is low, high, high, low, high; C > 2 is rows 3, 4, 5, so only rows 3 and 5 are also "high".
        self.assertEqual('TRUE', shape('COUNTIFS(C1:C5,">2",K1:K5,"high")=2'))
        self.assertEqual('TRUE', shape('COUNTIFS(K1:K5,"high",C1:C5,">2")=2'))
        # If the pairs were matched as sets rather than by position, this would be 3 * 3 = 9.
        self.assertEqual('TRUE', shape('COUNTIFS(C1:C5,">0",K1:K5,"high")=3'))

    def test_a_range_position_is_free_but_its_shape_is_not(self):
        # B and C are different columns of the same shape: a legal pair.
        self.assertEqual('TRUE', shape('COUNTIFS(C1:C5,">2",B1:B5,">20")=3'))
        verdict, reason = answer('COUNTIFS(C1:C5,">2",K1:K4,"high")=0')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('criteria range 2 is 1x4 cells, the first criteria range is 1x5', reason)


class SumAndAverageTests(unittest.TestCase):
    def test_sumifs_accumulates_the_matched_positions(self):
        self.assertEqual('TRUE', shape('SUMIFS(B1:B5,C1:C5,">2")=120'))
        self.assertEqual('TRUE', shape('SUMIFS(B1:B5,C1:C5,">1",K1:K5,"high")=100'))
        self.assertEqual('TRUE', shape('SUMIFS(B1:B5,C1:C5,">1",C1:C5,"<5",K1:K5,"high")=50'))

    def test_sumifs_agrees_with_sumif_on_one_pair(self):
        for criterion in ('">2"', '""', '"<>"', '">0"'):
            self.assertEqual(
                'TRUE',
                shape(f'SUMIFS(B1:B5,C1:C5,{criterion})=SUMIF(C1:C5,{criterion},B1:B5)'),
            )

    def test_a_matched_text_or_boolean_adds_nothing(self):
        # The matched positions hold 1, 2, 3, "x", TRUE, 0, -4: the numbers sum to 2.
        self.assertEqual('TRUE', shape('SUMIFS(A1:A8,A1:A8,"<>")=2'))

    def test_averageifs(self):
        self.assertEqual('TRUE', shape('AVERAGEIFS(B1:B5,C1:C5,">2")=40'))
        self.assertEqual('TRUE', shape('AVERAGEIFS(B1:B5,C1:C5,">1",C1:C5,"<4")=25'))
        verdict, reason = answer('AVERAGEIFS(B1:B5,C1:C5,">99")')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('#DIV/0!', reason)


class ErrorTests(unittest.TestCase):
    def test_an_error_under_a_criterion_is_refused(self):
        verdict, reason = answer('COUNTIFS(C1:C5,">2",H1:H5,">0")=0')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('error #N/A under a criterion', reason)

    def test_an_error_is_found_even_when_an_earlier_pair_never_matches(self):
        # The first criterion fails at every position; the error in the second range must still be refused, because
        # whether a defect is visible may not depend on which criterion was checked first.
        verdict, reason = answer('COUNTIFS(C1:C5,">99",H1:H5,">0")=0')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('error #N/A under a criterion', reason)

    def test_an_error_in_the_sum_range_is_read_only_where_the_sums_are_read(self):
        verdict, reason = answer('SUMIFS(H1:H8,A1:A8,"<>")=0')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn("error #N/A in SUMIFS's sum range", reason)
        # The same range, with a criterion that only matches the blank row A5 (whose H is blank): no error is read.
        self.assertEqual('TRUE', shape('SUMIFS(H1:H8,A1:A8,"")=0'))

    def test_an_error_in_the_average_range_names_the_average_range(self):
        verdict, reason = answer('AVERAGEIFS(H1:H8,A1:A8,"<>")')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn("error #N/A in AVERAGEIFS's average range", reason)


class RefusalTests(unittest.TestCase):
    def assertRefused(self, formula: str, fragment: str, **kwargs) -> None:
        verdict, reason = answer(formula, **kwargs)
        self.assertEqual('UNDECIDED', verdict, formula)
        self.assertIn(fragment, reason, formula)

    def test_an_unpaired_argument_is_refused(self):
        self.assertRefused('COUNTIFS(C1:C5,">2",K1:K5)=0', 'so 3 arguments leave one unpaired')
        self.assertRefused('SUMIFS(B1:B5,C1:C5,">2",K1:K5)=0', 'so 4 arguments leave one unpaired')

    def test_every_range_must_be_a_range(self):
        self.assertRefused('SUMIFS(1,C1:C5,">2")=0', 'SUMIFS needs a cell range as its first argument')
        self.assertRefused('COUNTIFS(C1,">2")=0', 'needs a cell range for criteria range 1')
        self.assertRefused('COUNTIFS(C1:C5,">2",7,"high")=0', 'needs a cell range for criteria range 2')
        self.assertRefused('COUNTIFS(C1:C5,C1:C5)=1', 'a range is not a single value')

    def test_the_accumulated_range_shape_is_named(self):
        self.assertRefused('SUMIFS(B1:B4,C1:C5,">2")=0', 'criteria range 1 is 1x5 cells, the sum range is 1x4')
        self.assertRefused('AVERAGEIFS(B1:B4,C1:C5,">2")', 'the average range is 1x4')

    def test_arities(self):
        self.assertRefused('COUNTIFS(C1:C5)=1', 'COUNTIFS takes 2 to 8')
        self.assertRefused('SUMIFS(B1:B5)=1', 'SUMIFS takes 3 to 8')
        self.assertRefused('AVERAGEIFS(B1:B5,C1:C5)=1', 'AVERAGEIFS takes 3 to 8')
        self.assertRefused(
            'COUNTIFS(C1:C5,">0",C1:C5,">1",C1:C5,">2",C1:C5,">3",C1:C5,">4")=0',
            'more than 8 arguments',
        )

    def test_a_plural_name_is_not_a_singular_one(self):
        # The dispatch is by name; a misspelling stays refused rather than falling through to `COUNTIF`.
        self.assertRefused('COUNTIFSS(C1:C5,">2")=0', 'is not a function this reader evaluates')


class ShiftTests(unittest.TestCase):
    def test_both_ranges_shift_together(self):
        # One rule over K1:K3 anchors at K1; at K2, A1:A4 reads A2:A5 and C1:C4 reads C2:C5.
        self.assertEqual('TRUE', shape('COUNTIFS(A1:A4,">0")=3', cell=(11, 1), anchor=(11, 1)))
        self.assertEqual('TRUE', shape('COUNTIFS(A1:A4,">0")=2', cell=(11, 2), anchor=(11, 1)))
        self.assertEqual('TRUE', shape('COUNTIFS(A1:A4,">0",C1:C4,">1")=2', cell=(11, 2), anchor=(11, 1)))

    def test_two_ranges_can_be_anchored_independently(self):
        # The first range is locked, so at K2 it still reads rows 1..4 while the second reads rows 2..5.
        self.assertEqual('TRUE', shape('COUNTIFS($A$1:$A$4,">0",C1:C4,">1")=3', cell=(11, 2), anchor=(11, 1)))


class CompositionTests(unittest.TestCase):
    def test_a_plural_result_composes(self):
        self.assertEqual('TRUE', shape('AND(COUNTIFS(C1:C5,">2")=3,AVERAGEIFS(B1:B5,C1:C5,">2")=40)'))
        self.assertEqual('TRUE', shape('COUNTIFS(C1:C5,">2")+1=4'))

    def test_a_plural_result_is_an_ordinary_number(self):
        # Inside an aggregate it is one value, not a range: `SUM(3)` is 3 and `COUNT(3)` is 1.
        self.assertEqual('TRUE', shape('SUM(COUNTIFS(C1:C5,">2"))=3'))
        self.assertEqual('TRUE', shape('COUNT(COUNTIFS(C1:C5,">2"))=1'))

    def test_a_plural_call_cannot_stand_in_for_a_range(self):
        verdict, reason = answer('COUNTIFS(COUNTIFS(C1:C5,">2"),1)')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('needs a cell range for criteria range 1', reason)


if __name__ == '__main__':
    unittest.main()
