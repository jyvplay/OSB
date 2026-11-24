#!/usr/bin/env python3
"""Differential oracle for WindowSnapGridSolver (core/windowing)."""
import unittest


def snap_to_work_area(win, work_area, threshold: int):
    wx, wy, ww, wh = win
    ax, ay, aw, ah = work_area
    assert ww > 0 and wh > 0 and aw > 0 and ah > 0 and threshold >= 0
    x, y = wx, wy
    if abs(x - ax) <= threshold:
        x = ax
    elif abs((x + ww) - (ax + aw)) <= threshold:
        x = ax + aw - ww
    if abs(y - ay) <= threshold:
        y = ay
    elif abs((y + wh) - (ay + ah)) <= threshold:
        y = ay + ah - wh
    return x, y, ww, wh


class WindowSnapGridSolverTest(unittest.TestCase):
    def test_snaps_edges(self):
        self.assertEqual(snap_to_work_area((12, 10, 400, 300), (0, 0, 1920, 1080), 16), (0, 0, 400, 300))
        self.assertEqual(snap_to_work_area((1510, 500, 400, 300), (0, 0, 1920, 1080), 16), (1520, 500, 400, 300))


if __name__ == "__main__":
    unittest.main()
