#!/usr/bin/env python3
"""Differential oracle for BackupMerkleDeltaPlanner (feature/backup)."""
import unittest


def plan_upload(local, remote, max_bytes):
    rem = {i: h.lower() for i, h, _ in remote}
    out, tot = [], 0
    for i, h, sz in sorted(local):
        if rem.get(i) != h.lower():
            tot += sz
            assert tot <= max_bytes
            out.append(i)
    return out


class BackupMerkleDeltaPlannerTest(unittest.TestCase):
    def test_delta(self):
        loc = [(0, "aa", 100), (1, "bb", 200), (2, "cc", 300)]
        rem = [(0, "AA", 100), (1, "xx", 200)]
        self.assertEqual(plan_upload(loc, rem, 1000), [1, 2])


if __name__ == "__main__":
    unittest.main()
