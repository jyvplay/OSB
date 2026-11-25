#!/usr/bin/env python3
"""Differential oracle for SettingsEnvVarSubstitutor (core/settings)."""
import re
import unittest

VAR_RE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}")


def substitute(tpl: str, env: dict) -> str:
    return VAR_RE.sub(lambda m: env.get(m.group(1), m.group(0)), tpl)


class SettingsEnvVarSubstitutorTest(unittest.TestCase):
    def test_sub(self):
        self.assertEqual(substitute("${HOME}/bin/${MISSING}", {"HOME": "/data"}), "/data/bin/${MISSING}")


if __name__ == "__main__":
    unittest.main()
