#!/usr/bin/env python3
"""Differential oracle for StorageQuotaEvictionPlanner (core/storage)."""
import unittest


def plan_evictions(items, needed_bytes: int):
    if needed_bytes == 0:
        return []
    evicted = []
    freed = 0
    for item_id, sz in items:
        evicted.append(item_id)
        freed += sz
        if freed >= needed_bytes:
            break
    return evicted


class StorageQuotaEvictionPlannerTest(unittest.TestCase):
    def test_plan(self):
        self.assertEqual(plan_evictions([("a", 100), ("b", 200), ("c", 300)], 250), ["a", "b"])
        self.assertEqual(plan_evictions([("a", 100)], 0), [])


if __name__ == "__main__":
    unittest.main()
