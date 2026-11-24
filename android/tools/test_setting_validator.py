#!/usr/bin/env python3
"""Independent differential oracle for SettingValidator (feature/settings).

Validates settings values against rules: required, allowed values (enum),
min/max numeric bounds, pattern matches. All rules evaluated; returns full
violation report. Cross-checks against the Kotlin contract in
feature/settings/src/main/kotlin/dev/fusion/settings/SettingValidator.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_RULES = 256
MAX_ALLOWED_VALUES = 64
MAX_KEY_LEN = 256
MAX_PATTERN_LEN = 128
MAX_VALUE_LEN = 1024
KEY_RE = re.compile(r"[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*")


class SettingValidator:
    def __init__(self):
        self._rules = {}

    def add_rule(self, key, required=False, allowed_values=None, min_val=None,
                 max_val=None, pattern=None):
        assert KEY_RE.fullmatch(key), f"invalid settings key: {key}"
        assert len(key) <= MAX_KEY_LEN, "key too long"
        assert len(self._rules) < MAX_RULES or key in self._rules, "too many rules"
        if allowed_values is not None:
            assert len(allowed_values) <= MAX_ALLOWED_VALUES, "too many allowed values"
        if pattern is not None:
            assert len(pattern) <= MAX_PATTERN_LEN, "pattern too long"
        self._rules[key] = {
            "required": required,
            "allowed_values": allowed_values,
            "min": min_val,
            "max": max_val,
            "pattern": pattern,
        }

    def validate(self, values):
        violations = []
        for key, rule in self._rules.items():
            value = values.get(key)
            if value is None:
                if rule["required"]:
                    violations.append((key, "required", "required setting missing"))
                continue
            assert len(value) <= MAX_VALUE_LEN, f"value for {key} too long"
            if rule["allowed_values"] is not None and value not in rule["allowed_values"]:
                violations.append((key, "allowedValues", "value not in allowed set"))
            if rule["min"] is not None or rule["max"] is not None:
                try:
                    num = float(value)
                except ValueError:
                    violations.append((key, "numeric", "value is not numeric"))
                else:
                    if rule["min"] is not None and num < rule["min"]:
                        violations.append((key, "min", "value below minimum"))
                    if rule["max"] is not None and num > rule["max"]:
                        violations.append((key, "max", "value above maximum"))
            if rule["pattern"] is not None:
                if not re.fullmatch(rule["pattern"], value):
                    violations.append((key, "pattern", "value does not match pattern"))
        return (len(violations) == 0, violations)

    def rule_count(self):
        return len(self._rules)

    def remove_rule(self, key):
        return self._rules.pop(key, None) is not None


class SettingValidatorTest(unittest.TestCase):
    def setUp(self):
        self.v = SettingValidator()

    def test_required_missing(self):
        self.v.add_theme = None  # noop placeholder
        self.v.add_rule("appearance.theme", required=True)
        valid, violations = self.v.validate({})
        self.assertFalse(valid)
        self.assertEqual(len(violations), 1)

    def test_required_present(self):
        self.v.add_rule("appearance.theme", required=True)
        valid, _ = self.v.validate({"appearance.theme": "dark"})
        self.assertTrue(valid)

    def test_allowed_values(self):
        self.v.add_rule("appearance.theme", allowed_values={"dark", "light"})
        valid, violations = self.v.validate({"appearance.theme": "neon"})
        self.assertFalse(valid)
        self.assertEqual(violations[0][1], "allowedValues")

    def test_min_max(self):
        self.v.add_rule("window.width", min_val=100.0, max_val=2000.0)
        valid, violations = self.v.validate({"window.width": "50"})
        self.assertFalse(valid)
        self.assertEqual(violations[0][1], "min")
        valid, violations = self.v.validate({"window.width": "5000"})
        self.assertFalse(valid)
        self.assertEqual(violations[0][1], "max")
        valid, _ = self.v.validate({"window.width": "800"})
        self.assertTrue(valid)

    def test_pattern(self):
        self.v.add_rule("username", pattern=r"[a-z]+")
        valid, violations = self.v.validate({"username": "User1"})
        self.assertFalse(valid)
        self.assertEqual(violations[0][1], "pattern")
        valid, _ = self.v.validate({"username": "alice"})
        self.assertTrue(valid)

    def test_all_rules_evaluated(self):
        self.v.add_rule("a", required=True)
        self.v.add_rule("b", required=True)
        valid, violations = self.v.validate({})
        self.assertFalse(valid)
        self.assertEqual(len(violations), 2)

    def test_invalid_key(self):
        with self.assertRaises(AssertionError):
            self.v.add_rule("Bad.Key")
        with self.assertRaises(AssertionError):
            self.v.add_rule("123")

    def test_remove_rule(self):
        self.v.add_rule("appearance.theme")
        self.assertTrue(self.v.remove_rule("appearance.theme"))
        self.assertFalse(self.v.remove_rule("appearance.theme"))


if __name__ == "__main__":
    unittest.main()
