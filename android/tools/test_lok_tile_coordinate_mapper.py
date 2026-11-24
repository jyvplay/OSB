#!/usr/bin/env python3
"""Differential oracle for LokTileCoordinateMapper (format/lok)."""
import unittest


def pixel_to_tile(px: int, py: int, tile_size_px: int):
    assert 0 <= px <= 1_000_000 and 0 <= py <= 1_000_000
    assert 1 <= tile_size_px <= 4096
    return px // tile_size_px, py // tile_size_px


def clip_to_document(x: int, y: int, w: int, h: int, doc_w: int, doc_h: int):
    assert 1 <= doc_w <= 1_000_000 and 1 <= doc_h <= 1_000_000
    assert w >= 0 and h >= 0
    x1 = max(0, min(x, doc_w))
    y1 = max(0, min(y, doc_h))
    x2 = max(0, min(x + w, doc_w))
    y2 = max(0, min(y + h, doc_h))
    nw, nh = x2 - x1, y2 - y1
    if nw <= 0 or nh <= 0:
        return None
    return (x1, y1, nw, nh)


class LokTileCoordinateMapperTest(unittest.TestCase):
    def test_pixel_to_tile(self):
        self.assertEqual(pixel_to_tile(0, 0, 256), (0, 0))
        self.assertEqual(pixel_to_tile(256, 511, 256), (1, 1))

    def test_clip_to_document(self):
        self.assertEqual(clip_to_document(-10, -10, 50, 50, 100, 100), (0, 0, 40, 40))
        self.assertIsNone(clip_to_document(200, 200, 50, 50, 100, 100))


if __name__ == "__main__":
    unittest.main()
