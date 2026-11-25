#!/usr/bin/env python3
"""Differential oracle for VaultClipboardClearDelay (feature/vault)."""
import unittest


def clamp_ms(d: int) -> int:
    return max(5_000, min(300_000, d))


class VaultClipboardClearDelayTest(unittest.TestCase):
    def test_clamp(self):
        self.assertEqual(clamp_ms(100), 5_000)
        self.assertEqual(clamp_ms(999_999), 300_000)


if __name__ == "__main__":
    unittest.main()
