#!/usr/bin/env python3
"""Differential oracle for Leb128VarintCodec (format/native)."""
import unittest


def encode_leb128(val: int) -> bytes:
    assert val >= 0
    out = []
    while True:
        b = val & 0x7F
        val >>= 7
        if val != 0:
            b |= 0x80
        out.append(b)
        if val == 0:
            break
    return bytes(out)


def decode_leb128(data: bytes) -> int:
    res = 0
    shift = 0
    for b in data:
        res |= (b & 0x7F) << shift
        if (b & 0x80) == 0:
            return res
        shift += 7
    raise ValueError("unterminated")


class Leb128VarintCodecTest(unittest.TestCase):
    def test_roundtrip(self):
        for v in (0, 1, 127, 128, 624485, (1 << 32) + 7):
            self.assertEqual(decode_leb128(encode_leb128(v)), v)


if __name__ == "__main__":
    unittest.main()
