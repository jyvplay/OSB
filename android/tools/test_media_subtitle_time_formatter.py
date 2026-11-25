#!/usr/bin/env python3
"""Differential oracle for MediaSubtitleTimeFormatter (feature/media)."""
import unittest


def format_vtt(ms: int) -> str:
    h = ms // 3_600_000
    m = (ms % 3_600_000) // 60_000
    s = (ms % 60_000) // 1000
    rem = ms % 1000
    return f"{h:02d}:{m:02d}:{s:02d}.{rem:03d}"


class MediaSubtitleTimeFormatterTest(unittest.TestCase):
    def test_format(self):
        self.assertEqual(format_vtt(3_661_005), "01:01:01.005")


if __name__ == "__main__":
    unittest.main()
