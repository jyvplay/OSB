#!/usr/bin/env python3
"""Differential oracle for WindowMinMaxClamp (core/windowing)."""
import unittest


def clamp_win(w, h, min_w, min_h, max_w, max_h):
    return max(min_w, min(max_w, w)), max(min_h, min(max_h, h))


class WindowMinMaxClampTest(unittest.TestCase):
    def test_clamp(self):
        self.assertEqual(clamp_win(100, 2000, 320, 240, 1920, 1080), (320, 1080))


if __name__ == "__main__":
    unittest.main()
