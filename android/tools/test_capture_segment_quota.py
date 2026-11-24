#!/usr/bin/env python3
"""Independent differential oracle for CaptureSegmentQuota (feature/capture).

Tracks capture segment sizes against a total quota, refusing segments that
would exceed it. Cross-checks against the Kotlin contract in
feature/capture/src/main/kotlin/dev/fusion/capture/CaptureSegmentQuota.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_SEGMENTS = 4096
MAX_ID_LEN = 128
MAX_SEGMENT_BYTES = 1 << 30
MAX_QUOTA_BYTES = 1 << 40
ID_RE = re.compile(r"[a-zA-Z0-9_-]{1,64}")


class CaptureSegmentQuota:
    def __init__(self, quota_bytes):
        assert 0 <= quota_bytes <= MAX_QUOTA_BYTES, "quota out of bounds"
        self._quota = quota_bytes
        self._segments = {}

    def register_segment(self, segment_id, size_bytes):
        assert ID_RE.fullmatch(segment_id), f"invalid segment id: {segment_id}"
        assert len(segment_id) <= MAX_ID_LEN, "segment id too long"
        assert 0 <= size_bytes <= MAX_SEGMENT_BYTES, "segment size out of bounds"
        assert len(self._segments) < MAX_SEGMENTS or segment_id in self._segments, f"too many segments: {MAX_SEGMENTS}"
        current_total = sum(self._segments.values())
        if current_total + size_bytes > self._quota:
            return False
        self._segments[segment_id] = size_bytes
        return True

    def remove_segment(self, segment_id):
        return self._segments.pop(segment_id, None) is not None

    def used_bytes(self):
        return sum(self._segments.values())

    def remaining_bytes(self):
        return self._quota - self.used_bytes()

    def segment_count(self):
        return len(self._segments)

    def is_quota_exceeded(self):
        return self.used_bytes() > self._quota

    def clear(self):
        self._segments.clear()


class CaptureSegmentQuotaTest(unittest.TestCase):
    def setUp(self):
        self.q = CaptureSegmentQuota(1000)

    def test_register_within_quota(self):
        self.assertTrue(self.q.register_segment("seg1", 300))
        self.assertTrue(self.q.register_segment("seg2", 400))
        self.assertEqual(self.q.used_bytes(), 700)
        self.assertEqual(self.q.remaining_bytes(), 300)

    def test_register_exceeds_quota(self):
        self.q.register_segment("seg1", 600)
        self.assertTrue(self.q.register_segment("seg2", 400))  # exactly 1000
        self.assertFalse(self.q.register_segment("seg3", 1))  # would exceed

    def test_remove_segment(self):
        self.q.register_segment("seg1", 300)
        self.assertTrue(self.q.remove_segment("seg1"))
        self.assertFalse(self.q.remove_segment("seg1"))
        self.assertEqual(self.q.used_bytes(), 0)

    def test_invalid_segment_id(self):
        with self.assertRaises(AssertionError):
            self.q.register_segment("Bad Segment", 100)

    def test_segment_size_out_of_bounds(self):
        with self.assertRaises(AssertionError):
            self.q.register_segment("seg1", MAX_SEGMENT_BYTES + 1)
        with self.assertRaises(AssertionError):
            self.q.register_segment("seg1", -1)

    def test_invalid_quota(self):
        with self.assertRaises(AssertionError):
            CaptureSegmentQuota(-1)
        with self.assertRaises(AssertionError):
            CaptureSegmentQuota(MAX_QUOTA_BYTES + 1)


if __name__ == "__main__":
    unittest.main()
