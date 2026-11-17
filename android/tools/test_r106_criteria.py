#!/usr/bin/env python3
"""Controls for `check_r106_criteria.py`.

An independent lane that is itself untested is a second opinion from the same author. These cases drive the
reference criterion grammar directly — `criterion_of`, `wildcard`, `criterion_holds`, `strip_head`, and whole
formulas through `decide` — so a mistake in the checker fails here instead of quietly agreeing with an equally wrong
Kotlin implementation. The wildcard matcher in the checker is a dynamic-programming table; the product's is a
backtracking scan; these cases are what keep the two honest about the same three characters.

The expectations are the rules this product states: a blank matches only an empty `=` criterion; a text cell is not
blank; a number criterion folds text that spells a number; an ordering criterion against text it cannot compare does
not match, while ordering two *different* texts is refused; an error under a criterion is a refusal; `~` escapes
`*`, `?` and itself, and a trailing `~` is a literal; a criterion above 255 characters is refused; `SUMIF` and
`AVERAGEIF` accumulate numbers only, and only the *shape* of the sum range has to match.
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_r106_criteria import (  # noqa: E402
    BLANK,
    Refused,
    criterion_holds,
    criterion_of,
    decide,
    strip_head,
    wildcard,
)

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
    (6, 1): ('num', 100.0),
    (6, 2): ('num', 200.0),
    (6, 3): ('num', 300.0),
    (8, 1): ('err', '#N/A'),
    (9, 1): ('txt', 'a*b'),
    (9, 2): ('txt', 'a?b'),
    (10, 1): ('txt', '>2'),
}
CELL = (2, 1)
ANCHOR = (2, 1)
TEXT = ('txt', '')


def answer(formula: str, cell=CELL, anchor=ANCHOR):
    return decide(formula, GRID, cell, anchor)


def shape(formula: str, **kwargs) -> str:
    return answer(formula, **kwargs)[0]


class CriterionParsingTests(unittest.TestCase):
    def test_a_plain_number_and_a_plain_boolean_are_equality(self):
        self.assertEqual(('=', ('num', 3.0)), criterion_of(('num', 3.0), 'COUNTIF'))
        self.assertEqual(('=', ('bool', True)), criterion_of(('bool', True), 'COUNTIF'))
        self.assertEqual(('=', ('txt', 'draft')), criterion_of(('txt', 'draft'), 'COUNTIF'))

    def test_each_operator_is_read_longest_first(self):
        self.assertEqual(('<=', ('num', 5.0)), criterion_of(('txt', '<=5'), 'COUNTIF'))
        self.assertEqual(('>=', ('num', 5.0)), criterion_of(('txt', '>=5'), 'COUNTIF'))
        self.assertEqual(('<>', ('num', 5.0)), criterion_of(('txt', '<>5'), 'COUNTIF'))
        self.assertEqual(('=', ('num', 5.0)), criterion_of(('txt', '=5'), 'COUNTIF'))
        self.assertEqual(('<', ('num', 5.0)), criterion_of(('txt', '<5'), 'COUNTIF'))
        self.assertEqual(('>', ('num', 5.0)), criterion_of(('txt', '>5'), 'COUNTIF'))

    def test_a_number_after_an_operator_is_numeric_only_when_it_is_written_as_one(self):
        self.assertEqual(('>', ('num', 100000.0)), criterion_of(('txt', '>1e5'), 'COUNTIF'))
        self.assertEqual(('>', ('num', 2.0)), criterion_of(('txt', '> 2'), 'COUNTIF'))
        # `0x10` and `1,5` are not literals in this model, so they are wildcard patterns, not numbers.
        self.assertEqual(('>', ('txt', '0x10')), criterion_of(('txt', '>0x10'), 'COUNTIF'))
        self.assertEqual(('>', ('txt', '1,5')), criterion_of(('txt', '>1,5'), 'COUNTIF'))

    def test_an_operator_alone_is_empty_text(self):
        self.assertEqual(('=', TEXT), criterion_of(('txt', '='), 'COUNTIF'))
        self.assertEqual(('<>', TEXT), criterion_of(('txt', '<>'), 'COUNTIF'))
        self.assertEqual(('=', TEXT), criterion_of(BLANK, 'COUNTIF'))

    def test_a_leading_sign_is_part_of_the_number(self):
        self.assertEqual(('<', ('num', -2.0)), criterion_of(('txt', '<-2'), 'COUNTIF'))
        self.assertEqual(('=', ('num', 5.0)), criterion_of(('num', 5.0), 'COUNTIF'))

    def test_the_length_bound_is_exact(self):
        self.assertEqual(('=', ('txt', 'a' * 255)), criterion_of(('txt', 'a' * 255), 'COUNTIF'))
        with self.assertRaises(Refused) as refused:
            criterion_of(('txt', 'a' * 256), 'COUNTIF')
        self.assertIn('above the 255-character bound', str(refused.exception))

    def test_an_error_criterion_is_refused(self):
        with self.assertRaises(Refused) as refused:
            criterion_of(('err', '#N/A'), 'SUMIF')
        self.assertIn('as the criterion of SUMIF', str(refused.exception))


class WildcardTests(unittest.TestCase):
    def test_a_star_matches_every_run_including_nothing(self):
        self.assertTrue(wildcard('*', 'anything'))
        self.assertTrue(wildcard('*', ''))
        self.assertTrue(wildcard('a*', 'apple'))
        self.assertFalse(wildcard('z*', 'apple'))
        self.assertTrue(wildcard('*an*', 'banana'))

    def test_a_question_matches_exactly_one_character(self):
        self.assertTrue(wildcard('?p*', 'apple'))
        self.assertTrue(wildcard('?p*', 'Apricot'))
        self.assertFalse(wildcard('?p*', 'p'))
        self.assertFalse(wildcard('a?', 'apple'))

    def test_literals_are_case_insensitive(self):
        self.assertTrue(wildcard('DRAFT', 'draft'))
        self.assertTrue(wildcard('draft', 'Draft'))
        # A trailing space is a character, so these are different texts.
        self.assertFalse(wildcard('final', 'FINAL '))

    def test_an_escaped_character_is_literal(self):
        self.assertTrue(wildcard('a~*b', 'a*b'))
        self.assertFalse(wildcard('a~*b', 'a?b'))
        self.assertTrue(wildcard('a~?b', 'a?b'))
        self.assertFalse(wildcard('a~?b', 'a*b'))
        # A tilde escapes whatever follows it, not only the three wildcard characters.
        self.assertTrue(wildcard('a~b', 'ab'))
        self.assertFalse(wildcard('a~b', 'a~b'))

    def test_a_trailing_escape_is_a_literal_tilde(self):
        self.assertTrue(wildcard('a~', 'a~'))
        self.assertFalse(wildcard('a~', 'a'))

    def test_double_star_is_the_same_as_one(self):
        self.assertTrue(wildcard('a**b', 'ab'))
        self.assertTrue(wildcard('a**b', 'axxb'))
        self.assertFalse(wildcard('a**b', 'axxbc'))

    def test_the_whole_pattern_has_to_cover_the_whole_text(self):
        self.assertFalse(wildcard('app', 'apple'))
        self.assertFalse(wildcard('', 'x'))
        self.assertTrue(wildcard('', ''))


class MatchingTests(unittest.TestCase):
    def test_a_blank_matches_only_the_empty_equals_criterion(self):
        self.assertTrue(criterion_holds(('=', TEXT), BLANK))
        self.assertFalse(criterion_holds(('<>', TEXT), BLANK))
        self.assertFalse(criterion_holds(('>', ('num', 0.0)), BLANK))
        self.assertFalse(criterion_holds(('=', ('num', 0.0)), BLANK))

    def test_an_empty_text_cell_is_not_blank(self):
        empty = ('txt', '')
        self.assertTrue(criterion_holds(('=', TEXT), empty))
        # It is "empty", so it does not satisfy non-blank either: `"<>"` is the non-blank count.
        self.assertFalse(criterion_holds(('<>', TEXT), empty))

    def test_a_number_criterion_folds_numeric_text(self):
        self.assertTrue(criterion_holds(('=', ('num', 4.0)), ('txt', ' 4 ')))
        self.assertTrue(criterion_holds(('=', ('num', 4.0)), ('num', 4.0)))
        self.assertFalse(criterion_holds(('=', ('num', 4.0)), ('txt', 'four')))
        self.assertFalse(criterion_holds(('=', ('num', 4.0)), ('bool', True)))

    def test_an_ordering_criterion_against_text_does_not_match(self):
        self.assertFalse(criterion_holds(('>', ('num', 0.0)), ('txt', 'x')))
        self.assertFalse(criterion_holds(('>', ('num', 0.0)), ('bool', True)))
        self.assertTrue(criterion_holds(('>', ('num', 0.0)), ('num', 1.0)))

    def test_ordering_two_different_texts_is_refused(self):
        # The same text in another case is orderable (and not greater than itself); a *different* text is not.
        self.assertFalse(criterion_holds(('>', ('txt', 'm')), ('txt', 'M')))
        self.assertTrue(criterion_holds(('>=', ('txt', 'm')), ('txt', 'M')))
        with self.assertRaises(Refused) as refused:
            criterion_holds(('>', ('txt', 'm')), ('txt', 'x'))
        self.assertIn('cannot order two different texts', str(refused.exception))

    def test_an_error_under_a_criterion_is_refused(self):
        with self.assertRaises(Refused) as refused:
            criterion_holds(('=', ('num', 1.0)), ('err', '#N/A'))
        self.assertIn('error #N/A under a criterion', str(refused.exception))

    def test_not_equals_is_the_negation_and_keeps_errors_out(self):
        self.assertTrue(criterion_holds(('<>', ('num', 3.0)), ('num', 2.0)))
        self.assertTrue(criterion_holds(('<>', ('txt', 'a*')), ('txt', 'banana')))
        self.assertFalse(criterion_holds(('<>', ('txt', 'a*')), ('txt', 'apple')))


class FunctionTests(unittest.TestCase):
    def test_countif_counts_matches(self):
        self.assertEqual('TRUE', shape('COUNTIF(A1:A8,">1")=2'))
        self.assertEqual('TRUE', shape('COUNTIF(A1:A8,3)=1'))
        self.assertEqual('TRUE', shape('COUNTIF(A1:A8,"x")=1'))
        self.assertEqual('TRUE', shape('COUNTIF(A1:A5,"")=1'))

    def test_sumif_sums_only_the_numbers_it_matched(self):
        self.assertEqual('TRUE', shape('SUMIF(C1:C5,">2",B1:B5)=120'))
        self.assertEqual('TRUE', shape('SUMIF(A1:A8,"<>",A1:A8)=2'))
        self.assertEqual('TRUE', shape('SUMIF(A1:A8,"<=0")=-4'))
        self.assertEqual('TRUE', shape('SUMIF(A1:A8,"",B1:B8)=50'))

    def test_averageif(self):
        self.assertEqual('TRUE', shape('AVERAGEIF(C1:C5,">2",B1:B5)=40'))
        self.assertEqual('TRUE', shape('AVERAGEIF(C1:C5,">2")=4'))

    def test_averageif_with_no_matches_is_an_error_not_a_zero(self):
        self.assertEqual('UNDECIDED', shape('AVERAGEIF(C1:C5,">99")'))
        verdict, reason = answer('AVERAGEIF(C1:C5,">99")')
        self.assertEqual('UNDECIDED', verdict)
        self.assertIn('#DIV/0!', reason)

    def test_the_sum_range_only_has_to_match_in_shape(self):
        # B1:B5 starts in a different column from C1:C5 and is still the sum range; comparing whole bounds instead
        # of widths and heights would refuse this, and every practical SUMIF looks like this.
        self.assertEqual('TRUE', shape('SUMIF(C1:C5,">2",B1:B5)=120'))
        self.assertEqual('TRUE', shape('AVERAGEIF(C1:C5,">2",B1:B5)=40'))

    def test_the_criterion_may_be_a_cell(self):
        self.assertEqual('TRUE', shape('COUNTIF(C1:C5,J1)=3'))
        self.assertEqual('TRUE', shape('SUMIF(C1:C5,J1,B1:B5)=120'))

    def test_a_criteria_result_composes(self):
        self.assertEqual('TRUE', shape('AND(COUNTIF(C1:C5,">2")=3,SUMIF(C1:C5,">2")=12)'))
        self.assertEqual('TRUE', shape('COUNTIF(C1:C5,">2")+1=4'))


class RefusalTests(unittest.TestCase):
    def assertRefused(self, formula: str, fragment: str, **kwargs) -> None:
        verdict, reason = answer(formula, **kwargs)
        self.assertEqual('UNDECIDED', verdict, formula)
        self.assertIn(fragment, reason, formula)

    def test_the_first_argument_must_be_a_range(self):
        self.assertRefused('COUNTIF(A1,1)=0', 'needs a cell range as its first argument')

    def test_the_third_argument_must_be_a_range(self):
        self.assertRefused('SUMIF(A1:A3,1,A1)=0', 'needs a cell range as its third argument')

    def test_a_sum_range_of_the_wrong_shape_is_refused(self):
        self.assertRefused('SUMIF(C1:C5,">2",B1:B4)=0', 'is 1x4 cells, the criteria range is 1x5')

    def test_arities(self):
        self.assertRefused('COUNTIF(A1:A8)=1', 'COUNTIF takes 2')
        self.assertRefused('COUNTIF(A1:A8,1,B1:B5)=0', 'COUNTIF takes 2 argument(s), not 3')
        self.assertRefused('SUMIF(A1:A8,1,B1:B5,C1:C5)=0', 'SUMIF takes 2 to 3')

    def test_an_error_in_the_sum_range_is_refused(self):
        self.assertRefused('SUMIF(A1:A8,"<>",H1:H8)=0', "#N/A in SUMIF's sum range")

    def test_a_range_as_the_criterion_is_refused(self):
        self.assertRefused('COUNTIF(A1:A3,A1:A3)=1', 'a range is not a single value')

    def test_a_criterion_above_the_bound_is_refused(self):
        self.assertRefused('COUNTIF(A1:A8,"' + 'a' * 256 + '")=0', 'above the 255-character bound')

    def test_a_criteria_function_is_not_an_aggregate(self):
        # The dispatch is by name: a misspelled criteria function stays refused, it does not fall through.
        self.assertRefused('COUNTIFS(A1:A3,1)=0', 'is not a function this reader evaluates')


class LaneTests(unittest.TestCase):
    def test_the_reason_head_is_stripped_however_long_the_formula(self):
        self.assertEqual('a criterion is too long', strip_head("expression 'X': a criterion is too long"))
        self.assertEqual('evaluates to #DIV/0!', strip_head("expression 'AVERAGEIF(A1:A3,\">9\")' evaluates to #DIV/0!"))
        # The product shortens a long formula inside the head, so the head must be found by its quotes.
        shortened = "expression 'COUNTIF(A1:A8,\"aaaaaaaaaaaaaaaaaaaaaa...': a criterion of 256 characters"
        self.assertEqual('a criterion of 256 characters', strip_head(shortened))
        self.assertEqual('bare text', strip_head('bare text'))

    def test_the_shift_rule_reaches_a_criteria_range(self):
        # One rule over K1:K3 anchors at K1, so `A1:A4` reads A2:A5 at K2 and A3:A6 at K3.
        self.assertEqual('TRUE', shape('COUNTIF(A1:A4,">0")=3', cell=(11, 1), anchor=(11, 1)))
        self.assertEqual('TRUE', shape('COUNTIF(A1:A4,">0")=2', cell=(11, 2), anchor=(11, 1)))
        self.assertEqual('TRUE', shape('COUNTIF(A1:A4,">0")=1', cell=(11, 3), anchor=(11, 1)))

    def test_an_anchored_range_does_not_move(self):
        self.assertEqual('TRUE', shape('COUNTIF($A$1:$A$4,">0")=3', cell=(11, 3), anchor=(11, 1)))


if __name__ == '__main__':
    unittest.main()
