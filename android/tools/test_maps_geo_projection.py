#!/usr/bin/env python3
"""Differential oracle for MapsGeoProjection (feature/maps)."""
import math
import unittest


def haversine_m(lat1, lon1, lat2, lon2):
    r = 6_371_000.0
    rl1, rl2 = math.radians(lat1), math.radians(lat2)
    dl, dlo = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dl / 2) ** 2 + math.cos(rl1) * math.cos(rl2) * math.sin(dlo / 2) ** 2
    return 2.0 * r * math.asin(min(1.0, math.sqrt(a)))


def lat_lon_to_tile(lat, lon, zoom):
    n = 1 << zoom
    x = max(0, min(n - 1, int(((lon + 180.0) / 360.0) * n)))
    lr = math.radians(max(-85.05112878, min(85.05112878, lat)))
    y = max(0, min(n - 1, int(((1.0 - math.log(math.tan(lr) + 1.0 / math.cos(lr)) / math.pi) / 2.0) * n)))
    return x, y


class MapsGeoProjectionTest(unittest.TestCase):
    def test_geo(self):
        self.assertAlmostEqual(haversine_m(0.0, 0.0, 0.0, 1.0), 111_194.9266, places=1)
        self.assertEqual(lat_lon_to_tile(0.0, 0.0, 1), (1, 1))


if __name__ == "__main__":
    unittest.main()
