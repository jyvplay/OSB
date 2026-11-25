#!/usr/bin/env python3
"""Differential oracle for ImagePngChunkWalker (format/image)."""
import struct
import unittest


def walk_chunks(data: bytes):
    pos, out = 8, []
    while pos + 12 <= len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        ctype = data[pos + 4 : pos + 8].decode("ascii")
        out.append((ctype, length))
        pos += 12 + length
        if ctype == "IEND":
            break
    return out


class ImagePngChunkWalkerTest(unittest.TestCase):
    def test_walk(self):
        hdr = b"\x89PNG\r\n\x1a\n"
        ihdr = struct.pack(">I4s", 13, b"IHDR") + b"\x00" * 17
        iend = struct.pack(">I4s", 0, b"IEND") + b"\x00" * 4
        self.assertEqual(walk_chunks(hdr + ihdr + iend), [("IHDR", 13), ("IEND", 0)])


if __name__ == "__main__":
    unittest.main()
