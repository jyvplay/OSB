#!/usr/bin/env python3
"""Pure-Python R31 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r31'
KERNELS = [
    'privacy/src/main/java/dev/fusion/privacy/LogScrubber.kt',
    'core/capabilities/src/main/java/dev/fusion/capabilities/CapabilityGate.kt',
    'core/index/src/main/kotlin/dev/fusion/index/SearchQuery.kt',
    'feature/editor/src/main/java/dev/fusion/editor/TextOffsets.kt',
    'feature/vault/src/main/kotlin/dev/fusion/vault/VaultManifest.kt',
    'format/pdf/src/main/java/dev/fusion/format/pdf/PdfTrailer.kt',
    'format/psd/src/main/kotlin/dev/fusion/psd/PsdResources.kt',
    'core/hw/src/main/java/dev/fusion/hw/CodecPreference.kt',
    'core/perf/src/main/java/dev/fusion/perf/PhaseLedger.kt',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/RobotsRules.kt',
]

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r31/kotlin/dev/fusion/testing/R31Checks.kt').is_file())

class PdfFixture(unittest.TestCase):
    def test_shape(self):
        raw = (FIX / 'min.pdf').read_bytes()
        text = raw.decode('ascii')
        self.assertTrue(text.startswith('%PDF-1.4'))
        self.assertTrue(text.rstrip().endswith('%%EOF'))
        sx = int(text[text.rindex('startxref') + 9:].strip().split()[0])
        self.assertTrue(text.startswith('xref', sx))
        entries = re.findall(r'\d{10} \d{5} [fn] \n', text[sx:])
        self.assertEqual(len(entries), 4)

class PsdFixture(unittest.TestCase):
    def test_shape(self):
        sec = (FIX / 'psd_res.bin').read_bytes()
        self.assertEqual(sec[:4], b'8BIM')
        self.assertEqual(len(sec), 36)

class RobotsFixture(unittest.TestCase):
    def test_shape(self):
        text = (FIX / 'robots.txt').read_text()
        self.assertIn('User-agent: *', text)
        self.assertIn('User-agent: fusion-test', text)

class ScrubFixture(unittest.TestCase):
    def test_shape(self):
        text = (FIX / 'scrub_sample.txt').read_text()
        self.assertIn('@', text)
        self.assertIn('Bearer ', text)
        self.assertIn('/data/', text)
        self.assertNotIn('bearer header', text.lower(), 'must not self-trip the bearer grammar')

class VaultCanonicalCrossCheck(unittest.TestCase):
    def test_digest_rule(self):
        import hashlib
        canon = 'a1|note.txt|12|' + 'a' * 64 + '\n' + 'b2|photo.bin|100|' + 'b' * 64
        digest = hashlib.sha256(canon.encode()).hexdigest()
        self.assertRegex(digest, r'^[0-9a-f]{64}$')
        swapped = 'b2|photo.bin|100|' + 'b' * 64 + '\n' + 'a1|note.txt|12|' + 'a' * 64
        self.assertNotEqual(hashlib.sha256(swapped.encode()).hexdigest(), digest,
                            'sorting is load-bearing: unsorted input must differ')

class RobotsCrossCheck(unittest.TestCase):
    def test_longest_wins(self):
        allow, disallow, path = '/private/public', '/private/', '/private/public'
        hits = [(allow, True), (disallow, False)]
        hits = [h for h in hits if path.startswith(h[0])]
        best = max(len(h[0]) for h in hits)
        self.assertTrue(any(v for p, v in hits if len(p) == best))

class QueryTokenizerCrossCheck(unittest.TestCase):
    def test_unicode_split(self):
        self.assertEqual([t for t in re.split(r'[^\w]+', 'hot CAFÉ_x', flags=re.UNICODE) if t],
                         ['hot', 'CAFÉ_x'])

if __name__ == '__main__':
    unittest.main()
