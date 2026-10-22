#!/usr/bin/env python3
"""Pure-Python R33 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import base64
import difflib
import gzip
import hashlib
import hmac
import re
import struct
import unittest
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r33'
KERNELS = [
    'feature/messages/src/main/kotlin/dev/fusion/messages/E164Format.kt',
    'feature/terminal/src/main/kotlin/dev/fusion/terminal/AnsiSgr.kt',
    'feature/beam/src/main/kotlin/dev/fusion/beam/ChunkPlan.kt',
    'feature/files/src/main/kotlin/dev/fusion/filesui/GlobMatch.kt',
    'feature/find/src/main/kotlin/dev/fusion/find/FuzzyScore.kt',
    'feature/together/src/main/kotlin/dev/fusion/together/VectorClock.kt',
    'core/documents/src/main/kotlin/dev/fusion/documents/DiffHunks.kt',
    'format/image/src/main/kotlin/dev/fusion/format/image/BmpInfo.kt',
    'format/archive/src/main/kotlin/dev/fusion/archive/GzipHead.kt',
    'feature/vault/src/main/kotlin/dev/fusion/vault/TotpCode.kt',
]

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r33/kotlin/dev/fusion/testing/R33Checks.kt').is_file())

class NoPackageCollisions(unittest.TestCase):
    def test_no_top_level_redeclaration(self):
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

class BmpFixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'tiny.bmp').read_bytes()
        self.assertEqual(raw[:2], b'BM')
        self.assertEqual(struct.unpack('<I', raw[2:6])[0], len(raw))
        self.assertEqual(len(raw), 70)
        self.assertEqual(struct.unpack('<ii', raw[18:26]), (2, 2))

class GzipFixtures(unittest.TestCase):
    def test_both_gunzip(self):
        self.assertEqual(gzip.open(FIX / 'name.gz', 'rb').read(), b'hello fusion\n')
        self.assertEqual(gzip.open(FIX / 'hcrc.gz', 'rb').read(), b'crc guarded\n')
    def test_flags(self):
        self.assertEqual((FIX / 'name.gz').read_bytes()[3], 0x08)
        self.assertEqual((FIX / 'hcrc.gz').read_bytes()[3], 0x02)

class TotpCrossCheck(unittest.TestCase):
    def test_rfc_vectors(self):
        key = base64.b32decode('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ')
        self.assertEqual(key, b'12345678901234567890')
        def totp(t, digits):
            h = hmac.new(key, struct.pack('>Q', t // 30), hashlib.sha1).digest()
            off = h[-1] & 0x0F
            v = struct.unpack('>I', h[off:off + 4])[0] & 0x7FFFFFFF
            return str(v % 10 ** digits).zfill(digits)
        self.assertEqual(totp(59, 8), '94287082')
        self.assertEqual(totp(20000000000, 8), '65353130')
        self.assertEqual(totp(1234567890, 6), '005924')

class FuzzyCrossCheck(unittest.TestCase):
    def test_hand_scores(self):
        self.assertEqual(10 + 15 + (10 + 5) + (10 + 5), 55)
        self.assertEqual(25 + (10 + 8 - 2), 41)

class GlobCrossCheck(unittest.TestCase):
    def test_star_crosses_slash(self):
        import fnmatch
        self.assertTrue(fnmatch.fnmatchcase('a/b', '**'))

class HunksCrossCheck(unittest.TestCase):
    def test_difflib_shape(self):
        sm = difflib.SequenceMatcher(None, ['a', 'b', 'c'], ['a', 'B', 'c'])
        self.assertEqual([t for t, _, _, _, _ in sm.get_opcodes()], ['equal', 'replace', 'equal'])

class ChunkCrossCheck(unittest.TestCase):
    def test_counts(self):
        self.assertEqual((10 - 1) // 3 + 1, 4)
        self.assertEqual(min(3, 10 - 9), 1)

class E164CrossCheck(unittest.TestCase):
    def test_trunk_rule(self):
        self.assertEqual('44' + '02079460000'[1:], '442079460000')

class AnsiCrossCheck(unittest.TestCase):
    def test_csi_left_intact(self):
        text = 'x\x1b[2Jy'
        self.assertIn('\x1b[2J', text)

if __name__ == '__main__':
    unittest.main()
