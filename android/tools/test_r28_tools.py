#!/usr/bin/env python3
"""Pure-Python R28 tests. No kotlinc, SDK or device needed; run from android/ with tools/ on PYTHONPATH."""
import datetime
import math
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r28'
KERNELS = [
    'core/files/src/main/kotlin/dev/fusion/files/TrashLedger.kt',
    'core/windowing/src/main/kotlin/dev/fusion/windowing/SnapZones.kt',
    'feature/find/src/main/kotlin/dev/fusion/find/FrecencyLedger.kt',
    'feature/maps/src/main/kotlin/dev/fusion/maps/TileMath.kt',
    'feature/media/src/main/kotlin/dev/fusion/media/ChapterMarks.kt',
    'core/security/src/main/kotlin/dev/fusion/security/ConsentLedger.kt',
    'feature/browser/src/main/kotlin/dev/fusion/browser/model/CookieJar.kt',
    'feature/automation/src/main/kotlin/dev/fusion/automation/Recurrence.kt',
    'feature/notes/src/main/kotlin/dev/fusion/notes/FrontMatter.kt',
    'feature/terminal/src/main/kotlin/dev/fusion/terminal/Scrollback.kt',
]

class KernelHygiene(unittest.TestCase):
    def test_kernels_are_host_only(self):
        for rel in KERNELS:
            text = (ROOT / rel).read_text()
            self.assertNotRegex(text, r'import (android|androidx)\.', f'{rel} must not import Android APIs')
            self.assertIn('SPDX-License-Identifier: Apache-2.0', text)
    def test_checks_consumer_exists(self):
        self.assertTrue((ROOT / 'testing/src/r28/kotlin/dev/fusion/testing/R28Checks.kt').is_file())

class ChaptersFixture(unittest.TestCase):
    def test_starts_monotonic(self):
        ns = {'p': 'http://podlove.org/simple-chapters'}
        root = ET.fromstring((FIX / 'chapters.xml').read_text())
        chs = root.findall('p:chapter', ns)
        self.assertEqual(3, len(chs))
        def ms(s):
            m = re.match(r'(?:(\d+):)?([0-5]?\d):([0-5]\d)(?:\.(\d{1,3}))?$', s)
            h, mi, se, fr = int(m.group(1) or 0), int(m.group(2)), int(m.group(3)), int((m.group(4) or '0').ljust(3, '0'))
            return h * 3600000 + mi * 60000 + se * 1000 + fr
        starts = [ms(c.attrib['start']) for c in chs]
        self.assertEqual([0, 205500, 4325000], starts)
        self.assertTrue(all(chs[i].attrib['title'] for i in range(3)))

class CookiesFixture(unittest.TestCase):
    def test_grammar(self):
        lines = [l.strip() for l in (FIX / 'cookies.txt').read_text().splitlines() if l.strip()]
        self.assertEqual(3, len(lines))
        for line in lines:
            name = line.split(';')[0]
            self.assertRegex(name, r'^[A-Za-z0-9!#$%&*+\-.^_`|~]+=.*$')
        self.assertTrue(any('Secure' in l for l in lines))

class FrontMatterFixture(unittest.TestCase):
    def test_shape(self):
        text = (FIX / 'note-fm.md').read_text()
        self.assertTrue(text.startswith('---\n'))
        header, body = text.split('---\n', 2)[1:]
        keys = [l.split(':')[0] for l in header.splitlines() if l.strip()]
        self.assertEqual(['title', 'draft', 'rating', 'tags'], keys)
        self.assertTrue(body.strip().startswith('# Body'))

class TrashFixture(unittest.TestCase):
    def test_expiry(self):
        now = 1761000000 + 8 * 86400
        expired = []
        for line in (FIX / 'trash.txt').read_text().splitlines():
            i, name, b, t, r = line.split('|')
            self.assertFalse('/' in name)
            if int(t) + int(r) * 86400 <= now:
                expired.append(i)
        self.assertEqual(['b2'], expired)

class TileMathCrossCheck(unittest.TestCase):
    def tile(self, lon, lat, z):
        n = 2 ** z
        x = min(int((lon + 180.0) / 360.0 * n), n - 1)
        lat = max(min(lat, 85.05112878), -85.05112878)
        y = min(int((1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2.0 * n), n - 1)
        return x, y
    def quadkey(self, x, y, z):
        return ''.join(str(((x >> (z - 1 - i)) & 1) + 2 * ((y >> (z - 1 - i)) & 1)) for i in range(z))
    def test_vectors(self):
        self.assertEqual((0, 0), self.tile(0.0, 0.0, 0))
        self.assertEqual((1, 1), self.tile(0.0, 0.0, 1))
        self.assertEqual('3', self.quadkey(1, 1, 1))
        q = self.quadkey(550, 335, 10)
        self.assertEqual(10, len(q))
        self.assertTrue(set(q) <= set('0123'))

class RecurrenceCrossCheck(unittest.TestCase):
    def expand(self, freq, interval, byday, count, start):
        out, day = [], start
        while len(out) < count and day <= start + datetime.timedelta(days=2000):
            ok = False
            if freq == 'DAILY':
                ok = (day - start).days % interval == 0
            elif freq == 'WEEKLY':
                monday = start - datetime.timedelta(days=start.weekday())
                ok = ((day - monday).days // 7) % interval == 0 and day.isoweekday() in byday
            elif freq == 'MONTHLY':
                months = (day.year - start.year) * 12 + day.month - start.month
                ok = months % interval == 0 and day.day == start.day
            if ok:
                out.append(day)
            day += datetime.timedelta(days=1)
        return out
    def test_vectors(self):
        s = datetime.date(2026, 10, 21)
        self.assertEqual([datetime.date(2026, 10, d) for d in (21, 23, 25, 27, 29)],
                         self.expand('DAILY', 2, set(), 5, s))
        self.assertEqual([datetime.date(2026, 10, 21), datetime.date(2026, 10, 26),
                          datetime.date(2026, 10, 28), datetime.date(2026, 11, 2)],
                         self.expand('WEEKLY', 1, {1, 3}, 4, s))
        self.assertEqual([datetime.date(2026, 1, 31), datetime.date(2026, 3, 31), datetime.date(2026, 5, 31)],
                         self.expand('MONTHLY', 1, set(), 3, datetime.date(2026, 1, 31)))

class FrecencyDecay(unittest.TestCase):
    def test_half_life(self):
        self.assertEqual(0.5, 0.5 ** ((14 * 86400) / (14 * 86400)))
        self.assertAlmostEqual(0.9999427, 0.5 ** (100 / (14 * 86400)), places=6)

class ScrollbackWrap(unittest.TestCase):
    def test_greedy(self):
        line = '0123456789abcdef' * 2
        chunks, rest = [], line
        while len(rest) > 8:
            chunks.append(rest[:8])
            rest = rest[8:]
        chunks.append(rest)
        self.assertEqual(['01234567', '89abcdef', '01234567', '89abcdef'], chunks)

if __name__ == '__main__':
    unittest.main()
