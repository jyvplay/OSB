#!/usr/bin/env python3
"""Pure-Python R29 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import csv
import io
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r29'
KERNELS = [
    'feature/beam/src/main/kotlin/dev/fusion/beam/ShareManifest.kt',
    'core/storage/src/main/kotlin/dev/fusion/storage/QuotaLedger.kt',
    'feature/alerts/src/main/kotlin/dev/fusion/alerts/AlertThrottle.kt',
    'feature/files/src/main/kotlin/dev/fusion/filesui/FileSort.kt',
    'core/commands/src/main/kotlin/dev/fusion/commands/AliasTable.kt',
    'feature/accessibility/src/main/kotlin/dev/fusion/a11y/ContrastRatio.kt',
    'core/settings/src/main/kotlin/dev/fusion/settings/LocaleMatch.kt',
    'feature/backup/src/main/kotlin/dev/fusion/backup/RetentionPlan.kt',
    'cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/DidlLite.kt',
    'format/ooxml/src/main/kotlin/dev/fusion/ooxml/CsvDialect.kt',
]

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r29/kotlin/dev/fusion/testing/R29Checks.kt').is_file())

class SharesFixture(unittest.TestCase):
    def test_fields(self):
        rows = [l for l in (FIX / 'shares.txt').read_text().splitlines() if l.strip() and not l.startswith('#')]
        self.assertEqual(len(rows), 5)
        for line in rows:
            name, size, mime, sha = line.split('|')
            self.assertNotRegex(name, r'[/\\]')
            self.assertGreaterEqual(int(size), 0)
            self.assertRegex(mime, r'^[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*$')
            self.assertRegex(sha, r'^[0-9a-f]{64}$')

class DidlFixture(unittest.TestCase):
    def test_wellformed(self):
        for name in ('didl_audio.xml', 'didl_video.xml'):
            root = ET.fromstring((FIX / name).read_text())
            locals_ = [e.tag.rsplit('}', 1)[-1] for e in root.iter()]
            self.assertIn('item', locals_)
            self.assertEqual(root.tag.rsplit('}', 1)[-1], 'DIDL-Lite')

class CsvFixture(unittest.TestCase):
    def test_shapes(self):
        cases = {'semi.csv': ';', 'tab.csv': '\t', 'pipe.csv': '|'}
        for name, delim in cases.items():
            rows = list(csv.reader(io.StringIO((FIX / name).read_text()), delimiter=delim))
            self.assertTrue(all(len(r) == 3 for r in rows), name)
        self.assertEqual(len(list(csv.reader(io.StringIO((FIX / 'semi.csv').read_text()), delimiter=';'))), 4)

class ContrastCrossCheck(unittest.TestCase):
    def test_vectors(self):
        def lum(rgb):
            def ch(c):
                v = c / 255.0
                return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
            return 0.2126 * ch(rgb[0]) + 0.7152 * ch(rgb[1]) + 0.0722 * ch(rgb[2])
        self.assertAlmostEqual(lum((255, 255, 255)), 1.0, places=9)
        self.assertAlmostEqual(lum((0, 0, 0)), 0.0, places=9)
        self.assertGreater(lum((0x76,) * 3), 0.17)
        self.assertLess(lum((0x76,) * 3), 0.20)

class LocaleCrossCheck(unittest.TestCase):
    def test_grammar(self):
        good = ['en', 'en-US', 'pt-BR', 'zh']
        for tag in good:
            parts = tag.split('-')
            self.assertRegex(parts[0].lower(), r'^[a-z]{2,3}$')
        for bad in ('en--US', 'e1-US', 'en_US', 'en-US-x'):
            self.assertIsNone(re.fullmatch(r'[A-Za-z]{2,3}(-[A-Za-z]{2})?', bad), bad)

class RetentionCrossCheck(unittest.TestCase):
    def test_windows(self):
        snaps = [('a', 10, 1, 0), ('b', 10, 1, 0), ('c', 9, 1, 0), ('d', 3, 0, 0), ('e', 40, 5, 1)]
        newest_days = sorted({s[1] for s in snaps}, reverse=True)[:2]
        self.assertEqual(newest_days, [40, 10])
        self.assertEqual(sorted({s[2] for s in snaps}, reverse=True)[:2], [5, 1])

class ThrottleCrossCheck(unittest.TestCase):
    def test_refill_math(self):
        tokens = min(3.0, 0.0 + (1000 - 0) / 1000.0 * 1.0)
        self.assertAlmostEqual(tokens, 1.0)
        tokens = min(3.0, 0.0 + (60000 - 1000) / 1000.0 * 1.0)
        self.assertAlmostEqual(tokens, 3.0)

class AliasCrossCheck(unittest.TestCase):
    def test_depth_bound(self):
        table = {f'a{i}': [f'a{i+1}'] for i in range(9)}
        head, hops = 'a0', 0
        while head in table and hops <= 8:
            head = table[head][0]
            hops += 1
        self.assertEqual((head, hops), ('a9', 9))

if __name__ == '__main__':
    unittest.main()
