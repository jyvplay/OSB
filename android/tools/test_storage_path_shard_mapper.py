#!/usr/bin/env python3
"""Differential oracle for StoragePathShardMapper (core/storage)."""
import unittest


def shard_path(h: str) -> str:
    n = h.lower()
    return f"{n[:2]}/{n[2:4]}/{n}"


class StoragePathShardMapperTest(unittest.TestCase):
    def test_shard(self):
        self.assertEqual(shard_path("ABCDEF12"), "ab/cd/abcdef12")


if __name__ == "__main__":
    unittest.main()
