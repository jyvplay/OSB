#!/usr/bin/env python3
"""Differential oracle for WildcardGlobMatcher (feature/find)."""
import unittest


def matches(pattern: str, text: str) -> bool:
    p = t = 0
    star = -1
    match = 0
    while t < len(text):
        if p < len(pattern) and pattern[p] in ("?", text[t]):
            p += 1
            t += 1
        elif p < len(pattern) and pattern[p] == "*":
            star = p
            match = t
            p += 1
        elif star != -1:
            p = star + 1
            match += 1
            t = match
        else:
            return False
    while p < len(pattern) and pattern[p] == "*":
        p += 1
    return p == len(pattern)


class WildcardGlobMatcherTest(unittest.TestCase):
    def test_glob(self):
        self.assertTrue(matches("*.kt", "Main.kt"))
        self.assertTrue(matches("a?c*e", "abcdefe"))
        self.assertFalse(matches("*.kt", "Main.java"))


if __name__ == "__main__":
    unittest.main()
