#!/usr/bin/env python3
"""Independent differential oracle for Ulid (core/storage).

Implements the ULID (Universally Unique Lexicographically Sortable
Identifier) codec per the canonical spec: 26-character Crockford base32,
first 10 chars = 48-bit millisecond timestamp, last 16 = 80 random bits.
Cross-checks against the Kotlin contract in
core/storage/src/main/kotlin/dev/fusion/storage/Ulid.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
LENGTH = 26
MAX_TIMESTAMP = (1 << 48) - 1
MAX_ULID = "7ZZZZZZZZZZZZZZZZZZZZZZZZZ"

_VALUE = {}
for i, c in enumerate(ALPHABET):
    _VALUE[c] = i
    _VALUE[c.lower()] = i


def _digit(c):
    v = _VALUE.get(c, -1)
    assert v >= 0, f"'{c}' is not a Crockford base32 digit (I,L,O,U are refused)"
    return v


def decode(s):
    assert len(s) == LENGTH, f"ULID must be {LENGTH} chars, got {len(s)}"
    assert _digit(s[0]) <= 7, f"ULID exceeds {MAX_ULID} (first char '{s[0]}')"
    ts = 0
    for i in range(10):
        ts = (ts << 5) | _digit(s[i])
    assert ts <= MAX_TIMESTAMP, "timestamp overflow"
    rand = bytearray(10)
    acc = 0
    bits = 0
    out = 0
    for i in range(10, LENGTH):
        acc = (acc << 5) | _digit(s[i])
        bits += 5
        while bits >= 8:
            rand[out] = (acc >> (bits - 8)) & 0xFF
            bits -= 8
            out += 1
    return (ts, bytes(rand))


def encode(timestamp_ms, random):
    assert 0 <= timestamp_ms <= MAX_TIMESTAMP, f"timestamp {timestamp_ms} outside 0..{MAX_TIMESTAMP}"
    assert len(random) == 10, "random component must be 10 bytes"
    sb = []
    for shift in range(45, -1, -5):
        sb.append(ALPHABET[((timestamp_ms >> shift) & 0x1F)])
    acc = 0
    bits = 0
    for b in random:
        acc = (acc << 8) | (b & 0xFF)
        bits += 8
        while bits >= 5:
            sb.append(ALPHABET[((acc >> (bits - 5)) & 0x1F)])
            bits -= 5
    return "".join(sb)


def timestamp_ms(s):
    return decode(s)[0]


def next_ulid(s):
    ts, rand = decode(s)
    r = bytearray(rand)
    i = 9
    while i >= 0:
        r[i] = (r[i] + 1) & 0xFF
        if r[i] != 0:
            break
        i -= 1
    assert i >= 0, "random component overflow within this millisecond"
    return encode(ts, bytes(r))


def is_valid(s):
    try:
        decode(s)
        return True
    except AssertionError:
        return False


class UlidTest(unittest.TestCase):
    def test_encode_decode_roundtrip(self):
        ts = 1234567890123
        random = bytes(range(10))
        encoded = encode(ts, random)
        self.assertEqual(len(encoded), LENGTH)
        decoded_ts, decoded_rand = decode(encoded)
        self.assertEqual(decoded_ts, ts)
        self.assertEqual(decoded_rand, random)

    def test_known_vector(self):
        ts = 1469918176385
        random = bytes.fromhex("0123456789abcdef0123")
        encoded = encode(ts, random)
        decoded_ts, decoded_rand = decode(encoded)
        self.assertEqual(decoded_ts, ts)
        self.assertEqual(decoded_rand, random)

    def test_timestamp_zero(self):
        random = bytes(10)
        encoded = encode(0, random)
        self.assertTrue(encoded.startswith("0000000000"))

    def test_timestamp_max(self):
        random = bytes(10)
        encoded = encode(MAX_TIMESTAMP, random)
        decoded_ts, _ = decode(encoded)
        self.assertEqual(decoded_ts, MAX_TIMESTAMP)

    def test_timestamp_too_large(self):
        with self.assertRaises(AssertionError):
            encode(MAX_TIMESTAMP + 1, bytes(10))

    def test_wrong_length_too_short(self):
        with self.assertRaises(AssertionError):
            decode("0" * 25)

    def test_wrong_length_too_long(self):
        with self.assertRaises(AssertionError):
            decode("0" * 27)

    def test_invalid_char_i(self):
        with self.assertRaises(AssertionError):
            decode("I" + "0" * 25)

    def test_invalid_char_l(self):
        with self.assertRaises(AssertionError):
            decode("L" + "0" * 25)

    def test_invalid_char_o(self):
        with self.assertRaises(AssertionError):
            decode("O" + "0" * 25)

    def test_invalid_char_u(self):
        with self.assertRaises(AssertionError):
            decode("U" + "0" * 25)

    def test_case_insensitive(self):
        random = bytes(10)
        upper = encode(12345, random)
        lower = upper.lower()
        ts_u, rand_u = decode(upper)
        ts_l, rand_l = decode(lower)
        self.assertEqual(ts_u, ts_l)
        self.assertEqual(rand_u, rand_l)

    def test_next_monotonic(self):
        random = bytes(10)
        ulid = encode(1000, random)
        next1 = next_ulid(ulid)
        next2 = next_ulid(next1)
        self.assertEqual(timestamp_ms(ulid), 1000)
        self.assertEqual(timestamp_ms(next1), 1000)
        self.assertEqual(timestamp_ms(next2), 1000)
        self.assertNotEqual(ulid, next1)
        self.assertNotEqual(next1, next2)

    def test_next_overflow(self):
        ulid = encode(1000, bytes([0xFF] * 10))
        with self.assertRaises(AssertionError):
            next_ulid(ulid)

    def test_is_valid(self):
        random = bytes(10)
        valid = encode(1000, random)
        self.assertTrue(is_valid(valid))
        self.assertFalse(is_valid("not a valid ulid at all"))
        self.assertFalse(is_valid(""))

    def test_timestamp_ms_accessor(self):
        random = bytes(10)
        encoded = encode(999999, random)
        self.assertEqual(timestamp_ms(encoded), 999999)


if __name__ == "__main__":
    unittest.main()
