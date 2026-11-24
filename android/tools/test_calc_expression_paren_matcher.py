#!/usr/bin/env python3
"""Differential oracle for CalcExpressionParenMatcher (feature/calc)."""
import unittest


def is_balanced(expr: str) -> bool:
    assert len(expr) <= 8192
    stack = []
    in_string = False
    escaped = False
    for ch in expr:
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch in "([{":
            stack.append(ch)
        elif ch == ")":
            if not stack or stack.pop() != "(":
                return False
        elif ch == "]":
            if not stack or stack.pop() != "[":
                return False
        elif ch == "}":
            if not stack or stack.pop() != "{":
                return False
    return not in_string and not stack


class CalcExpressionParenMatcherTest(unittest.TestCase):
    def test_balanced(self):
        self.assertTrue(is_balanced("SUM(A1:A10) + [1, 2]"))
        self.assertTrue(is_balanced('CONCAT("(((", A1)'))
        self.assertTrue(is_balanced('CONCAT("a\\"(", B1)'))

    def test_unbalanced(self):
        self.assertFalse(is_balanced("SUM(A1:A10"))
        self.assertFalse(is_balanced("(]"))
        self.assertFalse(is_balanced('"unclosed'))


if __name__ == "__main__":
    unittest.main()
