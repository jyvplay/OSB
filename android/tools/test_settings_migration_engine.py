#!/usr/bin/env python3
"""Differential oracle for SettingsMigrationEngine (core/settings)."""
import unittest


def migrate(initial: dict, cur_v: int, tgt_v: int, steps: dict) -> dict:
    st = dict(initial)
    v = cur_v
    while v < tgt_v:
        nxt_v, fn = steps[v]
        fn(st)
        v = nxt_v
    return st


class SettingsMigrationEngineTest(unittest.TestCase):
    def test_migrate(self):
        steps = {
            1: (2, lambda d: d.update({"theme": d.pop("dark", "light")})),
            2: (3, lambda d: d.setdefault("telemetry", "off")),
        }
        res = migrate({"dark": "true"}, 1, 3, steps)
        self.assertEqual(res, {"theme": "true", "telemetry": "off"})


if __name__ == "__main__":
    unittest.main()
