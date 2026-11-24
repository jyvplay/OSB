#!/usr/bin/env python3
"""Independent differential oracle for OcrPreprocessing (feature/lens).

Applies grayscale confirmation, threshold binarization, approximate
deskew angle, and width normalization with padding to grayscale images.
Cross-checks against the Kotlin contract in
feature/lens/src/main/kotlin/dev/fusion/lens/OcrPreprocessing.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_INPUT = 1 << 20
MAX_DIM = 4096
MAX_PIXELS = 1 << 18
MAX_WIDTH = 4096


def preprocess(pixels: bytes, width: int, height: int, threshold: int = 128,
               target_width: int = 0):
    assert len(pixels) <= MAX_INPUT, f"input exceeds {MAX_INPUT} bytes"
    assert width > 0 and height > 0 and width <= MAX_DIM and height <= MAX_DIM, "invalid dimensions"
    assert len(pixels) == width * height, "pixel count mismatch"
    assert 0 <= threshold <= 255, "threshold out of range"
    assert target_width == 0 or (0 < target_width <= MAX_WIDTH), "targetWidth out of bounds"

    # Binarize
    binary = bytes(255 if b >= threshold else 0 for b in pixels)

    # Deskew angle (approx: row darkness variance)
    angle = _deskew_angle(binary, width, height)

    out_w, out_h = width, height
    out = binary
    if target_width > 0 and target_width != width:
        scale = target_width / width
        out_w = target_width
        out_h = max(1, min(int(height * scale), MAX_DIM))
        out = _pad_or_scale(pixels, width, height, out_w, out_h)

    assert len(out) <= MAX_PIXELS, f"output exceeds {MAX_PIXELS} pixels"
    return (out_w, out_h, out, angle)


def threshold_only(pixels: bytes, threshold: int) -> bytes:
    assert 0 <= threshold <= 255, "threshold out of range"
    return bytes(255 if b >= threshold else 0 for b in pixels)


def _deskew_angle(pixels: bytes, width: int, height: int) -> float:
    row_sums = []
    for y in range(height):
        row_sum = sum(pixels[y * width + x] for x in range(width))
        row_sums.append(row_sum / width)
    mean = sum(row_sums) / height
    variance = sum((v - mean) ** 2 for v in row_sums) / height
    return max(-5.0, min(5.0, variance / 1_000_000.0))


def _pad_or_scale(src: bytes, src_w: int, src_h: int, dst_w: int, dst_h: int) -> bytes:
    dst = bytearray(dst_w * dst_h)
    x_scale = src_w / dst_w
    y_scale = src_h / dst_h
    for dy in range(dst_h):
        for dx in range(dst_w):
            sx = min(int(dx * x_scale), src_w - 1)
            sy = min(int(dy * y_scale), src_h - 1)
            dst[dy * dst_w + dx] = src[sy * src_w + sx]
    return bytes(dst)


class OcrPreprocessingTest(unittest.TestCase):
    def test_threshold(self):
        pixels = bytes([0, 100, 200, 255])
        w, h, out, angle = preprocess(pixels, 2, 2, threshold=128)
        self.assertEqual(w, 2)
        self.assertEqual(h, 2)
        self.assertEqual(out, bytes([0, 0, 255, 255]))
        self.assertIsInstance(angle, float)

    def test_all_above_threshold(self):
        pixels = bytes([255, 255, 255, 255])
        _, _, out, _ = preprocess(pixels, 2, 2, threshold=128)
        self.assertEqual(out, bytes([255, 255, 255, 255]))

    def test_all_below_threshold(self):
        pixels = bytes([0, 0, 0, 0])
        _, _, out, _ = preprocess(pixels, 2, 2, threshold=128)
        self.assertEqual(out, bytes([0, 0, 0, 0]))

    def test_resize(self):
        pixels = bytes([0, 255, 128, 64])
        w, h, out, _ = preprocess(pixels, 2, 2, target_width=4)
        self.assertEqual(w, 4)
        self.assertEqual(len(out), 4 * h)

    def test_target_width_same(self):
        pixels = bytes([0, 255, 128, 64])
        w, h, out, _ = preprocess(pixels, 2, 2, target_width=2)
        self.assertEqual(w, 2)
        self.assertEqual(out, threshold_only(pixels, 128))

    def test_invalid_threshold(self):
        with self.assertRaises(AssertionError):
            preprocess(bytes([0]), 1, 1, threshold=256)
        with self.assertRaises(AssertionError):
            preprocess(bytes([0]), 1, 1, threshold=-1)

    def test_invalid_dimensions(self):
        with self.assertRaises(AssertionError):
            preprocess(bytes([0]), 0, 1)
        with self.assertRaises(AssertionError):
            preprocess(bytes([0]), 1, 0)

    def test_pixel_count_mismatch(self):
        with self.assertRaises(AssertionError):
            preprocess(bytes([0, 1]), 1, 1)


if __name__ == "__main__":
    unittest.main()
