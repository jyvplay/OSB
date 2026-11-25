#!/usr/bin/env python3
"""Differential oracle for BeamPeerPortValidator (feature/beam)."""
import unittest


def is_valid_port(p: int) -> bool:
    return 1024 <= p <= 65535


class BeamPeerPortValidatorTest(unittest.TestCase):
    def test_port(self):
        self.assertTrue(is_valid_port(8080))
        self.assertFalse(is_valid_port(80))


if __name__ == "__main__":
    unittest.main()
