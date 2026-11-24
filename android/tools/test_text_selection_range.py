#!/usr/bin/env python3
"""Independent differential oracle for TextSelectionRange (feature/editor).

Bounded text selection range with normalization, clamping, collapse, and
extension. Cross-checks against the Kotlin contract in
feature/editor/src/main/kotlin/dev/fusion/editor/TextSelectionRange.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_DOC_LEN = 1 << 20


class TextSelectionRange:
    def __init__(self, doc_len, start, end):
        assert 0 <= doc_len <= MAX_DOC_LEN, "docLen out of bounds"
        assert 0 <= start <= doc_len, "start out of bounds"
        assert 0 <= end <= doc_len, "end out of bounds"
        self._doc_len = doc_len
        self._start, self._end = self._normalize(start, end)

    @classmethod
    def create(cls, doc_len, start, end):
        return cls(doc_len, start, end)

    @classmethod
    def collapsed(cls, doc_len, offset):
        assert 0 <= doc_len <= MAX_DOC_LEN, "docLen out of bounds"
        assert 0 <= offset <= doc_len, "offset out of bounds"
        return cls(doc_len, offset, offset)

    @staticmethod
    def _normalize(start, end):
        s, e = start, end
        if s > e:
            s, e = e, s
        return s, e

    def _coerce(self, v):
        return max(0, min(v, self._doc_len))

    def start(self):
        return self._start

    def end(self):
        return self._end

    def length(self):
        return self._end - self._start

    def is_collapsed(self):
        return self._start == self._end

    def is_empty(self):
        return self._end == self._start

    def clamp(self, new_start, new_end):
        assert 0 <= new_start <= self._doc_len, "newStart out of bounds"
        assert 0 <= new_end <= self._doc_len, "newEnd out of bounds"
        return TextSelectionRange(self._doc_len, new_start, new_end)

    def collapse_to_start(self):
        return TextSelectionRange(self._doc_len, self._start, self._start)

    def collapse_to_end(self):
        return TextSelectionRange(self._doc_len, self._end, self._end)

    def extend_by(self, delta_start, delta_end):
        new_start = self._coerce(self._start + delta_start)
        new_end = self._coerce(self._end + delta_end)
        return TextSelectionRange(self._doc_len, new_start, new_end)

    def contains(self, offset):
        return self._start <= offset < self._end

    def document_length(self):
        return self._doc_len


class TextSelectionRangeTest(unittest.TestCase):
    def test_normal_order(self):
        r = TextSelectionRange(100, 10, 20)
        self.assertEqual(r.start(), 10)
        self.assertEqual(r.end(), 20)
        self.assertEqual(r.length(), 10)

    def test_reversed_normalized(self):
        r = TextSelectionRange(100, 20, 10)
        self.assertEqual(r.start(), 10)
        self.assertEqual(r.end(), 20)

    def test_collapsed(self):
        r = TextSelectionRange.collapsed(100, 42)
        self.assertTrue(r.is_collapsed())
        self.assertEqual(r.length(), 0)
        self.assertEqual(r.start(), 42)

    def test_clamp(self):
        r = TextSelectionRange(100, 10, 20)
        r2 = r.clamp(30, 50)
        self.assertEqual(r2.start(), 30)
        self.assertEqual(r2.end(), 50)

    def test_extend(self):
        r = TextSelectionRange(100, 10, 20)
        r2 = r.extend_by(-5, 10)
        self.assertEqual(r2.start(), 5)
        self.assertEqual(r2.end(), 30)

    def test_extend_clamped(self):
        r = TextSelectionRange(100, 0, 10)
        r2 = r.extend_by(-5, 1000)
        self.assertEqual(r2.start(), 0)
        self.assertEqual(r2.end(), 100)

    def test_contains(self):
        r = TextSelectionRange(100, 10, 20)
        self.assertTrue(r.contains(15))
        self.assertFalse(r.contains(5))
        self.assertFalse(r.contains(20))

    def test_invalid_doc_len(self):
        with self.assertRaises(AssertionError):
            TextSelectionRange(-1, 0, 0)
        with self.assertRaises(AssertionError):
            TextSelectionRange(MAX_DOC_LEN + 1, 0, 0)

    def test_start_out_of_bounds(self):
        with self.assertRaises(AssertionError):
            TextSelectionRange(100, 101, 101)


if __name__ == "__main__":
    unittest.main()
