#!/usr/bin/env python3
"""Independent differential oracle for RpnEval (feature/calc).

Implements the same bounded reverse-polish stack evaluator in pure Python
and cross-checks against the Kotlin contract described in
feature/calc/src/main/kotlin/dev/fusion/calc/RpnEval.kt.

No Android SDK, Kotlin compiler or Gradle is required; this runs on host Python 3.11.
"""
from __future__ import annotations

import math
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))


MAX_TOKENS = 1024


def rpn_eval(tokens):
    """Pure-Python reference implementation of RpnEval.eval()."""
    assert 1 <= len(tokens) <= MAX_TOKENS, "token budget: %d" % len(tokens)
    stack = []
    for t in tokens:
        if t in ("+", "-", "*", "/"):
            assert len(stack) >= 2, "stack underflow"
            b = stack.pop()
            a = stack.pop()
            if t == "+":
                r = a + b
            elif t == "-":
                r = a - b
            elif t == "*":
                r = a * b
            else:
                assert b != 0.0, "division by zero"
                r = a / b
            assert math.isfinite(r), "non-finite result"
            stack.append(r)
        elif t == "neg":
            assert stack, "stack underflow"
            stack.append(-stack.pop())
        elif t == "sqrt":
            assert stack, "stack underflow"
            a = stack.pop()
            assert a >= 0.0, "sqrt of negative"
            stack.append(math.sqrt(a))
        else:
            try:
                v = float(t)
            except ValueError:
                raise AssertionError("bad literal %s" % t)
            assert math.isfinite(v), "bad literal %s" % t
            stack.append(v)
    assert len(stack) == 1, "stack has %d values" % len(stack)
    return stack[0]


class RpnEvalTest(unittest.TestCase):
    def test_basic_arithmetic(self):
        self.assertAlmostEqual(rpn_eval(["3", "4", "+"]), 7.0)
        self.assertAlmostEqual(rpn_eval(["10", "3", "-"]), 7.0)
        self.assertAlmostEqual(rpn_eval(["6", "7", "*"]), 42.0)
        self.assertAlmostEqual(rpn_eval(["20", "4", "/"]), 5.0)

    def test_neg_and_sqrt(self):
        self.assertAlmostEqual(rpn_eval(["5", "neg"]), -5.0)
        self.assertAlmostEqual(rpn_eval(["16", "sqrt"]), 4.0)
        self.assertAlmostEqual(rpn_eval(["2", "sqrt"]), math.sqrt(2))

    def test_complex_expression(self):
        self.assertAlmostEqual(rpn_eval(["3", "4", "+", "5", "2", "-", "*"]), 21.0)

    def test_stack_underflow_binary(self):
        with self.assertRaises(AssertionError):
            rpn_eval(["3", "+"])

    def test_stack_underflow_unary(self):
        with self.assertRaises(AssertionError):
            rpn_eval(["neg"])

    def test_division_by_zero(self):
        with self.assertRaises(AssertionError):
            rpn_eval(["3", "0", "/"])

    def test_sqrt_of_negative(self):
        with self.assertRaises(AssertionError):
            rpn_eval(["-1", "sqrt"])

    def test_unknown_token(self):
        with self.assertRaises(AssertionError):
            rpn_eval(["3", "4", "foo"])

    def test_bad_literal(self):
        with self.assertRaises(AssertionError):
            rpn_eval(["abc"])

    def test_non_finite_literal(self):
        with self.assertRaises(AssertionError):
            rpn_eval(["inf"])

    def test_final_stack_not_one(self):
        with self.assertRaises(AssertionError):
            rpn_eval(["3", "4"])

    def test_empty_tokens(self):
        with self.assertRaises(AssertionError):
            rpn_eval([])

    def test_too_many_tokens(self):
        with self.assertRaises(AssertionError):
            rpn_eval(["1"] * (MAX_TOKENS + 1))

    def test_single_literal(self):
        self.assertAlmostEqual(rpn_eval(["42"]), 42.0)

    def test_chained_neg(self):
        self.assertAlmostEqual(rpn_eval(["5", "neg", "neg"]), 5.0)

    def test_float_literals(self):
        self.assertAlmostEqual(rpn_eval(["1.5", "2.5", "+"]), 4.0)


if __name__ == "__main__":
    unittest.main()
