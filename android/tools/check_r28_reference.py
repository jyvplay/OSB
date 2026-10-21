#!/usr/bin/env python3
"""Independent Python recomputation of R28 kernel behavior from fixtures/sources.

Cross-checks chapters, cookie matching, recurrence vectors, tile math, trash
purge order and front-matter with independent implementations. Agreement is
cross-language evidence for the LOGIC ONLY -- not Android, device, service, or
acceptance evidence.
"""
import datetime
import json
import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r28'

def main():
    report = {}
    # Chapters.
    ns = {'p': 'http://podlove.org/simple-chapters'}
    chs = ET.fromstring((FIX / 'chapters.xml').read_text()).findall('p:chapter', ns)
    def ms(s):
        m = re.match(r'(?:(\d+):)?([0-5]?\d):([0-5]\d)(?:\.(\d{1,3}))?$', s)
        return (int(m.group(1) or 0) * 3600000 + int(m.group(2)) * 60000 +
                int(m.group(3)) * 1000 + int((m.group(4) or '0').ljust(3, '0')))
    starts = [ms(c.attrib['start']) for c in chs]
    assert starts == [0, 205500, 4325000] and all(b > a for a, b in zip(starts, starts[1:]))
    report['chapters'] = {'count': 3, 'starts_ms': starts}
    # Cookies: independent matcher mirror over the fixture's first three rules.
    jar = []  # (name, value, domain, hostOnly, path, secure, created)
    for line in (FIX / 'cookies.txt').read_text().splitlines():
        if not line.strip():
            continue
        parts = [p.strip() for p in line.split(';')]
        name, value = parts[0].split('=', 1)
        domain, host_only, path, secure = 'www.example.com', True, '/', False
        for attr in parts[1:]:
            k = attr.split('=')[0].strip().lower()
            v = attr.split('=', 1)[1].strip() if '=' in attr else ''
            if k == 'domain':
                domain, host_only = v.lstrip('.').lower(), False
            elif k == 'path' and v.startswith('/'):
                path = v
            elif k == 'secure':
                secure = True
        jar.append((name, value, domain, host_only, path, secure))
    def header(host, req_path, is_secure):
        out = []
        for name, value, domain, host_only, path, secure in jar:
            dm = (host == domain) if host_only else (host == domain or host.endswith('.' + domain))
            pm = req_path == path or (req_path.startswith(path) and
                                      (path.endswith('/') or req_path[len(path)] == '/'))
            if dm and pm and (not secure or is_secure):
                out.append(f'{name}={value}')
        return '; '.join(out)
    assert header('www.example.com', '/', False) == 'session=abc123; prefs=dark'
    assert header('www.example.com', '/app', True) == 'session=abc123; prefs=dark; appmode=full'
    assert header('www.example.com', '/application', True) == 'session=abc123; prefs=dark'
    assert header('sub.example.com', '/', True) == 'prefs=dark'
    report['cookies'] = {'rules': 3, 'decisions_verified': 4}
    # Recurrence vectors.
    def expand(freq, interval, byday, count, start, until=None):
        out, day = [], start
        while len(out) < count and (until is None or day <= until) and day <= start + datetime.timedelta(days=2000):
            ok = ((day - start).days % interval == 0 if freq == 'DAILY' else
                  (((day - (start - datetime.timedelta(days=start.weekday()))).days // 7) % interval == 0 and
                   day.isoweekday() in byday) if freq == 'WEEKLY' else
                  (((day.year - start.year) * 12 + day.month - start.month) % interval == 0 and
                   day.day == start.day))
            if ok:
                out.append(day)
            day += datetime.timedelta(days=1)
        return out
    s = datetime.date(2026, 10, 21)
    assert expand('DAILY', 2, set(), 5, s) == [datetime.date(2026, 10, d) for d in (21, 23, 25, 27, 29)]
    assert expand('WEEKLY', 1, {1, 3}, 4, s) == [datetime.date(2026, 10, 21), datetime.date(2026, 10, 26),
                                                datetime.date(2026, 10, 28), datetime.date(2026, 11, 2)]
    assert expand('MONTHLY', 1, set(), 3, datetime.date(2026, 1, 31)) == [
        datetime.date(2026, 1, 31), datetime.date(2026, 3, 31), datetime.date(2026, 5, 31)]
    report['recurrence'] = {'vectors': 3}
    # Tiles.
    def tile(lon, lat, z):
        n = 2 ** z
        x = min(int((lon + 180.0) / 360.0 * n), n - 1)
        lat = max(min(lat, 85.05112878), -85.05112878)
        y = min(int((1.0 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2.0 * n), n - 1)
        return x, y
    assert tile(0.0, 0.0, 1) == (1, 1)
    assert tile(13.4, 52.5, 10) == (550, 335)
    report['tiles'] = {'vectors': 2}
    # Trash purge mirror.
    now = 1761000000 + 8 * 86400
    entries = []
    for line in (FIX / 'trash.txt').read_text().splitlines():
        i, name, b, t, r = line.split('|')
        entries.append((i, int(b), int(t) + int(r) * 86400))
    expired = sorted([e for e in entries if e[2] <= now], key=lambda e: (e[2], e[0]))
    assert [e[0] for e in expired] == ['b2']
    rest = sorted([e for e in entries if e[2] > now], key=lambda e: (-e[1], e[0]))
    assert [e[0] for e in expired + rest] == ['b2', 'c3', 'a1']
    report['trash'] = {'entries': 3}
    # Front-matter mirror.
    text = (FIX / 'note-fm.md').read_text()
    assert text.startswith('---\n') and '\n---\n' in text[4:]
    header_txt = text[4:text.index('\n---\n', 4)]
    keys = [l.split(':')[0].strip() for l in header_txt.splitlines() if l.strip()]
    assert keys == ['title', 'draft', 'rating', 'tags']
    report['frontmatter'] = {'keys': 4}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r28/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print('R28 reference PASS: chapters 3, cookies 4/4 decisions, recurrence 3 vectors, tiles 2 vectors, trash 3, front-matter 4 keys.')

if __name__ == '__main__':
    main()
