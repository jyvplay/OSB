#!/usr/bin/env python3
"""Differential oracle for VaultAutoLockTimeout (feature/vault)."""
import unittest


def evaluate(last_activity_ms: int, now_ms: int, timeout_ms: int):
    rem = timeout_ms - (now_ms - last_activity_ms)
    if rem <= 0:
        return True, 0
    return False, rem


class VaultAutoLockTimeoutTest(unittest.TestCase):
    def test_evaluate(self):
        self.assertEqual(evaluate(1000, 1500, 1000), (False, 500))
        self.assertEqual(evaluate(1000, 2000, 1000), (True, 0))


if __name__ == "__main__":
    unittest.main()
