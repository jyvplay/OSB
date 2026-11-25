#!/usr/bin/env python3
"""Differential oracle for WindowAspectRatioResizer (core/windowing)."""
import unittest


def resize_by_width(w: int, ar: float, min_w: int, max_w: int):
    cw = max(min_w, min(max_w, w))
    ch = max(1, int(cw / ar + 0.5))
    return cw, ch


class WindowAspectRatioResizerTest(unittest.TestCase):
    def test_resize(self):
        self.assertEqual(resize_by_width(1600, 16 / 9, 320, 1920), (1600, 900))


if __name__ == "__main__":
    unittest.main()
