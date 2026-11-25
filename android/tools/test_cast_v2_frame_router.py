#!/usr/bin/env python3
"""Differential oracle for CastV2FrameRouter (cast/castv2)."""
import struct
import unittest


def encode_frame(payload: bytes) -> bytes:
    return struct.pack(">I", len(payload)) + payload


def decode_len(hdr: bytes) -> int:
    return struct.unpack(">I", hdr)[0]


class CastV2FrameRouterTest(unittest.TestCase):
    def test_frame(self):
        f = encode_frame(b"hello")
        self.assertEqual(decode_len(f[:4]), 5)


if __name__ == "__main__":
    unittest.main()
