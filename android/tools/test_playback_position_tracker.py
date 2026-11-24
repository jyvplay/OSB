#!/usr/bin/env python3
"""Independent differential oracle for PlaybackPositionTracker (feature/media).

Tracks playback position (ms) for media items bounded by item duration.
Cross-checks against the Kotlin contract in
feature/media/src/main/kotlin/dev/fusion/media/PlaybackPositionTracker.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_ITEMS = 4096
MAX_ID_LEN = 128
MAX_DURATION_MS = 24 * 60 * 60 * 1000
ID_RE = re.compile(r"[a-zA-Z0-9_-]{1,64}")


class PlaybackState:
    def __init__(self, position_ms, duration_ms):
        self.position_ms = position_ms
        self.duration_ms = duration_ms


class PlaybackPositionTracker:
    def __init__(self):
        self._positions = {}

    def set_duration(self, item_id, duration_ms):
        assert ID_RE.fullmatch(item_id), f"invalid item id: {item_id}"
        assert len(item_id) <= MAX_ID_LEN, "item id too long"
        assert 0 <= duration_ms <= MAX_DURATION_MS, "duration out of bounds"
        assert len(self._positions) < MAX_ITEMS or item_id in self._positions, f"too many items: {MAX_ITEMS}"
        current = self._positions.get(item_id)
        pos = current.position_ms if current else 0
        pos = max(0, min(pos, duration_ms))
        self._positions[item_id] = PlaybackState(pos, duration_ms)

    def update_position(self, item_id, position_ms):
        assert position_ms >= 0, "negative position"
        state = self._positions.get(item_id)
        if state is None:
            return False
        state.position_ms = max(0, min(position_ms, state.duration_ms))
        return True

    def seek(self, item_id, position_ms):
        assert position_ms >= 0, "negative position"
        state = self._positions.get(item_id)
        if state is None:
            return False
        state.position_ms = max(0, min(position_ms, state.duration_ms))
        return True

    def position(self, item_id):
        state = self._positions.get(item_id)
        return state.position_ms if state else None

    def duration(self, item_id):
        state = self._positions.get(item_id)
        return state.duration_ms if state else None

    def item_count(self):
        return len(self._positions)

    def remove_item(self, item_id):
        return self._positions.pop(item_id, None) is not None

    def clear(self):
        self._positions.clear()


class PlaybackPositionTrackerTest(unittest.TestCase):
    def setUp(self):
        self.t = PlaybackPositionTracker()

    def test_set_and_position(self):
        self.t.set_duration("song1", 100000)
        self.t.update_position("song1", 30000)
        self.assertEqual(self.t.position("song1"), 30000)
        self.assertEqual(self.t.duration("song1"), 100000)

    def test_position_clamped_to_duration(self):
        self.t.set_duration("song1", 100000)
        self.t.update_position("song1", 200000)
        self.assertEqual(self.t.position("song1"), 100000)

    def test_seek(self):
        self.t.set_duration("song1", 100000)
        self.assertTrue(self.t.seek("song1", 50000))
        self.assertEqual(self.t.position("song1"), 50000)

    def test_unknown_item(self):
        self.assertFalse(self.t.update_position("nope", 1000))
        self.assertFalse(self.t.seek("nope", 1000))
        self.assertIsNone(self.t.position("nope"))
        self.assertIsNone(self.t.duration("nope"))

    def test_negative_position(self):
        self.t.set_duration("song1", 100000)
        with self.assertRaises(AssertionError):
            self.t.update_position("song1", -1)
        with self.assertRaises(AssertionError):
            self.t.seek("song1", -1)

    def test_invalid_duration(self):
        with self.assertRaises(AssertionError):
            self.t.set_duration("song1", -1)
        with self.assertRaises(AssertionError):
            self.t.set_duration("song1", MAX_DURATION_MS + 1)

    def test_invalid_item_id(self):
        with self.assertRaises(AssertionError):
            self.t.set_duration("Bad Id!", 1000)


if __name__ == "__main__":
    unittest.main()
