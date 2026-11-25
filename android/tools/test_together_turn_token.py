#!/usr/bin/env python3
"""Differential oracle for TogetherTurnToken (feature/together)."""
import unittest


class TogetherTurnToken:
    def __init__(self):
        self.holder = None

    def claim(self, peer_id: str) -> bool:
        if self.holder is not None and self.holder != peer_id:
            return False
        self.holder = peer_id
        return True

    def release(self, peer_id: str) -> bool:
        if self.holder != peer_id:
            return False
        self.holder = None
        return True


class TogetherTurnTokenTest(unittest.TestCase):
    def test_turn_token(self):
        t = TogetherTurnToken()
        self.assertTrue(t.claim("p1"))
        self.assertFalse(t.claim("p2"))
        self.assertFalse(t.release("p2"))
        self.assertTrue(t.release("p1"))
        self.assertTrue(t.claim("p2"))


if __name__ == "__main__":
    unittest.main()
