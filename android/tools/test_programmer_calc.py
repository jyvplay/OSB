#!/usr/bin/env python3
"""Independent differential oracle for Programmer calculator (feature/calc).

Implements the same 64-bit two's complement programmer calculator in pure
Python and cross-checks against the Kotlin contract described in
feature/calc/src/main/kotlin/dev/fusion/calc/Programmer.kt and
feature/calc/src/main/kotlin/dev/fusion/calc/Calculator.kt.

No Android SDK, Kotlin compiler or Gradle is required; this runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))


MASK64 = (1 << 64) - 1
MAX_INPUT = 512
MAX_DEPTH = 32


def to_signed64(n):
    n &= MASK64
    if n >= (1 << 63):
        n -= (1 << 64)
    return n


def to_unsigned64(n):
    return n & MASK64


def format_hex(bits):
    return "0x" + format(to_unsigned64(bits), "016X")


def format_bin(bits):
    return "0b" + format(to_unsigned64(bits), "064b")


def format_oct(bits):
    return "0o" + format(to_unsigned64(bits), "022o")


def tokenize(input_str):
    tokens = []
    i = 0
    n = len(input_str)
    while i < n:
        c = input_str[i]
        if c.isspace():
            i += 1
            continue
        if c in "()":
            tokens.append(("PAREN", c))
            i += 1
            continue
        if c in "|^&+-*/%~":
            tokens.append(("OP", c))
            i += 1
            continue
        if c in "<>":
            j = i
            while j < n and input_str[j] == c:
                j += 1
            tokens.append(("OP", input_str[i:j]))
            i = j
            continue
        j = i
        while j < n and (input_str[j].isalnum() or input_str[j] in "xX"):
            j += 1
        tokens.append(("NUMBER", input_str[i:j]))
        i = j
    tokens.append(("END", ""))
    return tokens


class ProgrammerTokenizer:
    def __init__(self, input_str):
        self.tokens = tokenize(input_str)
        self.pos = 0

    def peek(self):
        return self.tokens[self.pos]

    def take(self):
        tok = self.tokens[self.pos]
        self.pos += 1
        return tok

    def expect(self, kind):
        tok = self.take()
        assert tok[0] == kind, "expected %s, got %s" % (kind, tok)


def parse_number(text):
    text = text.lower()
    if text.startswith("0x"):
        base = 16
        digits = text[2:]
    elif text.startswith("0b"):
        base = 2
        digits = text[2:]
    elif text.startswith("0o"):
        base = 8
        digits = text[2:]
    else:
        base = 10
        digits = text
    assert digits, "empty digits"
    for d in digits:
        val = int(d, base) if d.isdigit() else (ord(d.lower()) - ord('a') + 10)
        assert val is not None and val < base, "invalid digit"
    value = int(digits, base)
    assert value.bit_length() <= 64, "literal exceeds 64 bits"
    return to_signed64(value)


def evaluate_programmer(expression):
    assert len(expression) <= MAX_INPUT, "expression too long"
    t = ProgrammerTokenizer(expression)

    def expression(minimum, depth):
        assert depth < MAX_DEPTH, "nesting limit reached"
        kind, text = t.take()
        if kind == "NUMBER":
            left = parse_number(text)
        elif kind == "PAREN" and text == "(":
            left = expression(0, depth + 1)
            t.expect("PAREN")
        elif kind == "OP" and text in ("-", "+", "~"):
            right = expression(7, depth + 1)
            if text == "-":
                left = -right
            elif text == "+":
                left = right
            else:
                left = ~right
            left = to_signed64(left)
        else:
            raise AssertionError("expected a 64-bit integer or parenthesis")

        while True:
            kind, op = t.peek()
            if kind != "OP":
                return left
            prec = {"|": 1, "^": 2, "&": 3, "<<": 4, ">>": 4, ">>>": 4,
                    "+": 5, "-": 5, "*": 6, "/": 6, "%": 6}.get(op, -1)
            if prec < minimum:
                return left
            t.take()
            right = expression(prec + 1, depth + 1)
            if op == "|":
                left = to_signed64(left | right)
            elif op == "^":
                left = to_signed64(left ^ right)
            elif op == "&":
                left = to_signed64(left & right)
            elif op == "+":
                left = to_signed64(left + right)
            elif op == "-":
                left = to_signed64(left - right)
            elif op == "*":
                left = to_signed64(left * right)
            elif op == "/":
                assert right != 0, "division by zero"
                left = int(left / right)  # truncate toward zero like Kotlin
            elif op == "%":
                assert right != 0, "remainder by zero"
                left = left % right if left >= 0 else -((-left) % right)
            elif op == "<<":
                assert 0 <= right <= 63, "shift count must be from 0 to 63"
                left = to_signed64((to_unsigned64(left) << right) & MASK64)
            elif op == ">>":
                assert 0 <= right <= 63, "shift count must be from 0 to 63"
                left = left >> right
            elif op == ">>>":
                assert 0 <= right <= 63, "shift count must be from 0 to 63"
                left = to_signed64(to_unsigned64(left) >> right)
            else:
                return left

    result = expression(0, 0)
    kind, _ = t.take()
    assert kind == "END", "unexpected trailing input"
    return result


class ProgrammerCalcTest(unittest.TestCase):
    def test_decimal(self):
        self.assertEqual(evaluate_programmer("42"), 42)

    def test_hex(self):
        self.assertEqual(evaluate_programmer("0xFF"), 255)

    def test_binary(self):
        self.assertEqual(evaluate_programmer("0b1010"), 10)

    def test_octal(self):
        self.assertEqual(evaluate_programmer("0o17"), 15)

    def test_bitwise_or(self):
        self.assertEqual(evaluate_programmer("1 | 2"), 3)

    def test_bitwise_xor(self):
        self.assertEqual(evaluate_programmer("1 ^ 2"), 3)

    def test_bitwise_and(self):
        self.assertEqual(evaluate_programmer("3 & 1"), 1)

    def test_left_shift(self):
        self.assertEqual(evaluate_programmer("1 << 4"), 16)

    def test_right_shift(self):
        self.assertEqual(evaluate_programmer("16 >> 2"), 4)

    def test_logical_right_shift(self):
        self.assertEqual(evaluate_programmer("0x8000000000000000 >>> 1"), 1 << 62)

    def test_arithmetic_right_shift_negative(self):
        self.assertEqual(evaluate_programmer("-1 >> 1"), -1)

    def test_bitwise_not(self):
        self.assertEqual(evaluate_programmer("~0"), -1)

    def test_unary_minus(self):
        self.assertEqual(evaluate_programmer("-42"), -42)

    def test_parentheses(self):
        self.assertEqual(evaluate_programmer("(1 + 2) * 3"), 9)

    def test_precedence(self):
        self.assertEqual(evaluate_programmer("1 + 2 * 3"), 7)
        self.assertEqual(evaluate_programmer("1 + 2 | 4"), 7)

    def test_division_by_zero(self):
        with self.assertRaises(AssertionError):
            evaluate_programmer("1 / 0")

    def test_remainder_by_zero(self):
        with self.assertRaises(AssertionError):
            evaluate_programmer("1 % 0")

    def test_shift_count_out_of_range(self):
        with self.assertRaises(AssertionError):
            evaluate_programmer("1 << 64")

    def test_literal_exceeds_64_bits(self):
        with self.assertRaises(AssertionError):
            evaluate_programmer("0xFFFFFFFFFFFFFFFFF")

    def test_nesting_limit(self):
        with self.assertRaises(AssertionError):
            evaluate_programmer("(" * 33 + "1" + ")" * 33)

    def test_format_hex(self):
        self.assertEqual(format_hex(255), "0x00000000000000FF")
        self.assertEqual(format_hex(-1), "0xFFFFFFFFFFFFFFFF")

    def test_format_bin(self):
        self.assertEqual(format_bin(1), "0b0000000000000000000000000000000000000000000000000000000000000001")

    def test_format_oct(self):
        self.assertEqual(format_oct(8), "0o0000000000000000000010")


if __name__ == "__main__":
    unittest.main()
