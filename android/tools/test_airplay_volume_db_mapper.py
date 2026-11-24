#!/usr/bin/env python3
"""Differential oracle for AirPlayVolumeDbMapper (cast/airplay)."""
import math
import unittest

MIN_DB = -30.0
MAX_DB = 0.0
MUTE_DB = -144.0


def slider_to_db(slider: float) -> float:
    assert not math.isnan(slider) and 0.0 <= slider <= 1.0
    if slider == 0.0:
        return MUTE_DB
    return MIN_DB + slider * (MAX_DB - MIN_DB)


def db_to_slider(db: float) -> float:
    assert not math.isnan(db)
    if db <= MUTE_DB or db < MIN_DB:
        return 0.0
    if db >= MAX_DB:
        return 1.0
    return (db - MIN_DB) / (MAX_DB - MIN_DB)


class AirPlayVolumeDbMapperTest(unittest.TestCase):
    def test_roundtrip(self):
        self.assertEqual(slider_to_db(0.0), MUTE_DB)
        self.assertEqual(slider_to_db(1.0), 0.0)
        self.assertAlmostEqual(slider_to_db(0.5), -15.0)
        self.assertAlmostEqual(db_to_slider(-15.0), 0.5)
        self.assertEqual(db_to_slider(-144.0), 0.0)

    def test_out_of_bounds(self):
        with self.assertRaises(AssertionError):
            slider_to_db(1.1)


if __name__ == "__main__":
    unittest.main()
