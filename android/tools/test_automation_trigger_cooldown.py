#!/usr/bin/env python3
"""Differential oracle for AutomationTriggerCooldown (feature/automation)."""
import unittest


class AutomationTriggerCooldown:
    def __init__(self, cooldown_ms: int):
        self.cooldown_ms = cooldown_ms
        self.last = {}

    def try_fire(self, rule_id: str, now_ms: int) -> bool:
        prev = self.last.get(rule_id)
        if prev is not None and now_ms - prev < self.cooldown_ms:
            return False
        self.last[rule_id] = now_ms
        return True


class AutomationTriggerCooldownTest(unittest.TestCase):
    def test_cooldown(self):
        c = AutomationTriggerCooldown(1000)
        self.assertTrue(c.try_fire("r1", 100))
        self.assertFalse(c.try_fire("r1", 500))
        self.assertTrue(c.try_fire("r1", 1100))


if __name__ == "__main__":
    unittest.main()
