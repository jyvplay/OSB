#!/usr/bin/env python3
"""Differential oracle for BrowserHistoryDeduplicator (feature/browser)."""
import unittest


def deduplicate_latest(visits):
    latest = {}
    for url, ts in visits:
        if url not in latest or ts >= latest[url]:
            latest[url] = ts
    return sorted(latest.items(), key=lambda kv: -kv[1])


class BrowserHistoryDeduplicatorTest(unittest.TestCase):
    def test_dedup(self):
        self.assertEqual(deduplicate_latest([("a", 10), ("b", 20), ("a", 30)]), [("a", 30), ("b", 20)])


if __name__ == "__main__":
    unittest.main()
