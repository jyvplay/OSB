#!/usr/bin/env python3
"""Differential oracle for HaversineDistanceMeters (feature/maps)."""
import math
import unittest


def distance_meters(lat1, lon1, lat2, lon2):
    r = 6_371_000.0
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = math.sin(d_lat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(d_lon / 2) ** 2
    return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


class HaversineDistanceMetersTest(unittest.TestCase):
    def test_distance(self):
        self.assertAlmostEqual(distance_meters(0.0, 0.0, 0.0, 0.0), 0.0, places=5)
        self.assertAlmostEqual(distance_meters(0.0, 0.0, 0.0, 1.0), 111194.9266, places=1)


if __name__ == "__main__":
    unittest.main()
