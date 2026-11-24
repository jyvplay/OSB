#!/usr/bin/env python3
"""Independent differential oracle for PngChunkReader (format/image).

Parses PNG signature and chunk structure (length, type, CRC) without
decompressing pixel data. Cross-checks against the Kotlin contract in
format/image/src/main/kotlin/dev/fusion/image/PngChunkReader.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import struct
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_INPUT = 1 << 20
MAX_CHUNKS = 1024
MAX_CHUNK_LEN = 1 << 16
SIGNATURE = b"\x89PNG\r\n\x1a\n"


def _chunk(chunk_type: bytes, data: bytes) -> bytes:
    return len(data).to_bytes(4, "big") + chunk_type + data + b"\x00" * 4


def read(input_bytes: bytes):
    assert len(input_bytes) <= MAX_INPUT, f"input exceeds {MAX_INPUT} bytes"
    sig_valid = input_bytes[:8] == SIGNATURE
    offset = 8 if sig_valid else 0
    chunks = []
    while offset + 8 <= len(input_bytes):
        if len(chunks) >= MAX_CHUNKS:
            break
        length = struct.unpack(">I", input_bytes[offset:offset + 4])[0]
        assert 0 <= length <= MAX_CHUNK_LEN, "chunk length out of bounds"
        type_start = offset + 4
        type_end = type_start + 4
        if type_end + 4 > len(input_bytes):
            break
        type_bytes = input_bytes[type_start:type_end]
        if not _valid_type(type_bytes):
            break
        chunk_type = type_bytes.decode("ascii")
        data_start = type_end
        data_end = data_start + length
        crc_start = data_end
        if crc_start + 4 > len(input_bytes):
            break
        crc = struct.unpack(">I", input_bytes[crc_start:crc_start + 4])[0]
        critical = type_bytes[0] in b"ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        chunks.append((chunk_type, data_start, data_end, crc, critical))
        offset = crc_start + 4
    return (sig_valid, chunks)


def _valid_type(b: bytes) -> bool:
    if len(b) != 4:
        return False
    return all(65 <= c <= 90 or 97 <= c <= 122 for c in b)


def chunk_count(input_bytes: bytes):
    return len(read(input_bytes)[1])


class PngChunkReaderTest(unittest.TestCase):
    def test_signature_valid(self):
        # IHDR chunk: length=13, type=IHDR, 13 data bytes, 4 CRC bytes
        ihdr = _chunk(b"IHDR", b"\x00" * 13)
        sig_valid, chunks = read(SIGNATURE + ihdr)
        self.assertTrue(sig_valid)
        self.assertEqual(len(chunks), 1)
        self.assertEqual(chunks[0][0], "IHDR")
        self.assertTrue(chunks[0][4])  # critical

    def test_signature_invalid(self):
        sig_valid, chunks = read(b"notapng")  # < 8 bytes, no chunks possible
        self.assertFalse(sig_valid)
        self.assertEqual(chunks, [])

    def test_ancillary_chunk(self):
        # "tEXt" is ancillary (first letter lowercase)
        text_chunk = _chunk(b"tEXt", b"title\x00value")
        sig_valid, chunks = read(SIGNATURE + text_chunk)
        self.assertTrue(sig_valid)
        self.assertEqual(len(chunks), 1)
        self.assertEqual(chunks[0][0], "tEXt")
        self.assertFalse(chunks[0][4])  # ancillary

    def test_multiple_chunks(self):
        chunk1 = _chunk(b"IHDR", b"\x00" * 13)
        chunk2 = _chunk(b"tEXt", b"title\x00value")
        sig_valid, chunks = read(SIGNATURE + chunk1 + chunk2)
        self.assertEqual(len(chunks), 2)
        self.assertEqual(chunks[0][0], "IHDR")
        self.assertEqual(chunks[1][0], "tEXt")

    def test_empty_input(self):
        sig_valid, chunks = read(b"")
        self.assertFalse(sig_valid)
        self.assertEqual(chunks, [])


if __name__ == "__main__":
    unittest.main()
