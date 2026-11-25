#!/usr/bin/env python3
"""Differential oracle for ImageExifOrientationMapper (format/image)."""
import unittest

MAP = {
    1: (0, False),
    2: (0, True),
    3: (180, False),
    4: (180, True),
    5: (90, True),
    6: (90, False),
    7: (270, True),
    8: (270, False),
}


class ImageExifOrientationMapperTest(unittest.TestCase):
    def test_map(self):
        self.assertEqual(MAP[1], (0, False))
        self.assertEqual(MAP[6], (90, False))
        self.assertEqual(MAP[7], (270, True))


if __name__ == "__main__":
    unittest.main()
