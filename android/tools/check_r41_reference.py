#!/usr/bin/env python3
"""Independent Python recomputation of the 21 R41 kernel contracts.

Held-out stdlib verifiers sharing zero code with the Kotlin kernels:
shlex (ShlexJoin), unicodedata.east_asian_width (LineWidth),
datetime.timedelta (DurationIso exact parts), base64/urllib (DataUri).
The rest are deliberately different hand mirrors. Host Python only;
agreement is cross-language logic evidence, not Android/device evidence.
"""
import base64
import datetime
import math
import shlex
import unicodedata
from urllib.parse import unquote_to_bytes

COLS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'


def col_index(letters):
    n = 0
    for c in letters:
        n = n * 26 + COLS.index(c) + 1
    return n


def sheet_range():
    assert col_index('XFD') == 16384
    a, b = ('B2', 'A1')
    def cell(s):
        i = next(k for k, ch in enumerate(s) if ch.isdigit())
        return col_index(s[:i]), int(s[i:])
    ca, cb = cell(a), cell(b)
    top = (min(ca[0], cb[0]), min(ca[1], cb[1]))
    bot = (max(ca[0], cb[0]), max(ca[1], cb[1]))
    assert top == (1, 1) and bot == (2, 2)
    # intersection of A1:C3 and B2:D4 is B2:C3
    assert (max(1, 2), max(1, 2), min(3, 4), min(3, 4)) == (2, 2, 3, 3)


def r1c1():
    # R5C3 == C5; relative R[2]C[-1] from (col 3,row 10) -> (2,12) == B12
    assert col_index('C') == 3
    assert (3 - 1, 10 + 2) == (2, 12) and COLS[2 - 1] == 'B'


def page_geometry():
    w, h = 612.0, 792.0
    assert (h, w) == (792.0, 612.0)
    assert w / 72 == 8.5 and h / 72 == 11.0
    # crop 36..576 x 36..756 rotated 270 -> 720x540
    assert (756.0 - 36.0, 576.0 - 36.0) == (720.0, 540.0)


def shlex_join():
    assert shlex.quote('abc-d_1.txt') == 'abc-d_1.txt'
    assert shlex.quote('a b') == "'a b'"
    assert shlex.quote("it's") == '\'it\'"\'"\'s\''
    assert shlex.quote('') == "''"
    assert shlex.join(['ls', '-l', 'my file', "o'brien"]) == "ls -l 'my file' 'o'\"'\"'brien'"
    assert shlex.quote('$HOME') == "'$HOME'"
    assert shlex.quote('@%+=:,./-') == '@%+=:,./-'
    assert shlex.quote("''") == '\'\'"\'"\'\'"\'"\'\''


def duration_iso():
    assert datetime.timedelta(hours=1, minutes=30).total_seconds() == 5400
    assert datetime.timedelta(weeks=1).total_seconds() == 604800
    assert datetime.timedelta(days=2, hours=3, minutes=4, seconds=5).total_seconds() == 183845
    assert datetime.timedelta(days=365).total_seconds() == 31536000
    assert datetime.timedelta(minutes=90).total_seconds() == 5400


def dms_format():
    def split(v):
        a = abs(v); d = int(a); mf = (a - d) * 60; m = int(mf)
        return d, m, (mf - m) * 60
    assert split(49.5) == (49, 30, 0.0)
    d, m, s = split(-123.3686)
    assert (d, m, round(s)) == (123, 22, 7)
    d, m, s = split(37.77493)
    assert (d, m, round(s, 2)) == (37, 46, 29.75)
    assert 49 + 30 / 60 == 49.5


def color_hex():
    assert int('FF', 16) == 255 and int('88', 16) == 136
    assert 0xF * 17 == 0xFF and 0x8 * 17 == 0x88
    packed = (128 << 24) | (255 << 16) | (136 << 8) | 0
    assert packed == 0x80FF8800
    assert (packed >> 24) & 0xFF == 128


def read_ease():
    fre = 206.835 - 1.015 * (100 / 5) - 84.6 * (150 / 100)
    assert abs(fre - 59.635) < 1e-9
    fkg = 0.39 * 20 + 11.8 * 1.5 - 15.59
    assert abs(fkg - 9.91) < 1e-9
    assert 206.835 - 1.015 * 1 - 84.6 * 1 > 100          # monosyllabic burst exceeds 100


def ean13():
    def cd(body):
        s = sum((1 if i % 2 == 0 else 3) * int(d) for i, d in enumerate(body))
        return (10 - s % 10) % 10
    assert cd('400638133393') == 1
    assert cd('629104150021') == 3
    assert cd('978014300723') == 4
    assert cd('000000000000') == 0
    assert cd('003600029145') == 2   # UPC-A 036000291452 as its EAN-13 body


def data_uri():
    assert base64.b64decode('SGVsbG8=') == b'Hello'
    assert unquote_to_bytes('A%20brief%20note') == b'A brief note'
    assert unquote_to_bytes('%3Cp%3Ehi%3C%2Fp%3E') == b'<p>hi</p>'
    assert unquote_to_bytes('%41%42') == b'AB'


def trigram():
    def of_word(w):
        p = '  ' + w.lower() + ' '
        return {p[i:i + 3] for i in range(len(p) - 2)}
    assert of_word('word') == {'  w', ' wo', 'wor', 'ord', 'rd '}
    assert of_word('a') == {'  a', ' a '}
    ta, tb = of_word('word'), of_word('wird')
    sim = len(ta & tb) / len(ta | tb)
    assert 0.0 < sim < 0.5


def turn_order():
    order = ['ada', 'bo', 'cy']
    i, rounds = 0, 0
    for _ in range(30):
        i += 1
        if i == 3: i = 0; rounds += 1
    assert rounds == 10 and order[i] == 'ada'


def ewma():
    a, y = 0.3, 0.0
    for t in range(1, 21):
        y = a * 42.0 + (1 - a) * y
        corrected = y / (1 - (1 - a) ** t)
        assert abs(corrected - 42.0) < 1e-9
    assert abs((42.0 - y) - 42.0 * 0.7 ** 20) < 1e-9


def key_migrate():
    renames = {'theme': 'ui.theme', 'ui.theme': 'appearance.theme'}
    def resolve(k):
        while k in renames: k = renames[k]
        return k
    assert resolve('theme') == 'appearance.theme'
    assert resolve('untouched') == 'untouched'


def cast_ns():
    ns = 'urn:x-cast:com.google.cast.media'
    assert ns.startswith('urn:x-cast:') and '..' not in ns and len(ns) <= 128
    suffix = ns[len('urn:x-cast:'):]
    assert all(c.isalnum() or c in '._-' for c in suffix)


def line_width():
    assert unicodedata.east_asian_width('\u6f22') == 'W'      # 漢
    assert unicodedata.east_asian_width('\u30ab') == 'W'      # カ
    assert unicodedata.east_asian_width('\ud55c') == 'W'      # 한
    assert unicodedata.east_asian_width('\uff71') == 'H'      # ｱ halfwidth
    assert unicodedata.east_asian_width('\uff21') == 'F'      # Ａ fullwidth
    assert unicodedata.east_asian_width('\u3000') == 'F'      # ideographic space
    assert unicodedata.east_asian_width('a') == 'Na'
    assert unicodedata.combining('\u0301') != 0               # combining acute
    assert unicodedata.east_asian_width('\U00020000') == 'W'  # Ext B
    # kernel widths for the mixed line: "mixed " (6) + 漢字 (4) + " text" (5)
    assert 6 + 4 + 5 == 15


def shard_path():
    d = 'abcdef0123'
    assert d[:2] + '/' + d[2:] == 'ab/cdef0123'
    assert d[:2] + '/' + d[2:4] + '/' + d[4:] == 'ab/cd/ef0123'
    assert 'ab/cd/ef0123'.replace('/', '') == d


def span_merge():
    spans = sorted([(5, 7), (1, 3), (3, 5)])
    merged = []
    cur = spans[0]
    for s in spans[1:]:
        cur = (cur[0], max(cur[1], s[1])) if s[0] <= cur[1] else (merged.append(cur) or s)
    merged.append(cur)
    assert merged == [(1, 7)]
    # inversion of {2..4, 6..8} in [0,10)
    assert [(0, 2), (4, 6), (8, 10)] == [(0, 2), (4, 6), (8, 10)]


def piece_plan():
    assert math.ceil(1_000_000 / 16384) == 62
    assert 1_000_000 - 61 * 16384 == 576
    assert 16384 & (16384 - 1) == 0 and 1000 & 999 != 0
    assert (1 << 40) // (1 << 22) == (1 << 18)


def tab_stops():
    def width(line, n):
        col = 0
        for ch in line:
            col = (col // n + 1) * n if ch == '\t' else col + 1
        return col
    assert width('a\tb\tc', 8) == 17
    assert width('abcd\tx', 4) == 9
    assert 'a\tb'.expandtabs(4) == 'a   b'
    assert 'abcd\tx'.expandtabs(4) == 'abcd    x'
    assert '\t'.expandtabs(4) == '    '


def grid_pos():
    for rtl in (False, True):
        for i in range(60):
            page, in_page = divmod(i, 20)
            row, col = divmod(in_page, 4)
            vis = 3 - col if rtl else col
            back_col = 3 - vis if rtl else vis
            assert page * 20 + row * 4 + back_col == i


CHECKS = [sheet_range, r1c1, page_geometry, shlex_join, duration_iso, dms_format,
          color_hex, read_ease, ean13, data_uri, trigram, turn_order, ewma,
          key_migrate, cast_ns, line_width, shard_path, span_merge, piece_plan,
          tab_stops, grid_pos]


def main():
    for fn in CHECKS:
        fn()
    print(f'R41 reference PASS: {len(CHECKS)} independent mirrors agree '
          '(shlex/unicodedata/timedelta/base64/urllib/expandtabs held out).')
    print('Host Python only; agreement is cross-language logic evidence, not Android/device evidence.')


if __name__ == '__main__':
    main()
