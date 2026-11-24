#!/usr/bin/env python3
"""Differential oracle for CsrfTokenValidator (core/security)."""
import hmac
import unittest


def validate(expected: str, provided: str, expires_at_ms: int, now_ms: int) -> bool:
    assert 16 <= len(expected) <= 256
    assert now_ms >= 0 and expires_at_ms >= 0
    if now_ms >= expires_at_ms:
        return False
    if not (16 <= len(provided) <= 256):
        return False
    return hmac.compare_digest(expected.encode(), provided.encode())


class CsrfTokenValidatorTest(unittest.TestCase):
    def test_validate(self):
        tok = "a" * 32
        self.assertTrue(validate(tok, tok, 1000, 500))
        self.assertFalse(validate(tok, tok, 1000, 1000))
        self.assertFalse(validate(tok, "b" * 32, 1000, 500))


if __name__ == "__main__":
    unittest.main()
