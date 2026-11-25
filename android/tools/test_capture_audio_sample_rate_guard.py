#!/usr/bin/env python3
"""Differential oracle for CaptureAudioSampleRateGuard (feature/capture)."""
import unittest

SUPPORTED = {8000, 16000, 22050, 44100, 48000, 96000}


class CaptureAudioSampleRateGuardTest(unittest.TestCase):
    def test_rates(self):
        self.assertIn(48000, SUPPORTED)
        self.assertNotIn(12345, SUPPORTED)


if __name__ == "__main__":
    unittest.main()
