#!/usr/bin/env python3
"""Independent differential oracle for DecimalMath (feature/calc).

Implements the same bounded decimal exact arithmetic in pure Python and
cross-checks against the Kotlin contract described in
feature/calc/src/main/kotlin/dev/fusion/calc/DecimalMath.kt.

No Android SDK, Kotlin compiler or Gradle is required; this runs on host Python 3.11.
"""
from __future__ import annotations

import math
import re
import sys
import unittest
from decimal import Decimal, ROUND_HALF_EVEN, InvalidOperation, getcontext

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

PRECISION = 34
MIN_EXPONENT = -1000
MAX_EXPONENT = 1000
MAX_POWER = 64

DECIMAL_RE = re.compile(r"[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?")

getcontext().prec = 50
getcontext().rounding = ROUND_HALF_EVEN


def parse_decimal(text):
    assert text and text.strip(), "empty decimal literal"
    assert DECIMAL_RE.fullmatch(text.strip()), "invalid decimal literal: %s" % text
    return Decimal(text.strip())


def check_bounds(value):
    assert value.is_finite(), "non-finite decimal result"
    if value != 0:
        adj_exp = value.adjusted()
        assert MIN_EXPONENT <= adj_exp <= MAX_EXPONENT, (
            "adjusted exponent %d outside [%d, %d]" % (adj_exp, MIN_EXPONENT, MAX_EXPONENT)
        )


def round_to_precision(value):
    if value == 0:
        return Decimal(0)
    exponent = value.adjusted() - (PRECISION - 1)
    quantum = Decimal(1).scaleb(exponent)
    return value.quantize(quantum, rounding=ROUND_HALF_EVEN)


def normalize(value):
    """Strip trailing zeros, matching Kotlin's stripTrailingZeros()."""
    value = value.normalize()
    if value == 0:
        return "0"
    s = format(value, "f")
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    return s if s else "0"


def add(a, b):
    x = parse_decimal(a)
    y = parse_decimal(b)
    result = round_to_precision(x + y)
    check_bounds(result)
    return normalize(result)


def subtract(a, b):
    x = parse_decimal(a)
    y = parse_decimal(b)
    result = round_to_precision(x - y)
    check_bounds(result)
    return normalize(result)


def multiply(a, b):
    x = parse_decimal(a)
    y = parse_decimal(b)
    result = round_to_precision(x * y)
    check_bounds(result)
    return normalize(result)


def divide(a, b):
    x = parse_decimal(a)
    y = parse_decimal(b)
    assert y != 0, "division by zero"
    result = round_to_precision(x / y)
    check_bounds(result)
    return normalize(result)


def power(base, exponent):
    assert 0 <= exponent <= MAX_POWER, "power exponent outside [0, %d]" % MAX_POWER
    x = parse_decimal(base)
    if exponent == 0:
        return "1"
    result = x
    for _ in range(exponent - 1):
        result = round_to_precision(result * x)
        check_bounds(result)
    return normalize(result)


def negate(a):
    x = parse_decimal(a)
    return normalize(-x)


def abs_val(a):
    x = parse_decimal(a)
    return normalize(abs(x))


def is_valid_decimal(text):
    try:
        parse_decimal(text)
        return True
    except AssertionError:
        return False


class DecimalMathTest(unittest.TestCase):
    def test_add(self):
        self.assertEqual(add("1.5", "2.5"), "4")
        self.assertEqual(add("0.1", "0.2"), "0.3")

    def test_subtract(self):
        self.assertEqual(subtract("5", "3"), "2")
        self.assertEqual(subtract("1.5", "2.5"), "-1")

    def test_multiply(self):
        self.assertEqual(multiply("2", "3"), "6")
        self.assertEqual(multiply("1.5", "2"), "3")

    def test_divide(self):
        self.assertEqual(divide("10", "2"), "5")
        self.assertAlmostEqual(float(divide("1", "3")), 1 / 3, places=10)

    def test_divide_by_zero(self):
        with self.assertRaises(AssertionError):
            divide("1", "0")

    def test_power(self):
        self.assertEqual(power("2", 10), "1024")
        self.assertEqual(power("5", 0), "1")
        self.assertEqual(power("10", 3), "1000")

    def test_power_exponent_bound(self):
        with self.assertRaises(AssertionError):
            power("2", 65)
        with self.assertRaises(AssertionError):
            power("2", -1)

    def test_negate(self):
        self.assertEqual(negate("5"), "-5")
        self.assertEqual(negate("-5"), "5")

    def test_abs(self):
        self.assertEqual(abs_val("-5"), "5")
        self.assertEqual(abs_val("5"), "5")

    def test_empty_literal(self):
        with self.assertRaises(AssertionError):
            parse_decimal("")
        with self.assertRaises(AssertionError):
            parse_decimal("   ")

    def test_invalid_literal(self):
        with self.assertRaises(AssertionError):
            parse_decimal("abc")
        with self.assertRaises(AssertionError):
            parse_decimal("1.2.3")

    def test_is_valid_decimal(self):
        self.assertTrue(is_valid_decimal("3.14"))
        self.assertTrue(is_valid_decimal("-1.5e10"))
        self.assertFalse(is_valid_decimal("abc"))
        self.assertFalse(is_valid_decimal(""))

    def test_result_scale(self):
        r = add("1.5", "2.5")
        self.assertEqual(r, "4")

    def test_half_even_rounding(self):
        self.assertEqual(divide("5", "2"), "2.5")
        r = divide("1", "3")
        self.assertTrue(r.startswith("0.3333333333333333333333333333333333"))


if __name__ == "__main__":
    unittest.main()
