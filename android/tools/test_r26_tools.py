#!/usr/bin/env python3
"""Pure-Python R26 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import math
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r26'
KERNELS = [
    'core/designsystem/src/main/kotlin/dev/fusion/designsystem/BeautyStandard.kt',
    'core/security/src/main/kotlin/dev/fusion/security/AmnesiaPolicy.kt',
    'core/security/src/main/kotlin/dev/fusion/security/TorPosture.kt',
    'core/security/src/main/kotlin/dev/fusion/security/UsbPolicy.kt',
    'core/security/src/main/kotlin/dev/fusion/security/KeypadLayout.kt',
    'feature/media/src/main/kotlin/dev/fusion/media/PlaylistParser.kt',
    'feature/maps/src/main/kotlin/dev/fusion/maps/GpxTrack.kt',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/SafeNavigation.kt',
    'format/native/src/main/kotlin/dev/fusion/native/SandboxJob.kt',
    'format/native/src/main/kotlin/dev/fusion/native/TsvTable.kt',
]

def luminance(rgb):
    def channel(shift):
        s = ((rgb >> shift) & 255) / 255.0
        return s / 12.92 if s <= 0.04045 else ((s + 0.055) / 1.055) ** 2.4
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)

def ratio(a, b):
    x, y = luminance(a), luminance(b)
    return (max(x, y) + 0.05) / (min(x, y) + 0.05)

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', rel and text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r26/kotlin/dev/fusion/testing/R26Checks.kt').is_file())

class PaletteMath(unittest.TestCase):
    def test_all_pairs_meet_aa(self):
        src = (ROOT / 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/ProfessionalPalettes.kt').read_text()
        found = re.findall(r'ProfessionalColors\((0x[^)]+)\)', src)
        self.assertEqual(3, len(found))
        order = ['background', 'surface', 'raised', 'outline', 'primary', 'onPrimary', 'text', 'muted', 'selection', 'onSelection']
        pairs = [('text', 'background'), ('text', 'surface'), ('text', 'raised'), ('muted', 'background'),
                 ('muted', 'surface'), ('muted', 'raised'), ('onPrimary', 'primary'), ('onSelection', 'selection'), ('primary', 'background')]
        for body in found:
            role = dict(zip(order, [int(v.strip(), 16) for v in body.split(',')]))
            for fg, bg in pairs:
                self.assertGreaterEqual(ratio(role[fg], role[bg]), 4.5, f'{fg}/{bg}')

class PlaylistFixtures(unittest.TestCase):
    def test_m3u8_fixture(self):
        lines = (FIX / 'sample.m3u8').read_text().splitlines()
        self.assertEqual('#EXTM3U', lines[0])
        uris = [l for l in lines if l and not l.startswith('#')]
        self.assertEqual(3, len(uris))
        self.assertTrue(all(u.startswith('https://') for u in uris))
        self.assertNotIn('EXT-X-KEY', '\n'.join(lines))
    def test_pls_fixture(self):
        text = (FIX / 'sample.pls').read_text()
        self.assertIn('[playlist]', text)
        self.assertEqual(2, len(re.findall(r'^File\d+=https://', text, re.M)))

class GpxFixture(unittest.TestCase):
    def test_track_points_and_length(self):
        root = ET.fromstring((FIX / 'sample.gpx').read_text())
        pts = [(float(t.attrib['lat']), float(t.attrib['lon'])) for t in root.iter('trkpt')]
        self.assertEqual(3, len(pts))
        R = 6371008.8
        total = 0.0
        for (la1, lo1), (la2, lo2) in zip(pts, pts[1:]):
            p1, p2 = math.radians(la1), math.radians(la2)
            h = math.sin(math.radians(la2 - la1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lo2 - lo1) / 2) ** 2
            total += 2 * R * math.asin(math.sqrt(min(1.0, max(0.0, h))))
        self.assertGreater(total, 1.0)
        self.assertLess(total, 1000.0)

class UsbFixture(unittest.TestCase):
    RULE = re.compile(r'^(allow|block|reject) id (\*:\*|[0-9a-f]{4}:(?:\*|[0-9a-f]{4}))'
                      r'( with-interface \{ (?:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2} ?)+\})?'
                      r'( if !allowed-matches\(with-interface one-of \{ (?:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2} ?)+\}\))?$')
    def test_rules_match_grammar(self):
        lines = [l.split('#')[0].strip() for l in (FIX / 'usb-rules.conf').read_text().splitlines()]
        rules = [l for l in lines if l]
        self.assertEqual(3, len(rules))
        for r in rules:
            self.assertRegex(r, self.RULE, r)
    def test_suspicious_combo_rejected_first(self):
        lines = [l.split('#')[0].strip() for l in (FIX / 'usb-rules.conf').read_text().splitlines()]
        rules = [l for l in lines if l]
        self.assertTrue(rules[0].startswith('reject'))
        self.assertIn('08:06:50', rules[0])
        self.assertIn('03:01:01', rules[0])

class TsvSmoke(unittest.TestCase):
    def test_tab_shape(self):
        rows = [l.split('\t') for l in 'name\tage\nada\t36\n'.splitlines()]
        self.assertTrue(all(len(r) == 2 for r in rows))

if __name__ == '__main__':
    unittest.main()
