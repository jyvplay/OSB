#!/usr/bin/env python3
"""Independent differential oracle for FocusTraversalAuditor (feature/accessibility).

Validates focus traversal order: detects out-of-order jumps, enforces max
chain length, reports unknown elements. Cross-checks against the Kotlin
contract in feature/accessibility/src/main/kotlin/dev/fusion/a11y/FocusTraversalAuditor.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_ELEMENTS = 4096
MAX_ID_LEN = 256
MAX_CHAIN_LEN = 1024
ID_RE = __import__("re").compile(r"[a-zA-Z0-9_-]{1,64}")


class ElementInfo:
    def __init__(self, id, logical_index, focusable):
        self.id = id
        self.logical_index = logical_index
        self.focusable = focusable


class FocusTraversalAuditor:
    def __init__(self):
        self._elements = {}

    def add_element(self, id, logical_index, focusable):
        assert ID_RE.fullmatch(id), f"invalid element id: {id}"
        assert len(id) <= MAX_ID_LEN, "id too long"
        assert logical_index >= 0, "negative logical index"
        assert len(self._elements) < MAX_ELEMENTS or id in self._elements, f"too many elements: {MAX_ELEMENTS}"
        self._elements[id] = ElementInfo(id, logical_index, focusable)

    def audit(self, traversal):
        assert len(traversal) <= MAX_CHAIN_LEN, f"traversal exceeds {MAX_CHAIN_LEN} hops"
        violations = []
        out_of_order = []
        last_logical = -1
        last_id = ""
        chain_len = 0
        for i, id in enumerate(traversal):
            assert ID_RE.fullmatch(id), f"invalid traversal id: {id}"
            info = self._elements.get(id)
            if info is None:
                violations.append(f"unknown element at position {i}: {id}")
                continue
            if info.focusable:
                chain_len += 1
                if info.logical_index < last_logical:
                    out_of_order.append((last_id, id))
                    violations.append(f"out-of-order jump: {last_id} -> {id}")
                last_logical = info.logical_index
            last_id = id
        return (len(violations) == 0, out_of_order, chain_len, violations)

    def element_count(self):
        return len(self._elements)

    def focusable_count(self):
        return sum(1 for e in self._elements.values() if e.focusable)

    def clear(self):
        self._elements.clear()


class FocusTraversalAuditorTest(unittest.TestCase):
    def setUp(self):
        self.a = FocusTraversalAuditor()

    def test_in_order(self):
        self.a.add_element("a", 0, True)
        self.a.add_element("b", 1, True)
        self.a.add_element("c", 2, True)
        valid, jumps, chain, violations = self.a.audit(["a", "b", "c"])
        self.assertTrue(valid)
        self.assertEqual(jumps, [])
        self.assertEqual(chain, 3)
        self.assertEqual(violations, [])

    def test_out_of_order(self):
        self.a.add_element("a", 2, True)
        self.a.add_element("b", 0, True)
        valid, jumps, chain, violations = self.a.audit(["a", "b"])
        self.assertFalse(valid)
        self.assertEqual(len(jumps), 1)
        self.assertEqual(jumps[0], ("a", "b"))

    def test_non_focusable_skipped(self):
        self.a.add_element("a", 0, False)
        self.a.add_element("b", 1, True)
        valid, jumps, chain, violations = self.a.audit(["a", "b"])
        self.assertTrue(valid)
        self.assertEqual(chain, 1)

    def test_unknown_element(self):
        valid, jumps, chain, violations = self.a.audit(["ghost"])
        self.assertFalse(valid)
        self.assertEqual(len(violations), 1)
        self.assertIn("unknown element", violations[0])

    def test_invalid_id(self):
        with self.assertRaises(AssertionError):
            self.a.add_element("Bad Name", 0, True)

    def test_id_too_long(self):
        with self.assertRaises(AssertionError):
            self.a.add_element("x" * 257, 0, True)

    def test_negative_logical_index(self):
        with self.assertRaises(AssertionError):
            self.a.add_element("a", -1, True)

    def test_traversal_too_long(self):
        with self.assertRaises(AssertionError):
            self.a.audit(["a"] * (MAX_CHAIN_LEN + 1))

    def test_empty_traversal(self):
        valid, jumps, chain, violations = self.a.audit([])
        self.assertTrue(valid)
        self.assertEqual(chain, 0)


if __name__ == "__main__":
    unittest.main()
