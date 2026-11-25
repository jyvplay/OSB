#!/usr/bin/env python3
"""Differential oracle for LokDocumentDirtyRegion (format/lok)."""
import unittest


def union_rect(a, b):
    x1, y1 = min(a[0], b[0]), min(a[1], b[1])
    x2, y2 = max(a[0] + a[2], b[0] + b[2]), max(a[1] + a[3], b[1] + b[3])
    return x1, y1, x2 - x1, y2 - y1


class LokDocumentDirtyRegionTest(unittest.TestCase):
    def test_union(self):
        self.assertEqual(union_rect((10, 10, 20, 20), (25, 15, 10, 30)), (10, 10, 25, 35))


if __name__ == "__main__":
    unittest.main()
