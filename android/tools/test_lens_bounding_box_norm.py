#!/usr/bin/env python3
"""Differential oracle for LensBoundingBoxNorm (feature/lens)."""
import unittest


def normalize(x1, y1, x2, y2, w, h):
    nx1 = max(0.0, min(1.0, x1 / w))
    ny1 = max(0.0, min(1.0, y1 / h))
    nx2 = max(0.0, min(1.0, x2 / w))
    ny2 = max(0.0, min(1.0, y2 / h))
    assert nx1 <= nx2 and ny1 <= ny2
    return nx1, ny1, nx2, ny2


def iou(a, b):
    ix1, iy1 = max(a[0], b[0]), max(a[1], b[1])
    ix2, iy2 = min(a[2], b[2]), min(a[3], b[3])
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return 0.0 if union <= 0.0 else inter / union


class LensBoundingBoxNormTest(unittest.TestCase):
    def test_iou(self):
        b1 = normalize(0, 0, 50, 50, 100, 100)
        b2 = normalize(25, 25, 75, 75, 100, 100)
        self.assertAlmostEqual(iou(b1, b2), 1.0 / 7.0, places=5)


if __name__ == "__main__":
    unittest.main()
