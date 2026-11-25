#!/usr/bin/env python3
"""Differential oracle for WalCheckpointWatermark (core/storage)."""
import unittest


class WalCheckpointWatermark:
    def __init__(self, threshold: int):
        self.threshold = threshold
        self.pending = 0

    def append_frames(self, count: int) -> bool:
        self.pending += count
        return self.pending >= self.threshold

    def mark_checkpointed(self):
        self.pending = 0


class WalCheckpointWatermarkTest(unittest.TestCase):
    def test_watermark(self):
        w = WalCheckpointWatermark(100)
        self.assertFalse(w.append_frames(60))
        self.assertTrue(w.append_frames(40))
        w.mark_checkpointed()
        self.assertEqual(w.pending, 0)


if __name__ == "__main__":
    unittest.main()
