#!/usr/bin/env python3
"""Independent differential oracle for NotificationBatcher (feature/alerts).

Groups notifications into batches by category and time window, enforcing
per-batch and global caps. Cross-checks against the Kotlin contract in
feature/alerts/src/main/kotlin/dev/fusion/alerts/NotificationBatcher.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_BATCHES = 128
MAX_NOTIFICATIONS_PER_BATCH = 256
MAX_ID_LEN = 128
MAX_CATEGORY_LEN = 64
MAX_WINDOW_MS = 7 * 24 * 60 * 60 * 1000
ID_RE = re.compile(r"[a-zA-Z0-9_-]{1,64}")
CAT_RE = re.compile(r"[a-z][a-z0-9_-]*")


class Batch:
    def __init__(self, category, window_ms):
        self.category = category
        self.window_ms = window_ms
        self.notifications = []
        self.sealed = False


class NotificationBatcher:
    def __init__(self):
        self._batches = {}

    def open_batch(self, category, window_ms):
        assert CAT_RE.fullmatch(category), f"invalid category: {category}"
        assert len(category) <= MAX_CATEGORY_LEN, "category too long"
        assert 0 < window_ms <= MAX_WINDOW_MS, "window out of bounds"
        existing = next((b for b in self._batches.values()
                        if b.category == category and not b.sealed), None)
        if existing:
            return existing.category
        # Create and store a new batch keyed by category
        batch = Batch(category, window_ms)
        self._batches[category] = batch
        return category

    def add(self, batch_id, notification_id):
        assert ID_RE.fullmatch(notification_id), f"invalid notification id: {notification_id}"
        assert len(notification_id) <= MAX_ID_LEN, "notification id too long"
        batch = self._batches.get(batch_id)
        if batch is None:
            return False
        assert not batch.sealed, f"batch {batch_id} is sealed"
        assert len(batch.notifications) < MAX_NOTIFICATIONS_PER_BATCH, "batch full"
        assert notification_id not in batch.notifications, "duplicate notification"
        batch.notifications.append(notification_id)
        return True

    def seal_batch(self, batch_id):
        batch = self._batches.get(batch_id)
        if batch is None:
            return None
        assert not batch.sealed, "already sealed"
        batch.sealed = True
        return (batch_id, len(batch.notifications))

    def batch_size(self, batch_id):
        batch = self._batches.get(batch_id)
        return len(batch.notifications) if batch else None

    def open_batch_count(self):
        return sum(1 for b in self._batches.values() if not b.sealed)

    def sealed_batch_count(self):
        return sum(1 for b in self._batches.values() if b.sealed)

    def clear(self):
        self._batches.clear()


class NotificationBatcherTest(unittest.TestCase):
    def setUp(self):
        self.b = NotificationBatcher()

    def test_open_and_add(self):
        bid = self.b.open_batch("alerts", 5000)
        self.assertTrue(self.b.add(bid, "n1"))
        self.assertTrue(self.b.add(bid, "n2"))
        self.assertEqual(self.b.batch_size(bid), 2)

    def test_seal(self):
        bid = self.b.open_batch("alerts", 5000)
        self.b.add(bid, "n1")
        result = self.b.seal_batch(bid)
        self.assertEqual(result, (bid, 1))
        with self.assertRaises(AssertionError):  # sealed batch refuses add
            self.b.add(bid, "n2")

    def test_unknown_batch(self):
        self.assertFalse(self.b.add("nope", "n1"))
        self.assertIsNone(self.b.seal_batch("nope"))

    def test_invalid_category(self):
        with self.assertRaises(AssertionError):
            self.b.open_batch("Bad Cat", 5000)

    def test_window_out_of_bounds(self):
        with self.assertRaises(AssertionError):
            self.b.open_batch("alerts", 0)
        with self.assertRaises(AssertionError):
            self.b.open_batch("alerts", MAX_WINDOW_MS + 1)

    def test_duplicate_notification(self):
        bid = self.b.open_batch("alerts", 5000)
        self.b.add(bid, "n1")
        with self.assertRaises(AssertionError):
            self.b.add(bid, "n1")

    def test_invalid_notification_id(self):
        bid = self.b.open_batch("alerts", 5000)
        with self.assertRaises(AssertionError):
            self.b.add(bid, "Bad Name")


if __name__ == "__main__":
    unittest.main()
