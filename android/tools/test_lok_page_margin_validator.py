#!/usr/bin/env python3
"""Differential oracle for LokPageMarginValidator (format/lok)."""
import unittest


def are_valid(t, r, b, l) -> bool:
    return all(0 <= x <= 14400 for x in (t, r, b, l))


class LokPageMarginValidatorTest(unittest.TestCase):
    def test_margins(self):
        self.assertTrue(are_valid(720, 720, 720, 720))
        self.assertFalse(are_valid(-1, 720, 720, 720))


if __name__ == "__main__":
    unittest.main()
