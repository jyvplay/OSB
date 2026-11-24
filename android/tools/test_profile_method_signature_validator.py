#!/usr/bin/env python3
"""Differential oracle for ProfileMethodSignatureValidator (baselineprofile)."""
import re
import unittest

RULE_RE = re.compile(r"^[HSP]{1,3}L[a-zA-Z0-9_/$]+;->[a-zA-Z0-9_<>$]+\([^)]*\)[VZBCSIJFDL\[;a-zA-Z0-9_/$]+$")


def is_valid(rule: str) -> bool:
    if not rule or len(rule) > 512:
        return False
    return bool(RULE_RE.match(rule))


class ProfileMethodSignatureValidatorTest(unittest.TestCase):
    def test_valid_rules(self):
        self.assertTrue(is_valid("HSPLdev/fusion/shell/MainActivity;->onCreate(Landroid/os/Bundle;)V"))
        self.assertTrue(is_valid("PLdev/fusion/calc/Calc;->add(II)I"))

    def test_invalid_rules(self):
        self.assertFalse(is_valid(""))
        self.assertFalse(is_valid("XLdev/fusion/Main;->run()V"))
        self.assertFalse(is_valid("Hdev/fusion/Main;->run()V"))


if __name__ == "__main__":
    unittest.main()
