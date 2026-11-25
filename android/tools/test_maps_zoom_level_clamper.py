#!/usr/bin/env python3
"""Differential oracle for MapsZoomLevelClamper (feature/maps)."""
import unittest


def clamp_zoom(z: float, lo: float = 0.0, hi: float = 22.0) -> float:
    return max(lo, min(hi, z))


class MapsZoomLevelClamperTest(unittest.TestCase):
    def test_clamp(self):
        self.assertEqual(clamp_zoom(-1.0), 0.0)
        self.assertEqual(clamp_zoom(25.0), 22.0)


if __name__ == "__main__":
    unittest.main()
