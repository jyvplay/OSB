#!/usr/bin/env python3
"""Pure-Python R34 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import hashlib
import hmac
import mimetypes
import re
import statistics
import struct
import unittest
import zlib
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r34'
KERNELS = [
    'feature/calc/src/main/kotlin/dev/fusion/calc/StatSummary.kt',
    'core/files/src/main/java/dev/fusion/files/MimeGuess.kt',
    'feature/notes/src/main/java/dev/fusion/notes/OutlineParse.kt',
    'feature/editor/src/main/java/dev/fusion/editor/BracketMatch.kt',
    'cast/dlna/src/main/java/dev/fusion/cast/dlna/SoapFault.kt',
    'core/commands/src/main/kotlin/dev/fusion/commands/PipePlan.kt',
    'format/image/src/main/kotlin/dev/fusion/format/image/JpegSeg.kt',
    'format/archive/src/main/kotlin/dev/fusion/archive/ZlibHead.kt',
    'feature/continuity/src/main/kotlin/dev/fusion/continuity/VTodoParse.kt',
    'privacy/src/main/java/dev/fusion/privacy/Pseudonym.kt',
]

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r34/kotlin/dev/fusion/testing/R34Checks.kt').is_file())

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

class JpegFixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'tiny.jpg').read_bytes()
        self.assertEqual(raw[:2], b'\xff\xd8')
        self.assertEqual(len(raw), 141)
        self.assertEqual(raw[2:4], b'\xff\xe0')
        self.assertEqual(struct.unpack('>H', raw[91:93])[0], 11)
        self.assertEqual(struct.unpack('>H', raw[96:98])[0], 1)

class ZlibFixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'zlib.bin').read_bytes()
        self.assertEqual(len(raw), 31)
        self.assertEqual(raw[:2], b'\x78\x9c')
        self.assertEqual((raw[0] * 256 + raw[1]) % 31, 0)
        self.assertEqual(zlib.decompress(raw), b'hello fusion world! ' * 8)

class StatsCrossCheck(unittest.TestCase):
    def test_hand_values(self):
        self.assertEqual(statistics.mean([1, 2, 3, 4]), 2.5)
        self.assertEqual(statistics.pvariance([1, 2, 3, 4]), 1.25)
        self.assertEqual(statistics.median([1, 2, 3, 4]), 2.5)

class MimeCrossCheck(unittest.TestCase):
    def test_stdlib_agrees(self):
        self.assertEqual(mimetypes.guess_type('a.pdf')[0], 'application/pdf')
        self.assertEqual(mimetypes.guess_type('PHOTO.JPG')[0], 'image/jpeg')

class BracketCrossCheck(unittest.TestCase):
    def test_stack_agrees(self):
        pairs = {'(': ')', '[': ']', '{': '}'}
        stack, out = [], {}
        for i, c in enumerate('[{()}]'):
            if c in pairs:
                stack.append((c, i))
            else:
                _, j = stack.pop()
                out[j] = i
        self.assertEqual(out, {0: 5, 1: 4, 2: 3})

class SoapCrossCheck(unittest.TestCase):
    def test_etree_shape(self):
        root = ET.fromstring('<Fault><faultcode>x</faultcode><faultstring>y</faultstring></Fault>')
        self.assertEqual(root.findtext('faultcode'), 'x')

class PipeCrossCheck(unittest.TestCase):
    def test_split_repair(self):
        parts = [p.strip() for p in 'cat f | grep x | wc -l'.split('|')]
        self.assertEqual(parts, ['cat f', 'grep x', 'wc -l'])

class VtodoCrossCheck(unittest.TestCase):
    def test_due_shape(self):
        self.assertTrue(re.fullmatch(r'\d{8}(T\d{6}Z?)?', '20261022T120000Z'))
        self.assertTrue(re.fullmatch(r'\d{8}(T\d{6}Z?)?', '20261022'))

class PseudonymCrossCheck(unittest.TestCase):
    def test_hmac_shape(self):
        key = bytes((i * 7 + 1) % 256 for i in range(32))
        tok = hmac.new(key, b'crash\x00user-1', hashlib.sha256).hexdigest()[:32]
        self.assertEqual(tok, 'c6e8480c4fc241780116719dd6cb3fa0')

if __name__ == '__main__':
    unittest.main()
