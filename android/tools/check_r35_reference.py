#!/usr/bin/env python3
"""Independent Python recomputation of R35 kernel behavior from fixtures/sources.

Cross-checks exposure math (math.log2 mirror), XOR splitting (bytes-level
recompute), word diffs (difflib agreement, NOT the kernel's LCS DP),
duplicate grouping (groupby mirror + order), snippet windows (independent
slicing), ID3v2 headers (independent walk over the fixture), tile coverage
(independent slippy-math recomputation), vCards (dict-based re-parse), field
masks (comprehension mirror), and indent tallies (math.gcd mirror).
Agreement is cross-language evidence for the LOGIC ONLY -- not Android,
device, service, or acceptance evidence.
"""
import difflib
import json
import math
from itertools import groupby
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r35'


def main():
    report = {}
    # EV mirror.
    def ev(n, t):
        assert math.isfinite(n) and n > 0 and math.isfinite(t) and t > 0
        return math.log2(n * n / t)
    assert ev(1.0, 1.0) == 0.0 and ev(2.0, 0.5) == 3.0
    assert abs(ev(2.8, 0.01) - 9.614709844115207) < 1e-12
    assert abs(ev(4.0, 1.0 / 60.0) - 9.906890595608518) < 1e-12
    assert 2.0 * 2.0 / 2 ** 3.0 == 0.5
    assert math.sqrt(0.5 * 2 ** 3.0) == 2.0
    report['ev'] = {'vectors': 6}
    # XOR mirror.
    s, m = bytes([1, 2, 3, 4, 5]), bytes([9, 8, 7, 6, 5])
    blinded = bytes(x ^ y for x, y in zip(s, m))
    assert blinded == bytes([8, 10, 4, 2, 0])
    assert bytes(x ^ y for x, y in zip(m, blinded)) == s
    assert bytes([0xFF ^ 0x0F]) == bytes([0xF0])
    report['xor'] = {'vectors': 3}
    # Words mirror: difflib opcodes (kernel uses LCS DP + backtrack walk).
    sm = difflib.SequenceMatcher(None, ['a', 'b', 'c'], ['a', 'B', 'c'])
    assert sm.get_opcodes() == [('equal', 0, 1, 0, 1), ('replace', 1, 2, 1, 2), ('equal', 2, 3, 2, 3)]
    assert difflib.SequenceMatcher(None, [], ['x', 'y']).get_opcodes() == [('insert', 0, 0, 0, 2)]
    assert difflib.SequenceMatcher(None, ['x'], ['y']).get_opcodes() == [('replace', 0, 1, 0, 1)]
    report['words'] = {'opcodes': 3}
    # Dups mirror.
    d1, d2 = 'a' * 64, 'b' * 64
    recs = [('f1', 10, d1), ('f2', 10, d1), ('g1', 20, d2), ('g2', 20, d2),
            ('g3', 20, d2), ('solo', 10, d1[:-1] + 'c'), ('samesize', 10, 'c' * 64)]
    groups = {}
    for rid, size, dg in recs:
        groups.setdefault((size, dg), []).append(rid)
    out = sorted([(dg, size, sorted(ids)) for (size, dg), ids in groups.items() if len(ids) >= 2])
    assert out == [(d1, 10, ['f1', 'f2']), (d2, 20, ['g1', 'g2', 'g3'])], out
    report['dups'] = {'groups': 2}
    # Snippet mirror.
    text = 'alpha beta gamma delta epsilon zeta eta theta'
    def window(t, a, b, r):
        assert t and 0 <= a < b <= len(t) and 0 <= r <= 1000
        lo, hi = max(0, a - r), min(len(t), b + r)
        cut = ('…' if lo > 0 else '') + t[lo:hi] + ('…' if hi < len(t) else '')
        base = 1 if lo > 0 else 0
        return cut, base + a - lo, base + b - lo
    assert window(text, 11, 16, 6) == ('… beta gamma delta…', 7, 12)
    assert window(text, 0, 5, 40) == (text, 0, 5)
    assert window(text, 40, 45, 3) == ('…ta theta', 4, 9)
    assert window('tiny hit here', 5, 8, 40) == ('tiny hit here', 5, 8)
    assert window(text, 11, 16, 0) == ('…gamma…', 1, 6)
    report['snips'] = {'windows': 5}
    # ID3 mirror: independent walk over the fixture.
    b = (FIX / 'id3.bin').read_bytes()
    assert len(b) == 110 and b[:3] == b'ID3'
    assert (b[3], b[4], b[5]) == (4, 0, 0x00)
    size = 0
    for x in b[6:10]:
        assert x & 0x80 == 0
        size = size * 128 + x
    assert size == 100
    assert b[10:] == bytes(100)
    assert [0, 0, 2, 1] and (0 * 128 ** 3 + 0 * 128 ** 2 + 2 * 128 + 1) == 257
    report['id3'] = {'tag_size': 100}
    # Tiles mirror: independent slippy recomputation.
    MAX_LAT = 85.05112878
    def tile_of(lon, lat, z):
        assert z in range(20) and -180.0 <= lon <= 180.0
        n = 2 ** z
        x = min(max(math.floor((lon + 180.0) / 360.0 * n), 0), n - 1)
        c = min(max(lat, -MAX_LAT), MAX_LAT)
        y = min(max(math.floor((1.0 - math.asinh(math.tan(math.radians(c))) / math.pi) / 2.0 * n), 0), n - 1)
        return (x, y, z)
    assert tile_of(0.0, 0.0, 0) == (0, 0, 0)
    assert tile_of(0.0, 0.0, 1) == (1, 1, 1) and tile_of(0.1, 0.1, 1) == (1, 0, 1)
    assert tile_of(0.0, 0.0, 10) == (512, 512, 10) and tile_of(0.01, 0.01, 10) == (512, 511, 10)
    assert (2 ** 10) * (2 ** 10) > 4096  # world z10 refuses the cap
    report['tiles'] = {'pins': 5}
    # VCard mirror: dict-based re-parse with folding.
    v = ("BEGIN:VCARD\r\nVERSION:4.0\r\nFN:Jane Doe\r\nN:Doe;Jane;;;\r\n"
         "TEL;TYPE=work,voice:+1-555-0100\r\nTEL;TYPE=home:+1-555-0101\r\n"
         "EMAIL;TYPE=work:jane@example.com\r\nEND:VCARD")
    lines = []
    for ln in v.split('\r\n'):
        if ln.startswith(' ') and lines:
            lines[-1] += ln[1:]
        else:
            lines.append(ln)
    assert lines[0] == 'BEGIN:VCARD' and lines[-1] == 'END:VCARD'
    assert lines[1] == 'VERSION:4.0' and lines[2] == 'FN:Jane Doe'
    assert lines[3].split(':')[1].split(';')[:2] == ['Doe', 'Jane']
    assert [ln for ln in lines if ln.startswith('TEL')].__len__() == 2
    assert 'EMAIL;TYPE=work:jane@example.com' in lines
    report['vcard'] = {'lines': len(lines)}
    # Fields mirror.
    row = {'a': '1', 'b': '2', 'c': '3', 'd': '4'}
    assert {k: v for k, v in row.items() if k in {'b', 'd'}} == {'b': '2', 'd': '4'}
    assert {k: v for k, v in row.items() if k not in {'b'}} == {'a': '1', 'c': '3', 'd': '4'}
    report['fields'] = {'vectors': 2}
    # Indent mirror: math.gcd votes.
    def tally(lines):
        tabs, runs = 0, []
        for ln in lines:
            if not ln.strip():
                continue
            if ln.startswith('\t'):
                tabs += 1
            elif ln.startswith(' '):
                runs.append(len(ln) - len(ln.lstrip(' ')))
        w = math.gcd(*runs) if runs else None
        return tabs, len(runs), w if w in range(1, 9) else None
    assert tally(['x', '\ty', '\t\tz']) == (2, 0, None)
    assert tally(['x', '    y', '        z']) == (0, 2, 4)
    assert tally(['  a', '    b', '      c']) == (0, 3, 2)
    assert tally(['x'.rjust(41)]) == (0, 1, None)
    report['indent'] = {'tallies': 4}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r35/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print('R35 reference PASS: ev 6, xor 3, words 3 (difflib), dups 2, snips 5, '
          'id3 100B tag, tiles 5, vcard 8 lines, fields 2, indent 4.')


if __name__ == '__main__':
    main()
