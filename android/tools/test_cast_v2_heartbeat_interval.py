#!/usr/bin/env python3
"""Differential oracle for CastV2HeartbeatInterval (cast/castv2)."""
import unittest


def clamp_ms(ms: int) -> int:
    return max(1_000, min(60_000, ms))


class CastV2HeartbeatIntervalTest(unittest.TestCase):
    def test_clamp(self):
        self.assertEqual(clamp_ms(200), 1_000)
        self.assertEqual(clamp_ms(120_000), 60_000)


if __name__ == "__main__":
    unittest.main()
