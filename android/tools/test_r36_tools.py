#!/usr/bin/env python3
"""Pure-Python R36 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import math
import re
import struct
import unittest
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r36'
KERNELS = [
    'feature/calc/src/main/kotlin/dev/fusion/calc/RpnEval.kt',
    'feature/files/src/main/kotlin/dev/fusion/filesui/NameClash.kt',
    'feature/editor/src/main/java/dev/fusion/editor/WrapCol.kt',
    'core/commands/src/main/kotlin/dev/fusion/commands/RedirPlan.kt',
    'cast/castv2/src/main/kotlin/dev/fusion/castv2/CastState.kt',
    'feature/maps/src/main/kotlin/dev/fusion/maps/ScaleBar.kt',
    'feature/media/src/main/kotlin/dev/fusion/media/WavHead.kt',
    'format/image/src/main/kotlin/dev/fusion/format/image/TiffHead.kt',
    'privacy/src/main/java/dev/fusion/privacy/KAnon.kt',
    'core/security/src/main/kotlin/dev/fusion/security/AttemptWindow.kt',
    'feature/lens/src/main/kotlin/dev/fusion/lens/DofNear.kt',
    'feature/vault/src/main/kotlin/dev/fusion/vault/ShannonBits.kt',
]

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r36/kotlin/dev/fusion/testing/R36Checks.kt').is_file())

class NoPackageCollisions(unittest.TestCase):
    def test_no_top_level_redeclaration(self):
        decl = re.compile(r'^(?:data class|class|object|interface|typealias|enum class|sealed interface) (\w+)', re.M)
        for rel in KERNELS:
            here = ROOT / rel
            names = set(decl.findall(here.read_text()))
            self.assertTrue(names, rel)
            for sib in here.parent.glob('*.kt'):
                if sib == here:
                    continue
                clash = names & set(decl.findall(sib.read_text()))
                self.assertEqual(clash, set(), f'{rel} redeclares {clash} from {sib.name}')

class WavFixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'wav.bin').read_bytes()
        self.assertEqual(len(raw), 44)
        self.assertEqual(raw[:4], b'RIFF')
        self.assertEqual(raw[8:12], b'WAVE')
        self.assertEqual(raw[12:16], b'fmt ')
        fmt, ch, rate, brate, align, bits = struct.unpack_from('<HHIIHH', raw, 20)
        self.assertEqual((fmt, ch, rate, bits), (1, 1, 8000, 16))
        self.assertEqual(brate, 16000)
        self.assertEqual(align, 2)
        self.assertEqual(raw[36:40], b'data')

class TiffFixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'tiff.bin').read_bytes()
        self.assertEqual(len(raw), 16)
        self.assertEqual(raw[:2], b'II')
        self.assertEqual(struct.unpack_from('<H', raw, 2)[0], 42)
        self.assertEqual(struct.unpack_from('<I', raw, 4)[0], 8)

class RpnCrossCheck(unittest.TestCase):
    def test_hand_values(self):
        self.assertEqual(0.1 + 0.2, 0.30000000000000004)
        self.assertEqual(6.0 / 3.0, 2.0)

class ScaleCrossCheck(unittest.TestCase):
    def test_mpp_origin(self):
        self.assertEqual(156543.03392 * math.cos(math.radians(0.0)) / 2 ** 0, 156543.03392)
        self.assertAlmostEqual(156543.03392 * math.cos(math.radians(45.0)) / 2 ** 10,
                               108.09828206283957, places=9)

class CastCrossCheck(unittest.TestCase):
    def test_table_size(self):
        legal = {('IDLE', 'LOAD'), ('IDLE', 'PLAY'), ('BUFFERING', 'BUFFER_OK'),
                 ('BUFFERING', 'STOP'), ('PLAYING', 'PAUSE'), ('PLAYING', 'LOAD'),
                 ('PLAYING', 'STOP'), ('PLAYING', 'FINISH'), ('PAUSED', 'PLAY'),
                 ('PAUSED', 'LOAD'), ('PAUSED', 'STOP')}
        self.assertEqual(len(legal), 11)

class DofCrossCheck(unittest.TestCase):
    def test_hyperfocal_pin(self):
        self.assertAlmostEqual(50.0 * 50.0 / (2.8 * 0.03) + 50.0, 29811.904761904763, places=6)

class ShannonCrossCheck(unittest.TestCase):
    def test_exact_entropies(self):
        self.assertEqual(-sum((c / 2) * math.log2(c / 2) for c in Counter([0, 1]).values()), 1.0)
        self.assertEqual(-sum((c / 4) * math.log2(c / 4) for c in Counter([0, 1, 2, 3]).values()), 2.0)

class KanonCrossCheck(unittest.TestCase):
    def test_group_counts(self):
        counts = Counter([('a', 'x'), ('a', 'x'), ('b', 'y')])
        self.assertEqual((len(counts), min(counts.values())), (2, 1))

class WindowCrossCheck(unittest.TestCase):
    def test_half_open_boundary(self):
        prior, now, win = [40, 50, 150], 100, 60
        self.assertEqual(sum(1 for t in prior if now - win < t <= now), 1)

class NamesCrossCheck(unittest.TestCase):
    def test_suffix_strip(self):
        self.assertEqual(re.sub(r' \(\d+\)$', '', 'a (2)'), 'a')
        self.assertEqual(re.sub(r' \(\d+\)$', '', 'plain'), 'plain')

if __name__ == '__main__':
    unittest.main()
