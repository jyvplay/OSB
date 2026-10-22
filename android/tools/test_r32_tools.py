#!/usr/bin/env python3
"""Pure-Python R32 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import re
import unittest
from fractions import Fraction as PyFraction
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r32'
KERNELS = [
    'feature/calc/src/main/kotlin/dev/fusion/calc/FractionApprox.kt',
    'format/lok/src/main/java/dev/fusion/lok/OfficeCapability.kt',
    'cast/castv2/src/main/kotlin/dev/fusion/castv2/CastMediaMeta.kt',
    'core/commands/src/main/kotlin/dev/fusion/commands/ArgSplit.kt',
    'core/security/src/main/kotlin/dev/fusion/security/PinRules.kt',
    'privacy/src/main/java/dev/fusion/privacy/RetentionSweep.kt',
    'feature/continuity/src/main/kotlin/dev/fusion/continuity/ICalDuration.kt',
    'feature/maps/src/main/kotlin/dev/fusion/maps/GeoUri.kt',
    'feature/vault/src/main/kotlin/dev/fusion/vault/LockoutPolicy.kt',
    'feature/media/src/main/kotlin/dev/fusion/media/CueSheet.kt',
]

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r32/kotlin/dev/fusion/testing/R32Checks.kt').is_file())

class NoPackageCollisions(unittest.TestCase):
    def test_no_top_level_redeclaration(self):
        # Regression guard for the CI-caught GeoPoint clash: a kernel's
        # top-level type names must be unique within its package directory.
        decl = re.compile(r'^(?:data class|class|object|interface|typealias|enum class) (\w+)', re.M)
        for rel in KERNELS:
            here = ROOT / rel
            names = set(decl.findall(here.read_text()))
            self.assertTrue(names, rel)
            for sib in here.parent.glob('*.kt'):
                if sib == here:
                    continue
                clash = names & set(decl.findall(sib.read_text()))
                self.assertEqual(clash, set(), f'{rel} redeclares {clash} from {sib.name}')

class CueFixture(unittest.TestCase):
    def test_shape(self):
        text = (FIX / 'sheet.cue').read_text()
        self.assertRegex(text, r'FILE "sample\.bin" BINARY')
        self.assertEqual(len(re.findall(r'^\s*TRACK \d{2} AUDIO', text, flags=re.M)), 3)
        self.assertEqual(len(re.findall(r'INDEX 01 ', text)), 3)
        self.assertIn('INDEX 00 03:24:00', text)
        # Global tags precede TRACK 1 (parser must tolerate, not refuse).
        self.assertLess(text.index('TITLE "Sample Album"'), text.index('TRACK 01'))

class FractionCrossCheck(unittest.TestCase):
    def test_limit_denominator_agreement(self):
        for s, d, want in [('3.14159', 10, (22, 7)), ('3.14159', 100, (311, 99)),
                           ('0.333333', 100, (1, 3)), ('-2.5', 10, (-5, 2))]:
            f = PyFraction(s).limit_denominator(d)
            self.assertEqual((f.numerator, f.denominator), want, s)

class DurationCrossCheck(unittest.TestCase):
    def test_values(self):
        self.assertEqual(1 * 86400 + 2 * 3600 + 3 * 60 + 4, 93784)
        self.assertEqual(7 * 86400, 604800)
        self.assertEqual(4000 * 86400 > 315532800, True)

class GeoCrossCheck(unittest.TestCase):
    def test_round_trip(self):
        for lat, lon in [(48.2, 16.3), (-33.86, 151.2)]:
            self.assertEqual((float(str(lat)), float(str(lon))), (lat, lon))

class SweepCrossCheck(unittest.TestCase):
    def test_boundary_inclusive(self):
        now, max_age, at = 10000, 1000, 9000
        self.assertFalse(now - at > max_age)
        self.assertTrue(now - (at - 1) > max_age)

class LockoutCrossCheck(unittest.TestCase):
    def test_doubling_saturates_32min(self):
        delay = 30000
        for _ in range(10 - 4):
            delay = min(delay * 2, 3600000)
        self.assertEqual(delay, 1920000)

class PinCrossCheck(unittest.TestCase):
    def test_wrap_runs(self):
        def is_run(pin):
            ds = [ord(c) - 48 for c in pin]
            up = all((b - a) % 10 == 1 for a, b in zip(ds, ds[1:]))
            down = all((a - b) % 10 == 1 for a, b in zip(ds, ds[1:]))
            return up or down
        self.assertTrue(is_run('7890'))
        self.assertTrue(is_run('2109'))
        self.assertFalse(is_run('1357'))

class OfficeCrossCheck(unittest.TestCase):
    def test_viewable_sorted(self):
        view = {'pdf': True, 'txt': True, 'csv': True, 'docx': False}
        self.assertEqual(sorted(f for f, v in view.items() if v), ['csv', 'pdf', 'txt'])

class CueFramesCrossCheck(unittest.TestCase):
    def test_frame_math(self):
        self.assertEqual((3 * 60 + 26) * 75, 15450)
        self.assertEqual((7 * 60 + 10) * 75, 32250)

if __name__ == '__main__':
    unittest.main()
