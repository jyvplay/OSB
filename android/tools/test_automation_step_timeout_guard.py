#!/usr/bin/env python3
"""Differential oracle for AutomationStepTimeoutGuard (feature/automation)."""
import unittest


def clamp_ms(ms: int) -> int:
    return max(100, min(60_000, ms))


class AutomationStepTimeoutGuardTest(unittest.TestCase):
    def test_clamp(self):
        self.assertEqual(clamp_ms(10), 100)
        self.assertEqual(clamp_ms(99_999), 60_000)


if __name__ == "__main__":
    unittest.main()
