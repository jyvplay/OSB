#!/usr/bin/env python3
"""Differential oracle for LokTileCacheInvalidator (format/lok)."""
import unittest


def intersecting(part, dx, dy, dw, dh, ts=256):
    if dw == 0 or dh == 0:
        return []
    min_tx, max_tx = max(0, dx) // ts, max(0, dx + dw - 1) // ts
    min_ty, max_ty = max(0, dy) // ts, max(0, dy + dh - 1) // ts
    return [(part, tx, ty) for ty in range(min_ty, max_ty + 1) for tx in range(min_tx, max_tx + 1)]


class LokTileCacheInvalidatorTest(unittest.TestCase):
    def test_tiles(self):
        self.assertEqual(intersecting(0, 250, 0, 20, 10, 256), [(0, 0, 0), (0, 1, 0)])


if __name__ == "__main__":
    unittest.main()
