#!/usr/bin/env python3
"""Independent differential oracle for CastV2Channel (cast/castv2).

Parses CastV2 protocol frames: source id, destination id, namespace, payload
with 2-byte length-prefixed fields. Cross-checks against the Kotlin contract
in cast/castv2/src/main/kotlin/dev/fusion/castv2/CastV2Channel.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_INPUT = 1 << 16
MAX_FRAMES = 256
MAX_FIELD_LEN = 4096
NS_RE = __import__("re").compile(r"urn:x-cast:[a-z0-9.]+")


def parse(input_bytes: bytes):
    assert len(input_bytes) <= MAX_INPUT, f"input exceeds {MAX_INPUT} bytes"
    frames = []
    offset = 0
    while offset < len(input_bytes):
        if len(frames) >= MAX_FRAMES:
            break
        if offset + 4 > len(input_bytes):
            break
        frame_len = int.from_bytes(input_bytes[offset:offset + 4], "big")
        assert frame_len > 0 and frame_len <= MAX_INPUT, "invalid frame length"
        offset += 4
        if offset + frame_len > len(input_bytes):
            break
        frame_bytes = input_bytes[offset:offset + frame_len]
        offset += frame_len
        fo = 0
        def read_lp(data, off):
            if off + 2 > len(data):
                return None
            ln = int.from_bytes(data[off:off + 2], "big")
            if ln < 0 or off + 2 + ln > len(data):
                return None
            return (data[off + 2:off + 2 + ln], off + 2 + ln)
        src = read_lp(frame_bytes, fo)
        if src is None: break
        fo = src[1]
        dst = read_lp(frame_bytes, fo)
        if dst is None: break
        fo = dst[1]
        ns = read_lp(frame_bytes, fo)
        if ns is None: break
        fo = ns[1]
        pl = read_lp(frame_bytes, fo)
        if pl is None: break
        ns_str = ns[0].decode("utf-8")
        assert NS_RE.fullmatch(ns_str), f"invalid namespace: {ns_str}"
        frames.append((
            src[0].decode("utf-8"),
            dst[0].decode("utf-8"),
            ns_str,
            pl[0].decode("utf-8"),
        ))
    return frames


class CastV2ChannelTest(unittest.TestCase):
    def test_single_frame(self):
        src = b"sender-0"
        dst = b"receiver-0"
        ns = b"urn:x-cast:com.example.channel"
        pl = b"hello"
        frame = (
            len(src).to_bytes(2, "big") + src +
            len(dst).to_bytes(2, "big") + dst +
            len(ns).to_bytes(2, "big") + ns +
            len(pl).to_bytes(2, "big") + pl
        )
        data = len(frame).to_bytes(4, "big") + frame
        frames = parse(data)
        self.assertEqual(len(frames), 1)
        self.assertEqual(frames[0][0], "sender-0")
        self.assertEqual(frames[0][1], "receiver-0")
        self.assertEqual(frames[0][2], "urn:x-cast:com.example.channel")
        self.assertEqual(frames[0][3], "hello")

    def test_invalid_namespace(self):
        src = b"s"
        dst = b"r"
        ns = b"invalid-namespace"
        pl = b"x"
        frame = (
            len(src).to_bytes(2, "big") + src +
            len(dst).to_bytes(2, "big") + dst +
            len(ns).to_bytes(2, "big") + ns +
            len(pl).to_bytes(2, "big") + pl
        )
        data = len(frame).to_bytes(4, "big") + frame
        with self.assertRaises(AssertionError):
            parse(data)

    def test_empty_input(self):
        self.assertEqual(parse(b""), [])


if __name__ == "__main__":
    unittest.main()
