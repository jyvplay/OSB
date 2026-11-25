#!/usr/bin/env python3
"""Differential oracle for WindowTilingEngine (feature/window)."""
import unittest


def snap_rect(x, y, w, h, zone):
    hw, hh = w // 2, h // 2
    if zone == "LEFT_HALF":
        return (x, y, hw, h)
    if zone == "RIGHT_HALF":
        return (x + hw, y, w - hw, h)
    return (x, y, w, h)


class WindowTilingEngineTest(unittest.TestCase):
    def test_snap(self):
        self.assertEqual(snap_rect(0, 0, 1921, 1080, "LEFT_HALF"), (0, 0, 960, 1080))
        self.assertEqual(snap_rect(0, 0, 1921, 1080, "RIGHT_HALF"), (960, 0, 961, 1080))


if __name__ == "__main__":
    unittest.main()
