#!/usr/bin/env python3
"""Differential oracle for WindowZOrderStack (core/windowing)."""
import unittest


class WindowZOrderStack:
    def __init__(self):
        self.stack = []

    def bring_to_front(self, wid: str):
        if wid in self.stack:
            self.stack.remove(wid)
        self.stack.append(wid)

    def top_window(self):
        return self.stack[-1] if self.stack else None


class WindowZOrderStackTest(unittest.TestCase):
    def test_zorder(self):
        s = WindowZOrderStack()
        s.bring_to_front("w1")
        s.bring_to_front("w2")
        s.bring_to_front("w1")
        self.assertEqual(s.top_window(), "w1")
        self.assertEqual(s.stack, ["w2", "w1"])


if __name__ == "__main__":
    unittest.main()
