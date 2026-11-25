#!/usr/bin/env python3
"""Differential oracle for CaptureFpsFramePacer (feature/capture)."""
import unittest


def is_frame_dropped(delta_ns: int, fps: int) -> bool:
    return delta_ns > (1_000_000_000 // fps)


class CaptureFpsFramePacerTest(unittest.TestCase):
    def test_pacer(self):
        self.assertFalse(is_frame_dropped(16_000_000, 60))
        self.assertTrue(is_frame_dropped(17_000_000, 60))


if __name__ == "__main__":
    unittest.main()
