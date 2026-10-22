#!/usr/bin/env python3
"""Pure-Python R35 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import difflib
import math
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r35'
KERNELS = [
    'feature/lens/src/main/kotlin/dev/fusion/lens/EvShift.kt',
    'feature/vault/src/main/kotlin/dev/fusion/vault/XorSplit.kt',
    'core/documents/src/main/kotlin/dev/fusion/documents/WordDiff.kt',
    'feature/files/src/main/kotlin/dev/fusion/filesui/DupGroup.kt',
    'core/index/src/main/kotlin/dev/fusion/index/SnippetCut.kt',
    'feature/media/src/main/kotlin/dev/fusion/media/Id3Head.kt',
    'feature/maps/src/main/kotlin/dev/fusion/maps/TileCover.kt',
    'feature/continuity/src/main/kotlin/dev/fusion/continuity/VCardParse.kt',
    'privacy/src/main/java/dev/fusion/privacy/FieldMask.kt',
    'feature/editor/src/main/java/dev/fusion/editor/IndentDetect.kt',
]

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r35/kotlin/dev/fusion/testing/R35Checks.kt').is_file())

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

class Id3Fixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'id3.bin').read_bytes()
        self.assertEqual(len(raw), 110)
        self.assertEqual(raw[:3], b'ID3')
        self.assertEqual((raw[3], raw[4], raw[5]), (4, 0, 0))
        size = 0
        for x in raw[6:10]:
            self.assertEqual(x & 0x80, 0)
            size = size * 128 + x
        self.assertEqual(size, 100)

class EvCrossCheck(unittest.TestCase):
    def test_hand_values(self):
        self.assertEqual(math.log2(4.0 / 0.5), 3.0)
        self.assertEqual(2.0 * 2.0 / 2 ** 3.0, 0.5)

class XorCrossCheck(unittest.TestCase):
    def test_hand_bytes(self):
        self.assertEqual(bytes(a ^ b for a, b in zip(bytes([1, 2, 3]), bytes([9, 8, 7]))), bytes([8, 10, 4]))

class WordsCrossCheck(unittest.TestCase):
    def test_difflib_shape(self):
        sm = difflib.SequenceMatcher(None, ['a', 'b', 'c'], ['a', 'B', 'c'])
        self.assertEqual([t for t, _, _, _, _ in sm.get_opcodes()], ['equal', 'replace', 'equal'])

class TilesCrossCheck(unittest.TestCase):
    def test_slippy_origin(self):
        n = 2 ** 1
        self.assertEqual(math.floor((0.0 + 180.0) / 360.0 * n), 1)
        self.assertEqual((2 ** 10) * (2 ** 10) > 4096, True)

class IndentCrossCheck(unittest.TestCase):
    def test_gcd_width(self):
        self.assertEqual(math.gcd(2, 4, 6), 2)
        self.assertEqual(math.gcd(40) not in range(1, 9), True)

class SnipCrossCheck(unittest.TestCase):
    def test_ellipsis_marks(self):
        self.assertEqual('…' + 'beta' + '…', '\u2026beta\u2026')

class VcardCrossCheck(unittest.TestCase):
    def test_name_split(self):
        self.assertEqual('Doe;Jane;;;'.split(';')[:2], ['Doe', 'Jane'])

if __name__ == '__main__':
    unittest.main()
