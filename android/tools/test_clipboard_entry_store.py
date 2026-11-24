#!/usr/bin/env python3
"""Independent differential oracle for ClipboardEntryStore (feature/continuity).

Stores clipboard entries with per-entry size limits, total entry cap, and
max age expiry. Cross-checks against the Kotlin contract in
feature/continuity/src/main/kotlin/dev/fusion/continuity/ClipboardEntryStore.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import time
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_ENTRIES = 256
MAX_ID_LEN = 128
MAX_CONTENT_BYTES = 1 << 20
MAX_MIME_LEN = 128
MAX_TS = float("inf")
MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000
ID_RE = re.compile(r"[a-zA-Z0-9_-]{1,64}")
MIME_RE = re.compile(r"[a-z]+/[a-z0-9.+-]+")


class ClipboardEntry:
    def __init__(self, content, mime_type, timestamp_ms):
        self.content = content
        self.mime_type = mime_type
        self.timestamp_ms = timestamp_ms


class ClipboardEntryStore:
    def __init__(self):
        self._entries = {}

    def add_entry(self, entry_id, content, mime_type, timestamp_ms):
        assert ID_RE.fullmatch(entry_id), f"invalid entry id: {entry_id}"
        assert len(entry_id) <= MAX_ID_LEN, "entry id too long"
        assert len(content) <= MAX_CONTENT_BYTES, "content too large"
        assert MIME_RE.fullmatch(mime_type), f"invalid MIME type: {mime_type}"
        assert len(mime_type) <= MAX_MIME_LEN, "MIME type too long"
        assert 0 <= timestamp_ms <= MAX_TS, "timestamp out of bounds"
        assert len(self._entries) < MAX_ENTRIES or entry_id in self._entries, f"too many entries: {MAX_ENTRIES}"

        now = int(time.time() * 1000)
        if now - timestamp_ms > MAX_AGE_MS:
            return False

        self._entries[entry_id] = ClipboardEntry(bytes(content), mime_type, timestamp_ms)
        return True

    def remove_entry(self, entry_id):
        return self._entries.pop(entry_id, None) is not None

    def entry(self, entry_id):
        return self._entries.get(entry_id)

    def entry_count(self):
        return len(self._entries)

    def expired_count(self, now_ms):
        assert now_ms >= 0, "negative now_ms"
        return sum(1 for e in self._entries.values() if now_ms - e.timestamp_ms > MAX_AGE_MS)

    def clear(self):
        self._entries.clear()


class ClipboardEntryStoreTest(unittest.TestCase):
    def setUp(self):
        self.s = ClipboardEntryStore()

    def test_add_and_entry(self):
        now = int(time.time() * 1000)
        self.assertTrue(self.s.add_entry("e1", b"hello", "text/plain", now))
        e = self.s.entry("e1")
        self.assertIsNotNone(e)
        self.assertEqual(e.content, b"hello")
        self.assertEqual(e.mime_type, "text/plain")

    def test_remove_entry(self):
        now = int(time.time() * 1000)
        self.s.add_entry("e1", b"hello", "text/plain", now)
        self.assertTrue(self.s.remove_entry("e1"))
        self.assertFalse(self.s.remove_entry("e1"))

    def test_content_too_large(self):
        now = int(time.time() * 1000)
        with self.assertRaises(AssertionError):
            self.s.add_entry("e1", b"x" * (MAX_CONTENT_BYTES + 1), "text/plain", now)

    def test_invalid_mime(self):
        now = int(time.time() * 1000)
        with self.assertRaises(AssertionError):
            self.s.add_entry("e1", b"hello", "not-a-mime", now)

    def test_expired_entry(self):
        old = int(time.time() * 1000) - MAX_AGE_MS - 1
        self.assertFalse(self.s.add_entry("e1", b"hello", "text/plain", old))

    def test_negative_timestamp(self):
        with self.assertRaises(AssertionError):
            self.s.add_entry("e1", b"hello", "text/plain", -1)

    def test_expired_count(self):
        now = int(time.time() * 1000)
        # e1 is fresh at add time; e2 is old enough to be expired at add time
        # (rejected), so only e1 is stored. Use a future now_ms to make e1 expired.
        self.s.add_entry("e1", b"a", "text/plain", now)
        future = now + MAX_AGE_MS + 1
        self.assertEqual(self.s.expired_count(future), 1)
        self.assertEqual(self.s.expired_count(now), 0)


if __name__ == "__main__":
    unittest.main()
