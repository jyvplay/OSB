#!/usr/bin/env python3
"""Differential oracle for EditorBracketBalancer (feature/editor)."""
import unittest


def is_balanced(text: str) -> bool:
    st = []
    pairs = {")": "(", "]": "[", "}": "{"}
    for ch in text:
        if ch in "([{":
            st.append(ch)
        elif ch in pairs:
            if not st or st.pop() != pairs[ch]:
                return False
    return not st


class EditorBracketBalancerTest(unittest.TestCase):
    def test_balanced(self):
        self.assertTrue(is_balanced("a(b[c]{d})"))
        self.assertFalse(is_balanced("([)]"))


if __name__ == "__main__":
    unittest.main()
