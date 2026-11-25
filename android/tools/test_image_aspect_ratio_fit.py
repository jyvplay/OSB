#!/usr/bin/env python3
"""Differential oracle for ImageAspectRatioFit (format/image)."""
import unittest


def fit_inside(sw, sh, mw, mh):
    scale = min(mw / sw, mh / sh)
    return max(1, min(mw, int(sw * scale + 0.5))), max(1, min(mh, int(sh * scale + 0.5)))


class ImageAspectRatioFitTest(unittest.TestCase):
    def test_fit(self):
        self.assertEqual(fit_inside(1920, 1080, 960, 960), (960, 540))


if __name__ == "__main__":
    unittest.main()
