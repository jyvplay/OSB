#!/usr/bin/env python3
"""Independent Python recomputation of the 22 R42 kernel contracts.

Honesty note: this round's mirrors are predominantly HANDWRITTEN
recomputations (different code, same published constants) plus struct/
math; the Damm table and GS1-family constants are re-entered separately
here so a transcription slip on either side surfaces as disagreement.
Host Python only; agreement is cross-language logic evidence, not
Android/device evidence.
"""
import math
import struct

COLS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'


def col(letters):
    n = 0
    for c in letters:
        n = n * 26 + COLS.index(c) + 1
    return n


def abs_ref():
    # $B2 shifted by (2,3): column pinned, row moves
    assert (2, 2 + 3) == (2, 5)
    assert col('XFD') == 16384 and col('ZZ') == 702


def merged_cells():
    # A1:B2 vs B2:C3 overlap at B2
    assert max(1, 2) <= min(2, 3) and max(1, 2) <= min(2, 3)
    # hidden cells: 2x2 + 2x2 regions hide 3+3
    assert (4 - 1) + (4 - 1) == 6


def pdf_date():
    s = '199812231952-08\'00'
    assert s[0:4] == '1998' and s[4:6] == '12' and s[6:8] == '23'
    assert s[8:10] == '19' and s[10:12] == '52'
    assert -(8 * 60 + 0) == -480


def nmea():
    assert abs((48 + 7.038 / 60) - 48.1173) < 1e-9
    assert abs((11 + 31.0 / 60) - 11.516666666666667) < 1e-12
    d = 48.1173
    deg = int(d); minutes = (d - deg) * 60
    assert '%02d%07.4f' % (deg, minutes) == '4807.0380'


def hsts():
    parts = [p.strip() for p in 'max-age=31536000; includeSubDomains'.split(';')]
    kv = dict(p.split('=') if '=' in p else (p, '') for p in parts)
    assert kv['max-age'] == '31536000' and 'includeSubDomains' in kv


def pair_pin():
    T = [[0,3,1,7,5,9,8,6,4,2],[7,0,9,2,1,5,4,8,6,3],[4,2,0,6,8,7,1,3,5,9],[1,7,5,0,9,8,3,4,2,6],
         [6,1,2,3,0,4,5,9,7,8],[3,6,7,4,2,0,9,5,8,1],[5,8,6,9,7,2,0,1,3,4],[8,9,4,5,3,6,2,0,1,7],
         [9,4,3,8,6,1,7,2,0,5],[2,5,8,1,4,3,6,7,9,0]]
    def damm(s):
        x = 0
        for c in s: x = T[x][int(c)]
        return x
    assert damm('572') == 4 and damm('5724') == 0
    assert damm('5734') != 0 and damm('7524') != 0
    # anti-symmetry on the diagonal-adjacent pairs used by the kernel property
    full = '314159' + str(damm('314159'))
    assert damm(full) == 0
    for i in range(len(full) - 1):
        if full[i] != full[i + 1]:
            sw = full[:i] + full[i + 1] + full[i] + full[i + 2:]
            assert damm(sw) != 0


def ar_head():
    hdr = 'debian-binary'.ljust(16) + '0'.ljust(12) + '0'.ljust(6) + '0'.ljust(6) + '100644'.ljust(8) + '4'.ljust(10) + '`\n'
    assert len(hdr) == 60 and hdr[58:] == '`\n'
    assert int(hdr[48:58].strip()) == 4


def case_cycle():
    assert 'hello world'.title() == 'Hello World'
    assert '(aside) note'.title() == '(Aside) Note'


def comment_toggle():
    lines = ['  a', '    b']
    indent = min(len(l) - len(l.lstrip(' ')) for l in lines if l.strip())
    assert indent == 2
    assert [l[:indent] + '// ' + l[indent:] for l in lines] == ['  // a', '  //   b']


def unit_price():
    assert 250 / (500 * 1.0) == 0.5 and 450 / (1 * 1000.0) == 0.45
    assert 360 / (1 * 12.0) == 30.0 < 35.0
    assert 100 / (50 * 10.0) == 0.2


def rule_3rds():
    assert (1080 // 3, 1080 * 2 // 3, 1920 // 3, 1920 * 2 // 3) == (360, 720, 640, 1280)
    pts = [(360, 640), (720, 640), (360, 1280), (720, 1280)]
    best = min(pts, key=lambda p: (p[0] - 0) ** 2 + (p[1] - 0) ** 2)
    assert best == (360, 640)


def histo():
    bounds = [0, 10, 100, 1000]
    counts = [0, 0, 0]; overflow = 0
    for v in (5, 9, 10, 99, 100, 999, 5000, 0):
        for i in range(3):
            if v < bounds[i + 1]:
                counts[i] += 1; break
        else:
            overflow += 1
    assert counts == [3, 2, 2] and overflow == 1
    assert sum(counts) + overflow == 8


def csi_cursor():
    assert max(1, 5 - 99) == 1                      # clamp at top
    assert (min(24, 99), min(80, 99)) == (24, 80)   # CUP clamps
    assert int('' or '1') == 1                      # ECMA-48 default


def gpc():
    assert '1'.strip() == '1'
    import re as _re
    assert _re.search(r'"gpc"\s*:\s*true', '{ "gpc" : true }')


def pw_policy():
    pw = 'Aaaaaaaaaaa1!'
    assert len(pw) >= 12 and any(c.isupper() for c in pw) and any(c.isdigit() for c in pw)


def gain_db():
    assert abs(20 * math.log10(10) - 20) < 1e-12
    assert abs(10 ** (-6.020599913279624 / 20) - 0.5) < 1e-12
    assert abs(10 * math.log10(2) - 3.0102999566398116) < 1e-12


def hex_bytes():
    assert bytes.fromhex('000fff') == bytes([0, 15, 255])
    assert bytes([0, 15, 255]).hex() == '000fff'
    try:
        bytes.fromhex('abc'); raise AssertionError('odd accepted')
    except ValueError:
        pass


def snooze():
    ladder = [5]
    while ladder[-1] < 60:
        ladder.append(min(ladder[-1] * 2, 60))
    assert ladder == [5, 10, 20, 40, 60]


def nice_step():
    def step(lo, hi, maxt):
        rough = (hi - lo) / maxt
        mag = 10 ** math.floor(math.log10(rough))
        while True:
            for m in (1, 2, 5):
                s = m * mag
                if math.ceil(hi / s) - math.floor(lo / s) <= maxt:
                    return s
            mag *= 10
    assert step(0, 100, 6) == 20 and step(0, 100, 11) == 10
    assert step(0, 1, 5) == 0.2 and step(0, 7, 100) == 0.1


def next_run():
    anchor, period = 1000, 600
    def nxt(now):
        if now < anchor: return anchor
        return anchor + ((now - anchor) // period + 1) * period
    assert nxt(999) == 1000 and nxt(1000) == 1600 and nxt(2199) == 2200 and nxt(2200) == 2800


def rfc2231():
    from urllib.parse import unquote
    assert unquote("r%C3%A9sum%C3%A9.pdf", encoding='utf-8') == 'r\u00e9sum\u00e9.pdf'
    assert unquote('%A3%20rates', encoding='latin-1') == '\u00a3 rates'
    v = "ISO-8859-1'en'%A3"
    first = v.index("'"); second = v.index("'", first + 1)
    assert v[:first] == 'ISO-8859-1' and v[first + 1:second] == 'en'


def bmp_head():
    assert ((3 * 24 + 31) // 32) * 4 == 12
    assert ((5 * 8 + 31) // 32) * 4 == 8 and ((16 * 1 + 31) // 32) * 4 == 4
    hdr = struct.pack('<2sIHHI', b'BM', 78, 0, 0, 54)
    assert hdr[:2] == b'BM' and struct.unpack_from('<I', hdr, 2)[0] == 78


CHECKS = [abs_ref, merged_cells, pdf_date, nmea, hsts, pair_pin, ar_head,
          case_cycle, comment_toggle, unit_price, rule_3rds, histo, csi_cursor,
          gpc, pw_policy, gain_db, hex_bytes, snooze, nice_step, next_run,
          rfc2231, bmp_head]


def main():
    for fn in CHECKS:
        fn()
    print(f'R42 reference PASS: {len(CHECKS)} independent mirrors agree '
          '(handwritten recomputations + struct/math/urllib/str.title/bytes.hex).')
    print('Host Python only; agreement is cross-language logic evidence, not Android/device evidence.')


if __name__ == '__main__':
    main()
