#!/usr/bin/env python3
"""Pure-Python R27 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import datetime
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r27'
KERNELS = [
    'feature/launcher/src/main/kotlin/dev/fusion/launcher/TabletopArrange.kt',
    'feature/media/src/main/kotlin/dev/fusion/media/PodcastFeed.kt',
    'feature/media/src/main/kotlin/dev/fusion/media/StreamPolicy.kt',
    'feature/maps/src/main/kotlin/dev/fusion/maps/Gazetteer.kt',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/ContentBlocker.kt',
    'core/documents/src/main/kotlin/dev/fusion/documents/MarkdownDoc.kt',
    'core/security/src/main/kotlin/dev/fusion/security/FirewallPolicy.kt',
    'core/security/src/main/kotlin/dev/fusion/security/UsbInventory.kt',
    'core/designsystem/src/main/kotlin/dev/fusion/designsystem/TypeScale.kt',
    'feature/automation/src/main/kotlin/dev/fusion/automation/CronSpec.kt',
]
M3_OFFICIAL = {
    'displayLarge': (57.0, 64.0), 'displayMedium': (45.0, 52.0), 'displaySmall': (36.0, 44.0),
    'headlineLarge': (32.0, 40.0), 'headlineMedium': (28.0, 36.0), 'headlineSmall': (24.0, 32.0),
    'titleLarge': (22.0, 28.0), 'titleMedium': (16.0, 24.0), 'titleSmall': (14.0, 20.0),
    'bodyLarge': (16.0, 24.0), 'bodyMedium': (14.0, 20.0), 'bodySmall': (12.0, 16.0),
    'labelLarge': (14.0, 20.0), 'labelMedium': (12.0, 16.0), 'labelSmall': (11.0, 16.0),
}

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r27/kotlin/dev/fusion/testing/R27Checks.kt').is_file())

class FeedFixtures(unittest.TestCase):
    def test_rss_fixture(self):
        root = ET.fromstring((FIX / 'sample-podcast.xml').read_text())
        items = root.findall('.//item')
        self.assertEqual(3, len(items))
        audio = [e for e in root.findall('.//enclosure') if e.attrib['type'].startswith(('audio/', 'video/'))]
        self.assertEqual(2, len(audio))
    def test_atom_fixture(self):
        ns = {'a': 'http://www.w3.org/2005/Atom'}
        root = ET.fromstring((FIX / 'sample-atom.xml').read_text())
        entries = root.findall('a:entry', ns)
        self.assertEqual(2, len(entries))
        enc = [l for l in root.findall('.//a:link', ns) if l.attrib.get('rel') == 'enclosure']
        self.assertEqual(2, len(enc))
    def test_variant_fixture(self):
        text = (FIX / 'variant.m3u8').read_text()
        self.assertEqual(3, len(re.findall(r'#EXT-X-STREAM-INF', text)))
        self.assertNotIn('EXT-X-KEY', text)

class GazetteerSource(unittest.TestCase):
    def test_places_table(self):
        src = (ROOT / 'feature/maps/src/main/kotlin/dev/fusion/maps/Gazetteer.kt').read_text()
        places = re.findall(r'Place\("([^"]+)", "([^"]+)", (-?[\d.]+), (-?[\d.]+)\)', src)
        self.assertGreaterEqual(len(places), 40)
        names = [p[0] for p in places]
        self.assertEqual(len(names), len(set(n.lower() for n in names)))
        self.assertIn('Raleigh', names)
        for name, country, lat, lon in places:
            self.assertTrue(-90.0 <= float(lat) <= 90.0, name)
            self.assertTrue(-180.0 <= float(lon) <= 180.0, name)
            self.assertEqual(float(lat), round(float(lat)), name)
            self.assertEqual(float(lon), round(float(lon)), name)

class TypeScaleFidelity(unittest.TestCase):
    def test_matches_m3_baseline(self):
        src = (ROOT / 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/TypeScale.kt').read_text()
        found = {m.group(1): (float(m.group(2)), float(m.group(3)))
                 for m in re.finditer(r'Style\("(\w+)", ([\d.]+), ([\d.]+), (?:true|false)\)', src)}
        self.assertEqual(M3_OFFICIAL, found)

class BlocklistFixture(unittest.TestCase):
    def test_grammar(self):
        lines = [l.split('#')[0].strip() for l in (FIX / 'blocklist.txt').read_text().splitlines()]
        rules = [l for l in lines if l]
        self.assertEqual(5, len(rules))
        for r in rules:
            self.assertTrue(re.match(r'^(0\.0\.0\.0|127\.0\.0\.1) \S+$', r) or
                            re.match(r'^(@@)?\|\|[a-z0-9.-]+\^$', r), r)
        self.assertTrue(any(r.startswith('@@') for r in rules))

class InventoryFixtures(unittest.TestCase):
    LINE = re.compile(r'^Bus\s+\d{1,3}\s+Device\s+\d{1,3}:\s+ID\s+[0-9a-fA-F]{4}:[0-9a-fA-F]{4}')
    def ids(self, name):
        return {l.split('ID')[1].split()[0].lower() for l in (FIX / name).read_text().splitlines() if l.strip()}
    def test_diff_counts(self):
        for name in ('inventory-a.txt', 'inventory-b.txt'):
            for l in (FIX / name).read_text().splitlines():
                if l.strip():
                    self.assertRegex(l.split(' {')[0], self.LINE)
        a, b = self.ids('inventory-a.txt'), self.ids('inventory-b.txt')
        self.assertEqual({'046d:c31c', '1a2b:3c4d'}, b - a)
        self.assertEqual({'0781:5567'}, a - b)

class CronCrossCheck(unittest.TestCase):
    def next_fire(self, expr, after):
        f = expr.split()
        mins = self.expand(f[0], 0, 59); hours = self.expand(f[1], 0, 23)
        doms = self.expand(f[2], 1, 31); mons = self.expand(f[3], 1, 12); dows = self.expand(f[4], 0, 6)
        dom_r, dow_r = f[2] != '*', f[4] != '*'
        t = after + datetime.timedelta(minutes=1)
        for _ in range(525600):
            dow = (t.weekday() + 1) % 7
            day = (t.day in doms or dow in dows) if (dom_r and dow_r) else \
                  (t.day in doms if dom_r else (dow in dows if dow_r else True))
            if t.minute in mins and t.hour in hours and t.month in mons and day:
                return t
            t += datetime.timedelta(minutes=1)
        return None
    def expand(self, field, lo, hi):
        out = set()
        for part in field.split(','):
            step = int(part.split('/')[1]) if '/' in part else 1
            rng = part.split('/')[0]
            a, b = (lo, hi) if rng == '*' else (int(rng.split('-')[0]), int(rng.split('-')[1]) if '-' in rng else int(rng))
            out.update(range(a, b + 1, step))
        return out
    def test_vectors(self):
        start = datetime.datetime(2026, 10, 21, 9, 0)
        self.assertEqual(datetime.datetime(2026, 10, 21, 9, 30), self.next_fire('30 9 * * *', start))
        self.assertEqual(datetime.datetime(2026, 10, 26, 12, 0), self.next_fire('0 12 * * 1', start))

class FirewallSource(unittest.TestCase):
    def test_fail_closed_text(self):
        src = (ROOT / 'core/security/src/main/kotlin/dev/fusion/security/FirewallPolicy.kt').read_text()
        for token in ('policy drop', 'redirect to :', 'REVIEW PROPOSAL ONLY', 'authorityAttested', 'meta protocol != ip drop'):
            self.assertIn(token, src)
        self.assertNotIn('\n    accept\n', src)

if __name__ == '__main__':
    unittest.main()
