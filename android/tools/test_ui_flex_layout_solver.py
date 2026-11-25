#!/usr/bin/env python3
"""Differential oracle for UiFlexLayoutSolver (core/ui)."""
import unittest


def solve_row(container: int, gap: int, items):
    total_gap = gap * (len(items) - 1) if items else 0
    fixed_sum = sum(max(f, m) for _, f, w, m in items if w <= 0)
    rem = max(0, container - total_gap - fixed_sum)
    tot_w = sum(w for _, _, w, _ in items if w > 0)
    cur, out = 0, []
    for iid, f, w, m in items:
        sz = max(m, int(rem * (w / tot_w))) if (w > 0 and tot_w > 0) else max(f, m)
        out.append((iid, cur, sz))
        cur += sz + gap
    return out


class UiFlexLayoutSolverTest(unittest.TestCase):
    def test_row(self):
        res = solve_row(100, 10, [("a", 20, 0.0, 0), ("b", 0, 1.0, 0), ("c", 0, 2.0, 0)])
        self.assertEqual(res, [("a", 0, 20), ("b", 30, 20), ("c", 60, 40)])


if __name__ == "__main__":
    unittest.main()
