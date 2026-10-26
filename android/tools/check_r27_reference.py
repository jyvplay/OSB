#!/usr/bin/env python3
"""Independent Python recomputation of R27 kernel behavior from fixtures/sources.

Cross-checks podcast enclosure counts, blocklist decisions, cron next-fire vectors,
the M3 type table and gazetteer shape with independent implementations. Agreement is
cross-language evidence for the LOGIC ONLY -- not Android, device, service, or
acceptance evidence.
"""
import datetime
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r27'

def cron_next(expr, after):
    f = expr.split()
    def expand(field, lo, hi):
        out = set()
        for part in field.split(','):
            step = int(part.split('/')[1]) if '/' in part else 1
            rng = part.split('/')[0]
            a, b = (lo, hi) if rng == '*' else (int(rng.split('-')[0]), int(rng.split('-')[1]) if '-' in rng else int(rng))
            out.update(range(a, b + 1, step))
        return out
    mins, hours = expand(f[0], 0, 59), expand(f[1], 0, 23)
    doms, mons = expand(f[2], 1, 31), expand(f[3], 1, 12)
    dows = {0 if d == 7 else d for d in expand(f[4], 0, 7)}
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

def main():
    report = {}
    # Podcast counts.
    rss = ET.fromstring((FIX / 'sample-podcast.xml').read_text())
    audio = [e for e in rss.findall('.//enclosure') if e.attrib['type'].startswith(('audio/', 'video/'))]
    skipped = len(rss.findall('.//enclosure')) - len(audio)
    assert (len(audio), skipped) == (2, 1), (len(audio), skipped)
    ns = {'a': 'http://www.w3.org/2005/Atom'}
    atom = ET.fromstring((FIX / 'sample-atom.xml').read_text())
    enc = [l for l in atom.findall('.//a:link', ns) if l.attrib.get('rel') == 'enclosure']
    assert len(enc) == 2
    report['podcast'] = {'rss_episodes': 2, 'rss_skipped': 1, 'atom_episodes': 2}
    # Blocklist mirror.
    blocked, exceptions = set(), set()
    for line in (FIX / 'blocklist.txt').read_text().splitlines():
        line = line.split('#')[0].strip()
        if not line:
            continue
        if line.startswith('@@||'):
            exceptions.add(line[4:-1])
        elif line.startswith('||'):
            blocked.add(line[2:-1])
        else:
            blocked.add(line.split()[1].lower())
    def decide(host):
        host = host.strip().strip('.').lower()
        if any(host == d or host.endswith('.' + d) for d in exceptions):
            return True
        if any(host == d or host.endswith('.' + d) for d in blocked):
            return False
        return True
    expected = {'tracker.example.net': False, 'sub.ads.example.net': False, 'beacon.example.org': False,
                'a.b.metrics.example.org': False, 'safe.metrics.example.org': True, 'example.com': True}
    for host, want in expected.items():
        assert decide(host) == want, host
    report['blocklist'] = {'rules': len(blocked) + len(exceptions), 'decisions_verified': len(expected)}
    # Cron vectors.
    start = datetime.datetime(2026, 10, 21, 9, 0)
    assert cron_next('30 9 * * *', start) == datetime.datetime(2026, 10, 21, 9, 30)
    assert cron_next('0 12 * * 1', start) == datetime.datetime(2026, 10, 26, 12, 0)
    report['cron'] = {'vectors': 2}
    # M3 table fidelity.
    src = (ROOT / 'core/designsystem/src/main/kotlin/dev/fusion/designsystem/TypeScale.kt').read_text()
    found = re.findall(r'Style\("(\w+)", ([\d.]+), ([\d.]+), (?:true|false)\)', src)
    assert len(found) == 15, len(found)
    official = {'displayLarge': (57.0, 64.0), 'displayMedium': (45.0, 52.0), 'displaySmall': (36.0, 44.0),
                'headlineLarge': (32.0, 40.0), 'headlineMedium': (28.0, 36.0), 'headlineSmall': (24.0, 32.0),
                'titleLarge': (22.0, 28.0), 'titleMedium': (16.0, 24.0), 'titleSmall': (14.0, 20.0),
                'bodyLarge': (16.0, 24.0), 'bodyMedium': (14.0, 20.0), 'bodySmall': (12.0, 16.0),
                'labelLarge': (14.0, 20.0), 'labelMedium': (12.0, 16.0), 'labelSmall': (11.0, 16.0)}
    for name, size, lh in found:
        assert official[name] == (float(size), float(lh)), name
    report['typescale'] = {'styles': 15}
    # Gazetteer shape.
    gaz = (ROOT / 'feature/maps/src/main/kotlin/dev/fusion/maps/Gazetteer.kt').read_text()
    places = re.findall(r'Place\("([^"]+)", "([^"]+)", (-?[\d.]+), (-?[\d.]+)\)', gaz)
    assert len(places) >= 40
    assert all(float(lat) == round(float(lat)) and float(lon) == round(float(lon)) for _, _, lat, lon in places)
    report['gazetteer'] = {'places': len(places)}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r27/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print(f"R27 reference PASS: podcast 2+1/2, blocklist {len(expected)}/6 decisions, cron 2 vectors, M3 15/15, gazetteer {len(places)} places.")

if __name__ == '__main__':
    main()
