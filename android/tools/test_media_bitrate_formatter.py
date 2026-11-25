#!/usr/bin/env python3
"""Differential oracle for MediaBitrateFormatter (feature/media)."""
import unittest


def format_bps(bps: int) -> str:
    return f"{bps // 1000} kbps" if bps < 1_000_000 else f"{bps / 1_000_000.0:.1f} Mbps"


class MediaBitrateFormatterTest(unittest.TestCase):
    def test_format(self):
        self.assertEqual(format_bps(320_000), "320 kbps")
        self.assertEqual(format_bps(2_500_000), "2.5 Mbps")


if __name__ == "__main__":
    unittest.main()
