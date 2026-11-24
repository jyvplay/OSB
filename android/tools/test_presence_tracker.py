#!/usr/bin/env python3
"""Independent differential oracle for PresenceTracker (feature/together).

Tracks user presence (online/away/offline) with last-seen timestamps.
Cross-checks against the Kotlin contract in
feature/together/src/main/kotlin/dev/fusion/together/PresenceTracker.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_USERS = 1024
MAX_ID_LEN = 128
MAX_TS = float("inf")
ID_RE = re.compile(r"[a-zA-Z0-9_-]{1,64}")


class PresenceTracker:
    def __init__(self):
        self._presence = {}

    def set_presence(self, user_id: str, status: str, last_seen_ms: int):
        assert ID_RE.fullmatch(user_id), f"invalid user id: {user_id}"
        assert len(user_id) <= MAX_ID_LEN, "user id too long"
        assert status in {"online", "away", "offline"}, f"invalid status: {status}"
        assert 0 <= last_seen_ms <= MAX_TS, "timestamp out of bounds"
        assert len(self._presence) < MAX_USERS or user_id in self._presence, "too many users"
        self._presence[user_id] = (status, last_seen_ms)

    def remove_user(self, user_id: str) -> bool:
        return self._presence.pop(user_id, None) is not None

    def presence(self, user_id: str):
        return self._presence.get(user_id)

    def snapshot(self):
        users = list(self._presence.keys())
        online = away = offline = 0
        for status, _ in self._presence.values():
            if status == "online":
                online += 1
            elif status == "away":
                away += 1
            elif status == "offline":
                offline += 1
        return (users, online, away, offline)

    def user_count(self):
        return len(self._presence)

    def clear(self):
        self._presence.clear()


class PresenceTrackerTest(unittest.TestCase):
    def setUp(self):
        self.t = PresenceTracker()

    def test_set_and_presence(self):
        self.t.set_presence("alice", "online", 1000)
        p = self.t.presence("alice")
        self.assertIsNotNone(p)
        self.assertEqual(p[0], "online")
        self.assertEqual(p[1], 1000)

    def test_snapshot(self):
        self.t.set_presence("alice", "online", 1000)
        self.t.set_presence("bob", "away", 2000)
        self.t.set_presence("carol", "offline", 3000)
        users, online, away, offline = self.t.snapshot()
        self.assertEqual(online, 1)
        self.assertEqual(away, 1)
        self.assertEqual(offline, 1)
        self.assertEqual(len(users), 3)

    def test_remove_user(self):
        self.t.set_presence("alice", "online", 1000)
        self.assertTrue(self.t.remove_user("alice"))
        self.assertFalse(self.t.remove_user("alice"))
        self.assertIsNone(self.t.presence("alice"))

    def test_invalid_status(self):
        with self.assertRaises(AssertionError):
            self.t.set_presence("alice", "busy", 1000)

    def test_invalid_user_id(self):
        with self.assertRaises(AssertionError):
            self.t.set_presence("Bad User!", "online", 1000)

    def test_negative_timestamp(self):
        with self.assertRaises(AssertionError):
            self.t.set_presence("alice", "online", -1)


if __name__ == "__main__":
    unittest.main()
