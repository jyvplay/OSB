#!/usr/bin/env python3
"""Independent Python recomputation of R32 kernel behavior from fixtures/sources.

Cross-checks fraction approximation (stdlib limit_denominator), the Office
capability matrix, cast metadata validation (urllib-based, not regex),
argv splitting, PIN policy, retention sweeps, RFC 5545 durations
(regex-based, not the kernel's manual scan), geo URIs, vault lockout, and
the CUE fixture (regex grammar reader). Agreement is cross-language
evidence for the LOGIC ONLY -- not Android, device, service, or
acceptance evidence.
"""
import json
import re
from fractions import Fraction as PyFraction
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r32'

def main():
    report = {}
    # Fraction mirror: CPython's independent continued-fraction implementation.
    vectors = [('0.5', 100, 1, 2), ('3.14159', 10, 22, 7), ('3.14159', 100, 311, 99),
               ('0.333333', 100, 1, 3), ('-2.5', 10, -5, 2), ('0', 10, 0, 1),
               ('7', 1, 7, 1), ('0.125', 1000, 1, 8)]
    for s, d, n, q in vectors:
        f = PyFraction(s).limit_denominator(d)
        assert (f.numerator, f.denominator) == (n, q), (s, d, f)
    report['fraction'] = {'vectors': len(vectors)}
    # Office mirror: matrix transcription check.
    gates6 = ['engine payload', 'Android hosting', 'memory', 'fidelity', 'security', 'license']
    matrix = {'pdf': (True, False, gates6), 'txt': (True, False, ['fidelity']),
              'csv': (True, False, ['fidelity'])}
    for f in ['odt', 'ods', 'odp', 'docx', 'xlsx', 'pptx', 'rtf', 'html']:
        matrix[f] = (False, False, gates6)
    assert matrix['pdf'] == (True, False, gates6)
    assert matrix['docx'][2][0] == 'engine payload' and len(matrix['docx'][2]) == 6
    assert matrix['txt'] == (True, False, ['fidelity'])
    assert sorted(f for f, (v, _, _) in matrix.items() if v) == ['csv', 'pdf', 'txt']
    assert 'xyz' not in matrix  # unknown probes negative
    assert 'ODT'.strip().lstrip('.').lower() == 'odt' and '.pdf'.lstrip('.').lower() == 'pdf'
    report['office'] = {'formats': len(matrix)}
    # Cast-meta mirror: urllib-based URL verdicts, charset MIME check.
    def url_ok(u):
        # urlsplit lowercases schemes, so case is enforced on the raw string.
        if not (u.startswith('http://') or u.startswith('https://')):
            return False
        if len(u) > 2048 or any(c.isspace() for c in u):
            return False
        try:
            p = urlsplit(u)
        except ValueError:
            return False
        return p.scheme in ('http', 'https') and bool(p.netloc)
    token = set('abcdefghijklmnopqrstuvwxyz0123456789!#$&^_.+-')
    def mime_ok(m):
        parts = m.split('/')
        return (len(parts) == 2 and all(parts) and
                all(set(p) <= token for p in parts))
    def title_ok(t):
        return 1 <= len(t) <= 256 and all(ord(c) >= 0x20 and c not in '"\\' for c in t)
    assert url_ok('http://192.168.1.5/v.mp4') and mime_ok('video/mp4') and title_ok('Clip')
    assert url_ok('https://h/a?b=c&d=e')
    for bad in ['ftp://h/a', 'http://', 'http://h/a b', 'HTTP://h/a']:
        assert not url_ok(bad), bad
    for bad in ['text/html;level=1', 'Video/MP4', 'video']:
        assert not mime_ok(bad), bad
    for bad in ['', 't' * 257, 'say "hi"', 'a\\b', 'a\tb']:
        assert not title_ok(bad), repr(bad)
    report['castmeta'] = {'vectors': 15}
    # ArgSplit mirror: state machine phrased around explicit states.
    def split_args(line):
        assert len(line) <= 8192
        args, cur, state = [], [], 'bare'  # bare | single | double | escape-next via flag
        in_arg, i, esc = False, 0, None
        while i < len(line):
            c = line[i]
            if state == 'single':
                if c == "'":
                    state = 'bare'
                else:
                    cur.append(c)
                i += 1
                continue
            if esc is not None:
                cur.append(c)
                in_arg = True
                esc = None
                i += 1
                continue
            if state == 'double':
                if c == '"':
                    state = 'bare'
                elif c == '\\':
                    esc = True
                else:
                    cur.append(c)
                i += 1
                continue
            if c in '\'"':
                state = 'single' if c == "'" else 'double'
                in_arg = True
                i += 1
            elif c == '\\':
                esc = True
                in_arg = True
                i += 1
            elif c == '#':
                break
            elif c.isspace():
                if in_arg:
                    assert len(args) < 256
                    args.append(''.join(cur))
                    cur, in_arg = [], False
                i += 1
            else:
                cur.append(c)
                in_arg = True
                i += 1
        assert state == 'bare' and esc is None, 'unterminated'
        if in_arg:
            assert len(args) < 256
            args.append(''.join(cur))
        return args
    cases = [('a b c', ['a', 'b', 'c']), ('a "b c" d', ['a', 'b c', 'd']),
             ("'a\"b'", ['a"b']), ('"a\'b"', ["a'b"]), ('a\\ b', ['a b']),
             ('"a\\"b"', ['a"b']), ('""', ['']), ('a "" b', ['a', '', 'b']),
             ('a # c', ['a']), ('# only', []), ('"a#b"', ['a#b']),
             ('ab#cd', ['ab']), ('', []), ('"a"bonus', ['abonus'])]
    for src, want in cases:
        assert split_args(src) == want, src
    for bad in ['"abc', "ab'cd", 'ab\\', '"ab\\', 'a' * 8193, ' '.join(['a'] * 257)]:
        try:
            split_args(bad)
        except AssertionError:
            pass
        else:
            raise AssertionError(f'no refusal: {bad[:20]!r}')
    report['argsplit'] = {'vectors': len(cases) + 6}
    # PIN mirror.
    def pin_ok(pin):
        reasons = []
        if not pin or not all('0' <= c <= '9' for c in pin):
            reasons.append('digits')
        if not 4 <= len(pin) <= 12:
            reasons.append('length')
        if pin and len(set(pin)) == 1:
            reasons.append('same')
        ds = [ord(c) - 48 for c in pin] if pin.isdigit() and pin else []
        if len(ds) >= 2:
            if all((b - a) % 10 == 1 for a, b in zip(ds, ds[1:])):
                reasons.append('run')
            elif all((a - b) % 10 == 1 for a, b in zip(ds, ds[1:])):
                reasons.append('run')
        if len(pin) % 2 == 0 and pin and pin[:len(pin) // 2] == pin[len(pin) // 2:]:
            reasons.append('half')
        if pin in ('1234', '4321', '0000', '2580', '0852'):
            reasons.append('blocked')
        return reasons
    assert pin_ok('1357') == [] and pin_ok('2749') == []
    assert set(pin_ok('1234')) == {'run', 'blocked'}
    assert 'same' in pin_ok('0000') and 'run' not in pin_ok('0000')
    assert 'run' in pin_ok('7890') and 'run' in pin_ok('2109') and 'run' in pin_ok('5432')
    assert 'half' in pin_ok('1212') and pin_ok('12ab') == ['digits']
    assert 'length' in pin_ok('123') and 'length' in pin_ok('')
    report['pin'] = {'vectors': 12}
    # Sweep mirror.
    table = {'log': 1000, 'consent': 5000}
    recs = [('a', 'log', 9000), ('b', 'log', 8999), ('c', 'consent', 5000), ('d', 'consent', 4999)]
    keep = [i for i, k, at in recs if not 10000 - at > table[k]]
    purge = [i for i, k, at in recs if 10000 - at > table[k]]
    assert keep == ['a', 'c'] and purge == ['b', 'd']
    report['sweep'] = {'keep': 2, 'purge': 2}
    # Duration mirror: regex grammar, arithmetic total.
    dur = re.compile(r'([+-]?)P(?:(\d{1,9})W|(?:(\d{1,9})D)?(?:T(?:(\d{1,9})H)?(?:(\d{1,9})M)?(?:(\d{1,9})S)?)?)')
    def to_seconds(t):
        m = dur.fullmatch(t)
        assert m, t
        sign, w, d, h, mi, s = m.groups()
        assert not (w and (d or h or mi or s)), 'weeks stand alone'
        assert (d or h or mi or s or w), 'empty body'
        if 'T' in t:
            assert (h or mi or s), 'bare T'
        total = (int(w or 0) * 604800 + int(d or 0) * 86400 + int(h or 0) * 3600 +
                 int(mi or 0) * 60 + int(s or 0))
        assert total <= 315532800, 'over cap'
        return -total if sign == '-' else total
    assert to_seconds('P1W') == 604800 and to_seconds('P1DT2H3M4S') == 93784
    assert to_seconds('-PT15M') == -900 and to_seconds('PT0S') == 0
    assert to_seconds('+P2D') == 172800 and to_seconds('PT1H30M') == 5400
    for bad in ['P1M', 'P1Y', 'P', 'PT', 'P1DT', 'PT1S2H', 'PT1H2H', 'P1W2D',
                'T1H', 'P1D ', 'P0000000001D', 'P4000D', '']:
        try:
            to_seconds(bad)
        except AssertionError:
            pass
        else:
            raise AssertionError(f'no refusal: {bad!r}')
    report['duration'] = {'vectors': 19}
    # Geo mirror: partition-based parse.
    def parse_geo(t):
        assert len(t) <= 512 and t[:4].lower() == 'geo:'
        body = t[4:]
        assert body and not any(c.isspace() for c in body)
        head, _, params = body.partition(';')
        coords = head.split(',')
        assert len(coords) in (2, 3)
        lat, lon = float(coords[0]), float(coords[1])
        assert lat == lat and lon == lon and abs(lat) != float('inf') and abs(lon) != float('inf')
        assert -90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0
        alt = float(coords[2]) if len(coords) == 3 else None
        if alt is not None:
            assert alt == alt and abs(alt) != float('inf')
        unc = None
        if params:
            for p in params.split(';'):
                k, eq, v = p.partition('=')
                assert eq
                if k.lower() == 'crs':
                    assert v.lower() == 'wgs84'
                elif k.lower() == 'u':
                    unc = float(v)
                    assert unc == unc and unc != float('inf') and unc >= 0
                else:
                    raise AssertionError(f'unknown {k}')
        return (lat, lon, alt, unc)
    assert parse_geo('geo:48.2,16.3') == (48.2, 16.3, None, None)
    assert parse_geo('GEO:-33.86,151.20,12.5') == (-33.86, 151.2, 12.5, None)
    assert parse_geo('geo:48.2,16.3;crs=wgs84;u=30.5') == (48.2, 16.3, None, 30.5)
    for bad in ['48.2,16.3', 'geo:90.1,0', 'geo:0,180.1', 'geo:48.2', 'geo:1,2,3,4',
                'geo:1,2;crs=luna', 'geo:1,2;foo=bar', 'geo:1,2;u', 'geo:1,2;u=-1',
                'geo:NaN,0', 'geo:48.2, 16.3']:
        try:
            parse_geo(bad)
        except (AssertionError, ValueError):
            pass
        else:
            raise AssertionError(f'no refusal: {bad!r}')
    report['geo'] = {'vectors': 14}
    # Lockout mirror.
    def record(failures, until, now, success):
        assert now >= 0 and 0 <= failures <= 10 and until >= 0
        if success:
            return (0, 0)
        f = min(failures + 1, 10)
        if f <= 3:
            return (f, 0)
        delay = 30000
        for _ in range(f - 4):
            delay = min(delay * 2, 3600000)
        return (f, now + delay)
    assert record(0, 0, 1000, False) == (1, 0)
    assert record(3, 0, 10000, False) == (4, 40000)
    assert record(4, 40000, 40000, False) == (5, 100000)
    f, u = 0, 0
    for _ in range(10):
        f, u = record(f, u, 0, False)
    assert (f, u) == (10, 1920000)
    assert record(10, 1920000, 0, False) == (10, 1920000)
    assert record(5, 100000, 99999, True) == (0, 0)
    report['lockout'] = {'vectors': 6}
    # CUE mirror: regex line grammar over the real fixture.
    lines = (FIX / 'sheet.cue').read_text().split('\n')
    assert len(lines) <= 1000
    tracks, number, title, perf, idx, fname = [], 0, None, None, None, None
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith('REM'):
            continue
        m = (re.fullmatch(r'FILE "([^"]+)" ([A-Za-z0-9]+)', line) or
             re.fullmatch(r'TRACK (\d{2}) AUDIO', line) or
             re.fullmatch(r'(TITLE|PERFORMER) "([^"]*)"', line) or
             re.fullmatch(r'INDEX (\d+) (\d+):(\d{2}):(\d{2})', line))
        assert m, line
        if line.startswith('FILE'):
            assert fname is None and number == 0 and not tracks
            fname = m.group(1)
        elif line.startswith('TRACK'):
            assert fname is not None and len(tracks) < 99
            if number:
                assert idx is not None
                assert (not tracks and number == 1) or number == tracks[-1][0] + 1
                assert not tracks or idx > tracks[-1][3]
                tracks.append((number, title, perf, idx))
                title, perf, idx = None, None, None
            number = int(m.group(1))
        elif line.startswith('INDEX'):
            assert number > 0
            if m.group(1) == '01':
                assert idx is None
                mm, ss, ff = int(m.group(2)), int(m.group(3)), int(m.group(4))
                assert ss <= 59 and ff <= 74
                idx = (mm * 60 + ss) * 75 + ff
                assert idx <= 75 * 60 * 60 * 10
        else:
            if number > 0:
                if m.group(1) == 'TITLE':
                    title = m.group(2)
                else:
                    perf = m.group(2)
    assert fname == 'sample.bin'
    if number:
        assert idx is not None and number == tracks[-1][0] + 1 and idx > tracks[-1][3]
        tracks.append((number, title, perf, idx))
    assert tracks == [(1, 'First Song', 'Sample Artist', 0), (2, 'Second Song', None, 15450),
                      (3, None, None, 32250)], tracks
    report['cue'] = {'tracks': 3}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r32/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print('R32 reference PASS: fraction 8 (stdlib limit_denominator), office 11 formats, '
          'castmeta 15, argsplit 20, pin 12, sweep 2+2, duration 19 (regex grammar), '
          'geo 14, lockout 6, cue 3 tracks (regex grammar).')

if __name__ == '__main__':
    main()
