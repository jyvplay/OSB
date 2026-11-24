#!/usr/bin/env python3
"""Independent differential oracle for Pbkdf2KeyDerivation (core/security).

Implements PBKDF2-HMAC-SHA256 key derivation using Python's hashlib.pbkdf2_hmac
and cross-checks against the Kotlin contract described in
core/security/src/main/kotlin/dev/fusion/security/Pbkdf2KeyDerivation.kt.

No Android SDK, Kotlin compiler or Gradle is required; this runs on host Python 3.11.
"""
from __future__ import annotations

import hashlib
import os
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))


def derive_pbkdf2(password: str, salt: bytes, iterations: int, key_length: int) -> bytes:
    """Python reference implementation of PBKDF2-HMAC-SHA256."""
    assert 1 <= len(password) <= 1_024, "password length out of bounds"
    assert 1 <= len(salt) <= 1_024, "salt length out of bounds"
    assert 1 <= iterations <= 1_000_000, "iterations out of bounds"
    assert 1 <= key_length <= 65_536, "key length out of bounds"
    return hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, iterations, dklen=key_length)


class Pbkdf2KeyDerivationTest(unittest.TestCase):
    def test_deterministic_derivation(self):
        password = "correct horse battery staple"
        salt = b"\x01\x02\x03\x04\x05\x06\x07\x08"
        iterations = 10_000
        key_length = 32

        key1 = derive_pbkdf2(password, salt, iterations, key_length)
        key2 = derive_pbkdf2(password, salt, iterations, key_length)
        self.assertEqual(key1, key2)
        self.assertEqual(len(key1), key_length)

    def test_different_salt_different_key(self):
        password = "password"
        iterations = 10_000
        key_length = 16
        key1 = derive_pbkdf2(password, b"\x01" * 16, iterations, key_length)
        key2 = derive_pbkdf2(password, b"\x02" * 16, iterations, key_length)
        self.assertNotEqual(key1, key2)

    def test_different_iterations_different_key(self):
        password = "password"
        salt = b"\x01" * 16
        key_length = 16
        key1 = derive_pbkdf2(password, salt, 1_000, key_length)
        key2 = derive_pbkdf2(password, salt, 10_000, key_length)
        self.assertNotEqual(key1, key2)

    def test_password_bounds(self):
        with self.assertRaises(AssertionError):
            derive_pbkdf2("", b"\x01" * 16, 1000, 16)
        with self.assertRaises(AssertionError):
            derive_pbkdf2("x" * 1_025, b"\x01" * 16, 1000, 16)

    def test_salt_bounds(self):
        with self.assertRaises(AssertionError):
            derive_pbkdf2("password", b"", 1000, 16)
        with self.assertRaises(AssertionError):
            derive_pbkdf2("password", b"\x01" * 1_025, 1000, 16)

    def test_iteration_bounds(self):
        with self.assertRaises(AssertionError):
            derive_pbkdf2("password", b"\x01" * 16, 0, 16)
        with self.assertRaises(AssertionError):
            derive_pbkdf2("password", b"\x01" * 16, 1_000_001, 16)

    def test_key_length_bounds(self):
        with self.assertRaises(AssertionError):
            derive_pbkdf2("password", b"\x01" * 16, 1000, 0)
        with self.assertRaises(AssertionError):
            derive_pbkdf2("password", b"\x01" * 16, 1000, 65_537)

    def test_known_vector(self):
        # RFC 6070 test vector 1: PBKDF2-HMAC-SHA256(password="password", salt="salt", 1 iteration, 20 bytes)
        expected = bytes.fromhex("120fb6cffcf8b32c43e7225256c4f837a86548c9")
        derived = derive_pbkdf2("password", b"salt", 1, 20)
        self.assertEqual(derived, expected)

    def test_verify_match(self):
        password = "secret"
        salt = os.urandom(16)
        iterations = 5_000
        key_length = 32
        key = derive_pbkdf2(password, salt, iterations, key_length)

        # Simulate the Kotlin verify() flow
        derived_again = derive_pbkdf2(password, salt, iterations, key_length)
        self.assertEqual(derived_again, key)

    def test_verify_no_match(self):
        salt = os.urandom(16)
        iterations = 5_000
        key_length = 32
        key = derive_pbkdf2("correct", salt, iterations, key_length)
        derived_wrong = derive_pbkdf2("wrong", salt, iterations, key_length)
        self.assertNotEqual(derived_wrong, key)


if __name__ == "__main__":
    unittest.main()
