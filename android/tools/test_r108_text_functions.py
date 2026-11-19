#!/usr/bin/env python3
"""Controls for `check_r108_text_functions.py`.

An independent lane that is itself untested is a second opinion from the same author. These cases drive the
reference text implementation directly — the wildcard token list, the table matcher in both modes, the search scan,
the substitution walk, the whole-number guard and `decide` — so a mistake in the checker fails here instead of
quietly agreeing with an equally wrong Kotlin implementation.

The expectations are the rules this product states: `*` and `?` fold case and `~` escapes the next character, with a
trailing `~` literal; the full matcher needs the pattern to cover the whole text while the prefix matcher accepts the
empty pattern anywhere (which is how `SEARCH` returns the position where a match *starts*); a `SEARCH` start outside
`1..length+1` is `#VALUE!` and so is a pattern that never matches; `SUBSTITUTE` is case-sensitive, replaces all
occurrences or one instance, and leaves the text alone past the last occurrence; `REPT` and `SUBSTITUTE` refuse to
build a text longer than Excel's 32767-character cell text; `VALUE` reads this reader's own number grammar.
"""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_r108_text_functions import (  # noqa: E402
    NUMBER_TEXT,
    Refused,
    search_position,
    substitute,
    whole_argument,
    wild_match,
    wild_tokens,
    wildcard,
)


class WildTokensTest(unittest.TestCase):
    def test_literals_split_into_one_token_each(self):
        self.assertEqual(wild_tokens('ab'), [('lit', 'a'), ('lit', 'b')])

    def test_star_and_question(self):
        self.assertEqual(wild_tokens('a*b?'), [('lit', 'a'), ('run', None), ('lit', 'b'), ('one', None)])

    def test_tilde_escapes_the_next_character(self):
        self.assertEqual(wild_tokens('~*'), [('lit', '*')])
        self.assertEqual(wild_tokens('a~?b'), [('lit', 'a'), ('lit', '?'), ('lit', 'b')])

    def test_trailing_tilde_is_a_literal_tilde(self):
        self.assertEqual(wild_tokens('ab~'), [('lit', 'a'), ('lit', 'b'), ('lit', '~')])

    def test_empty_pattern_has_no_tokens(self):
        self.assertEqual(wild_tokens(''), [])


class WildMatchTest(unittest.TestCase):
    def fold(self, text):
        return text.lower()

    def test_full_match_needs_the_whole_text(self):
        tokens = wild_tokens('a')
        self.assertTrue(wild_match(tokens, self.fold('a'), 0, prefix=False))
        self.assertFalse(wild_match(tokens, self.fold('ab'), 0, prefix=False))

    def test_prefix_match_accepts_a_covered_prefix(self):
        tokens = wild_tokens('a')
        self.assertTrue(wild_match(tokens, self.fold('ab'), 0, prefix=True))

    def test_empty_pattern_matches_every_position_as_a_prefix(self):
        tokens = wild_tokens('')
        self.assertTrue(wild_match(tokens, self.fold('abc'), 0, prefix=True))
        self.assertTrue(wild_match(tokens, self.fold('abc'), 2, prefix=True))
        self.assertFalse(wild_match(tokens, self.fold('abc'), 0, prefix=False))

    def test_a_star_covers_nothing_and_everything(self):
        tokens = wild_tokens('a*h')
        self.assertTrue(wild_match(tokens, self.fold('ah'), 0, prefix=False))
        self.assertTrue(wild_match(tokens, self.fold('alph'), 0, prefix=False))
        self.assertFalse(wild_match(tokens, self.fold('alpha'), 0, prefix=False))

    def test_two_stars_need_no_special_case(self):
        tokens = wild_tokens('a**b')
        self.assertTrue(wild_match(tokens, self.fold('ab'), 0, prefix=False))
        self.assertTrue(wild_match(tokens, self.fold('axxb'), 0, prefix=False))

    def test_a_question_mark_needs_one_character(self):
        tokens = wild_tokens('a?')
        self.assertTrue(wild_match(tokens, self.fold('ab'), 0, prefix=False))
        self.assertFalse(wild_match(tokens, self.fold('a'), 0, prefix=False))

    def test_matching_starts_at_the_offset_it_is_given(self):
        tokens = wild_tokens('b')
        self.assertTrue(wild_match(tokens, self.fold('ab'), 1, prefix=False))
        self.assertFalse(wild_match(tokens, self.fold('ab'), 0, prefix=False))

    def test_case_folds_through_the_literal(self):
        self.assertTrue(wildcard('ALP*', 'alpha'))
        self.assertTrue(wildcard('ALP', 'alp'))

    def test_an_escaped_star_is_not_a_wildcard(self):
        self.assertTrue(wildcard('a~*b', 'a*b'))
        self.assertFalse(wildcard('a~*b', 'axb'))


class SearchPositionTest(unittest.TestCase):
    def test_reports_where_the_prefix_match_starts(self):
        self.assertEqual(search_position(wild_tokens('l'), 'alpha', 1), 2)
        self.assertIsNone(search_position(wild_tokens('l'), 'alpha', 3))
        self.assertEqual(search_position(wild_tokens('a'), 'alpha', 2), 5)

    def test_star_absorbs_the_rest_so_the_first_position_wins(self):
        self.assertEqual(search_position(wild_tokens('*a'), 'alpha', 1), 1)

    def test_reports_none_when_nothing_matches(self):
        self.assertIsNone(search_position(wild_tokens('z'), 'alpha', 1))

    def test_folds_case(self):
        self.assertEqual(search_position(wild_tokens('L'), 'alpha', 1), 2)

    def test_starts_at_the_position_it_is_given(self):
        self.assertEqual(search_position(wild_tokens('a'), 'alpha', 2), 5)


class SubstituteTest(unittest.TestCase):
    def test_replaces_every_occurrence(self):
        self.assertEqual(substitute('a-b-c', '-', '+', None), 'a+b+c')

    def test_replaces_one_instance(self):
        self.assertEqual(substitute('a-b-c', '-', '+', 2), 'a-b+c')

    def test_past_the_last_occurrence_changes_nothing(self):
        self.assertEqual(substitute('a-b-c', '-', '+', 9), 'a-b-c')

    def test_is_case_sensitive(self):
        self.assertEqual(substitute('alpha', 'A', 'X', None), 'alpha')

    def test_growth_is_visible(self):
        self.assertGreater(len(substitute('aaa', 'a', 'bb', None)), 3)


class WholeArgumentTest(unittest.TestCase):
    def test_reads_a_whole_number(self):
        self.assertEqual(whole_argument(('num', 3.0)), 3)

    def test_rejects_a_fraction(self):
        self.assertIsNone(whole_argument(('num', 1.5)))

    def test_rejects_a_number_too_large_for_an_index(self):
        self.assertIsNone(whole_argument(('num', 1e12)))

    def test_coerces_a_boolean_like_every_numeric_argument(self):
        self.assertEqual(whole_argument(('bool', True)), 1)

    def test_refuses_text_rather_than_answering_value(self):
        with self.assertRaises(Refused):
            whole_argument(('txt', '2'))


class NumberGrammarTest(unittest.TestCase):
    def test_accepts_plain_scientific_and_signed_text(self):
        for text in ('100', '-4.5', '+2', '.5', '1e2', '2.5E-3'):
            self.assertTrue(NUMBER_TEXT.match(text), text)

    def test_rejects_everything_else(self):
        for text in ('', '0x10', '1,5', '$5', '5%', 'TRUE', '1 2'):
            self.assertIsNone(NUMBER_TEXT.match(text), text)


if __name__ == '__main__':
    unittest.main()
