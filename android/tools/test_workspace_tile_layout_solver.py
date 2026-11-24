#!/usr/bin/env python3
"""Differential oracle for WorkspaceTileLayoutSolver (app)."""
import unittest


def solve(tile_count: int, viewport_w: int, min_tile_w: int, gap: int, aspect_ratio: float = 1.0):
    assert 0 <= tile_count <= 1024
    assert 1 <= viewport_w <= 16384
    assert 1 <= min_tile_w <= viewport_w
    assert 0 <= gap < viewport_w
    assert aspect_ratio > 0.0
    if tile_count == 0:
        return 1, 0, viewport_w, max(1, int(viewport_w / aspect_ratio))
    cols = max(1, (viewport_w + gap) // (min_tile_w + gap))
    rows = (tile_count + cols - 1) // cols
    total_gap = (cols - 1) * gap
    tile_w = max(1, (viewport_w - total_gap) // cols)
    tile_h = max(1, int(tile_w / aspect_ratio))
    return cols, rows, tile_w, tile_h


class WorkspaceTileLayoutSolverTest(unittest.TestCase):
    def test_grid_layout(self):
        self.assertEqual(solve(5, 1000, 300, 20), (3, 2, 320, 320))
        self.assertEqual(solve(0, 800, 200, 10), (1, 0, 800, 800))


if __name__ == "__main__":
    unittest.main()
