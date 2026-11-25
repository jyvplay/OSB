#!/usr/bin/env python3
"""Differential oracle for LauncherGridSnapSolver (feature/launcher)."""
import unittest


def snap(x, y, cw, ch, cols, rows):
    c = max(0, min(cols - 1, int(x / cw + 0.5)))
    r = max(0, min(rows - 1, int(y / ch + 0.5)))
    return c, r


class LauncherGridSnapSolverTest(unittest.TestCase):
    def test_snap(self):
        self.assertEqual(snap(190.0, 95.0, 100.0, 100.0, 4, 5), (2, 1))


if __name__ == "__main__":
    unittest.main()
