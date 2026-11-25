#!/usr/bin/env python3
"""Differential oracle for ContinuityPairCodeVerifier (feature/continuity)."""
import hmac
import unittest


class ContinuityPairCodeVerifier:
    def __init__(self, pin: str, max_attempts: int = 5):
        self.pin = pin
        self.max_attempts = max_attempts
        self.attempts = 0
        self.locked = False

    def verify(self, candidate: str) -> bool:
        if self.locked:
            return False
        self.attempts += 1
        if self.attempts >= self.max_attempts and candidate != self.pin:
            self.locked = True
            return False
        return hmac.compare_digest(self.pin.encode(), candidate.encode())


class ContinuityPairCodeVerifierTest(unittest.TestCase):
    def test_verify_and_lock(self):
        v = ContinuityPairCodeVerifier("123456", 2)
        self.assertFalse(v.verify("000000"))
        self.assertFalse(v.verify("111111"))
        self.assertTrue(v.locked)
        self.assertFalse(v.verify("123456"))


if __name__ == "__main__":
    unittest.main()
