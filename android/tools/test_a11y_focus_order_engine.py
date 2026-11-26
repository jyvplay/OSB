#!/usr/bin/env python3
"""Differential oracle for A11yFocusOrderEngine (feature/accessibility)."""
import unittest


def next_focus(nodes, cur):
    ordered = [n[0] for n in sorted((x for x in nodes if x[3]), key=lambda x: (x[1], x[2], x[0]))]
    if not ordered:
        return None
    if cur not in ordered:
        return ordered[0]
    return ordered[(ordered.index(cur) + 1) % len(ordered)]


class A11yFocusOrderEngineTest(unittest.TestCase):
    def test_focus(self):
        ns = [("b", 10, 20, True), ("a", 10, 5, True), ("c", 5, 0, False)]
        self.assertEqual(next_focus(ns, "a"), "b")
        self.assertEqual(next_focus(ns, "b"), "a")


if __name__ == "__main__":
    unittest.main()
