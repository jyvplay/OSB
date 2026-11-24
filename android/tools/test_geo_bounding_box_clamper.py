#!/usr/bin/env python3
"""Differential oracle for GeoBoundingBoxClamper (feature/maps)."""
import math
import unittest


def clamp_bbox(min_lat: float, min_lon: float, max_lat: float, max_lon: float):
    assert not any(math.isnan(x) for x in (min_lat, min_lon, max_lat, max_lon))
    c_min_lat = max(-90.0, min(90.0, min_lat))
    c_max_lat = max(-90.0, min(90.0, max_lat))
    assert c_min_lat <= c_max_lat
    c_min_lon = max(-180.0, min(180.0, min_lon))
    c_max_lon = max(-180.0, min(180.0, max_lon))
    return c_min_lat, c_min_lon, c_max_lat, c_max_lon


class GeoBoundingBoxClamperTest(unittest.TestCase):
    def test_clamps_bounds(self):
        self.assertEqual(clamp_bbox(-100.0, -200.0, 100.0, 200.0), (-90.0, -180.0, 90.0, 180.0))

    def test_rejects_inverted_lat(self):
        with self.assertRaises(AssertionError):
            clamp_bbox(20.0, 0.0, 10.0, 10.0)


if __name__ == "__main__":
    unittest.main()
