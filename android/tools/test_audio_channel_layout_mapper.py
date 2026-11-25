#!/usr/bin/env python3
"""Differential oracle for AudioChannelLayoutMapper (feature/media)."""
import unittest


def map_layout(channels: int) -> str:
    assert 1 <= channels <= 64
    return {1: "MONO", 2: "STEREO", 6: "SURROUND_5_1", 8: "SURROUND_7_1"}.get(channels, "CUSTOM")


class AudioChannelLayoutMapperTest(unittest.TestCase):
    def test_map(self):
        self.assertEqual(map_layout(2), "STEREO")
        self.assertEqual(map_layout(6), "SURROUND_5_1")
        self.assertEqual(map_layout(4), "CUSTOM")


if __name__ == "__main__":
    unittest.main()
