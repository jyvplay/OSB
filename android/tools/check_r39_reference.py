#!/usr/bin/env python3
"""Independent Python recomputation of the 19 R39 kernel contracts.

Six mirrors are HELD-OUT stdlib implementations sharing zero code with the
Kotlin kernels: datetime (SerialDate, Rfc3339), the punycode codec
(Punycode), base64 (JwtSplit), fnmatch (GlobMatch), int/bigint mod 97
(IbanCheck). The rest are deliberately different hand mirrors.
Host Python only; agreement is cross-language logic evidence, not
Android/device evidence.
"""
import base64
import datetime
import fnmatch
import math
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r39'


def serial_date():
    d0 = datetime.date(1899, 12, 30)
    d31 = datetime.date(1899, 12, 31)
    assert (datetime.date(1900, 1, 1) - d31).days == 1
    assert (datetime.date(1900, 2, 28) - d31).days == 59
    assert (datetime.date(1900, 3, 1) - d0).days == 61
    assert (datetime.date(1970, 1, 1) - d0).days == 25569
    assert (datetime.date(2026, 10, 23) - d0).days == 46318
    assert (datetime.date(9999, 12, 31) - d0).days == 2958465
    assert (datetime.date(2000, 2, 29) - d0).days == 36585
    assert (datetime.date(2026, 10, 23) - datetime.date(1904, 1, 1)).days == 44856
    assert 46318 - 44856 == 1462


def col_letters():
    def to_letters(n):
        s = ''
        while n > 0:
            n, rem = divmod(n - 1, 26)
            s = chr(ord('A') + rem) + s
        return s
    table = {1: 'A', 26: 'Z', 27: 'AA', 52: 'AZ', 53: 'BA', 702: 'ZZ', 703: 'AAA', 728: 'AAZ', 16384: 'XFD'}
    for n, L in table.items():
        assert to_letters(n) == L, (n, L)
    assert to_letters(16385) == 'XFE'  # just beyond the grid, hence the Kotlin refusal


def col_width():
    mdw = 7
    assert int(((256 * 8.43 + (128 // mdw)) / 256) * mdw) == 59
    assert int(((256 * 8.43 + (128 // mdw)) / 256) * mdw) + 5 == 64
    assert math.floor(((8.43 * mdw + 5) / mdw) * 256) / 256 == 9.140625
    assert math.floor((64 - 5) / mdw * 100 + 0.5) / 100 == 8.43
    assert int(((256 * 255.0 + (128 // mdw)) / 256) * mdw) + 5 == 1790
    assert int(((256 * 8.43 + (128 // 8)) / 256) * 8) == 67


def smart_quote():
    def educate(text):
        out = []
        openers = set('([{\u2018\u201C\u2013\u2014')
        for i, c in enumerate(text):
            prev = text[i - 1] if i else None
            nxt = text[i + 1] if i + 1 < len(text) else None
            opens = prev is None or prev.isspace() or prev in openers
            if c == '"':
                out.append('\u201C' if opens else '\u201D')
            elif c == "'":
                if prev and prev.isalpha() and nxt and nxt.isalpha():
                    out.append('\u2019')
                elif nxt and nxt.isdigit() and (prev is None or prev.isspace()):
                    out.append('\u2019')
                elif opens:
                    out.append('\u2018')
                else:
                    out.append('\u2019')
            else:
                out.append(c)
        return ''.join(out)
    assert educate('"Hello," she said.') == '\u201CHello,\u201D she said.'
    assert educate("can't") == 'can\u2019t'
    assert educate("the '90s") == 'the \u201990s'
    assert educate('"He said \'go\' now"') == '\u201CHe said \u2018go\u2019 now\u201D'
    assert educate("'Tis") == '\u2018Tis'


def hyphen_soft():
    SHY = '\u00ad'
    word = f'hy{SHY}phen{SHY}a{SHY}tion'
    stripped = word.replace(SHY, '')
    assert stripped == 'hyphenation'
    cand, seen = [], 0
    for ch in word:
        if ch == SHY:
            if seen > 0 and (not cand or cand[-1] != seen):
                cand.append(seen)
        else:
            seen += 1
    cand = [c for c in cand if c < seen]
    assert cand == [2, 6, 7]
    assert stripped[:6] + '-' == 'hyphen-' and stripped[6:] == 'ation'


def roman_num():
    # positional-table mirror (different mechanism from the greedy Kotlin kernel)
    ones = ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX']
    tens = ['', 'X', 'XX', 'XXX', 'XL', 'L', 'LX', 'LXX', 'LXXX', 'XC']
    hund = ['', 'C', 'CC', 'CCC', 'CD', 'D', 'DC', 'DCC', 'DCCC', 'CM']
    thou = ['', 'M', 'MM', 'MMM']

    def to_roman(n):
        return thou[n // 1000] + hund[n // 100 % 10] + tens[n // 10 % 10] + ones[n % 10]
    assert to_roman(2026) == 'MMXXVI'
    assert to_roman(1994) == 'MCMXCIV'
    assert to_roman(3999) == 'MMMCMXCIX'
    assert to_roman(1666) == 'MDCLXVI'
    assert to_roman(3888) == 'MMMDCCCLXXXVIII'
    spell = {to_roman(n): n for n in range(1, 4000)}
    assert len(spell) == 3999            # canonical spellings are unique
    assert 'IIII' not in spell and 'VX' not in spell and 'IM' not in spell


def crop_box():
    def norm(a, b, c, d):
        return (min(a, c), min(b, d), max(a, c), max(b, d))
    def inter(p, q):
        r = (max(p[0], q[0]), max(p[1], q[1]), min(p[2], q[2]), min(p[3], q[3]))
        assert r[2] > r[0] and r[3] > r[1]
        return r
    letter = (0.0, 0.0, 612.0, 792.0)
    crop = norm(612.0, 792.0, 36.0, 36.0)
    assert crop == (36.0, 36.0, 612.0, 792.0)
    assert inter(crop, letter) == crop
    assert inter(norm(-50.0, -50.0, 700.0, 900.0), letter) == letter
    assert inter(norm(300.0, 400.0, 900.0, 1000.0), letter) == (300.0, 400.0, 612.0, 792.0)


def ico_dir():
    b = (FIX / 'ico.bin').read_bytes()
    reserved, typ, count = struct.unpack_from('<HHH', b, 0)
    assert (reserved, typ, count) == (0, 1, 2)
    e0 = struct.unpack_from('<BBBBHHII', b, 6)
    e1 = struct.unpack_from('<BBBBHHII', b, 22)
    assert e0[0] == 16 and e0[6] == 40 and e0[7] == 38
    assert e1[0] == 0 and e1[6] == 33 and e1[7] == 78
    assert struct.unpack_from('<I', b, e0[7])[0] == 40          # BITMAPINFOHEADER
    assert b[e1[7]:e1[7] + 8] == bytes([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A])
    w, h = struct.unpack_from('>II', b, e1[7] + 16)
    assert (w, h) == (256, 256)                                  # agrees with 0=>256 directory entry


def punycode():
    assert 'bücher'.encode('punycode').decode() == 'bcher-kva'
    assert 'münchen'.encode('punycode').decode() == 'mnchen-3ya'
    assert 'ü'.encode('punycode').decode() == 'tda'
    assert 'MajiでKoiする5秒前'.encode('punycode').decode() == 'MajiKoi5-783gue6qz075azm5e'
    assert b'bcher-kva'.decode('punycode') == 'bücher'
    assert b'bcher-KVA'.decode('punycode') == 'bücher'
    assert 'London'.encode('punycode').decode() == 'London-'
    assert b'London-'.decode('punycode') == 'London'
    assert 'résumé-café'.encode('punycode').decode('ascii') and \
        bytes('résumé-café'.encode('punycode')).decode('punycode') == 'résumé-café'


def usb_desc():
    b = (FIX / 'usbdev.bin').read_bytes()
    f = struct.unpack('<BBHBBBBHHHBBBB', b)
    assert f[0] == 18 and f[1] == 1 and f[2] == 0x0200
    assert f[3] == 0 and f[6] == 64
    assert f[7] == 0x1D6B and f[8] == 0x0104 and f[9] == 0x0121
    assert f[10:14] == (1, 2, 3, 1)
    assert '%04x:%04x' % (f[7], f[8]) == '1d6b:0104'
    bcd = f[9]
    assert f'{(bcd >> 12) & 0xF}{(bcd >> 8) & 0xF}.{(bcd >> 4) & 0xF}{bcd & 0xF}'.lstrip('0') == '1.21'


def midi_note():
    assert 440.0 * 2 ** ((60 - 69) / 12) == 261.6255653005986
    assert abs(440.0 * 2 ** ((127 - 69) / 12) - 12543.853951415975) < 1e-9
    names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
    assert names[69 % 12] + str(69 // 12 - 1) == 'A4'
    assert names[0 % 12] + str(0 // 12 - 1) == 'C-1'
    assert names[127 % 12] + str(127 // 12 - 1) == 'G9'
    cents = (69 + 12 * math.log2(442 / 440) - 69) * 100
    assert 7 < cents < 9


def ulid():
    A = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
    def ts_of(s):
        v = 0
        for c in s[:10]:
            v = v * 32 + A.index(c)
        return v
    assert ts_of('01ARZ3NDEKTSV4RRFFQ69G5FAV') == 1469922850259
    assert datetime.datetime.fromtimestamp(1469922850259 / 1000, datetime.timezone.utc).year == 2016
    assert ts_of('7ZZZZZZZZZZZZZZZZZZZZZZZZZ') == 2 ** 48 - 1
    # spec successor vector: last two chars RZ -> S0 (base32 carry)
    assert A[A.index('R')] == 'R' and A[(A.index('Z') + 1) % 32] == '0' and A[A.index('R') + 1] == 'S'
    for bad in 'ILOU':
        assert bad not in A


def jwt_split():
    tok = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJvc2IiLCJuIjo3fQ.AQIDBA'
    h, p, s = tok.split('.')
    pad = lambda x: x + '=' * (-len(x) % 4)
    import json
    header = json.loads(base64.urlsafe_b64decode(pad(h)))
    assert header == {'alg': 'HS256', 'typ': 'JWT'}
    assert base64.urlsafe_b64decode(pad(p)) == b'{"sub":"osb","n":7}'
    assert base64.urlsafe_b64decode(pad(s)) == bytes([1, 2, 3, 4])
    none_h = json.loads(base64.urlsafe_b64decode(pad('eyJhbGciOiJub25lIn0')))
    assert none_h == {'alg': 'none'}


def iban_check():
    def ok(iban):
        r = iban[4:] + iban[:4]
        return int(''.join(str(int(c, 36)) for c in r)) % 97 == 1
    assert ok('GB82WEST12345698765432')
    assert ok('DE89370400440532013000')
    assert ok('NL91ABNA0417164300')
    assert ok('NO9386011117947')
    assert ok('IT60X0542811101000000123456')
    assert not ok('GB82WEST12345698765433')
    n = int(''.join(str(int(c, 36)) for c in 'WEST12345698765432GB00'))
    assert 98 - n % 97 == 82


def glob_match():
    cases = [
        ('*.txt', 'notes.txt', True), ('*.txt', 'notes.txt.bak', False),
        ('a*b*c', 'aXbYbZc', True), ('?at', 'cat', True), ('?at', 'at', False),
        ('[abc]x', 'bx', True), ('[abc]x', 'dx', False),
        ('[!abc]x', 'dx', True), ('[!abc]x', 'ax', False),
        ('[a-c]x', 'bx', True), ('[a-c]x', 'dx', False),
        ('[]]', ']', True), ('a[', 'a[', True), ('[*]star', '*star', True),
        ('*', '', True), ('', '', True), ('', 'x', False),
        ('[a-]x', '-x', True), ('[a-]x', 'ax', True), ('A*', 'a', False),
        ('r39-*.??r', 'r39-beam.jar', True), ('?', '', False),
        ('[!a-c]x', 'dx', True), ('[!a-c]x', 'bx', False),
        ('a*a*a*b', 'aaaaaaab', True), ('a*a*a*b', 'aaaaaaac', False),
    ]
    for pat, text, want in cases:
        assert fnmatch.fnmatchcase(text, pat) == want, (pat, text, want)


def mac_addr():
    def bits(first_octet):
        return bool(first_octet & 1), bool(first_octet & 2)   # multicast, local
    assert bits(0xDA) == (False, True)
    assert bits(0x00) == (False, False)
    assert bits(0xFF) == (True, True)
    assert bits(0x01) == (True, False)
    assert '0025.96ab.cdef'.replace('.', '') == '002596abcdef'
    assert ':'.join('002596abcdef'[i:i + 2] for i in range(0, 12, 2)) == '00:25:96:ab:cd:ef'


def rfc3339():
    assert int(datetime.datetime.fromisoformat('1985-04-12T23:20:50.52Z').timestamp()) == 482196050
    assert datetime.datetime.fromisoformat('1985-04-12T23:20:50.52Z').microsecond == 520000
    assert int(datetime.datetime.fromisoformat('1996-12-19T16:39:57-08:00').timestamp()) == 851042397
    assert int(datetime.datetime.fromisoformat('2026-10-23T12:00:00+00:00').timestamp()) == 1792756800
    assert int(datetime.datetime.fromisoformat('2026-10-23T17:30:00+05:30').timestamp()) == 1792756800
    assert int(datetime.datetime.fromisoformat('1970-01-01T00:00:00+00:00').timestamp()) == 0
    try:
        datetime.datetime.fromisoformat('2016-12-31T23:59:60+00:00')
        raise AssertionError('fromisoformat accepted a leap second')
    except ValueError:
        pass
    try:
        datetime.datetime.fromisoformat('2026-02-29T00:00:00+00:00')
        raise AssertionError('fromisoformat accepted Feb 29 2026')
    except ValueError:
        pass


def quiet_hours():
    def contains(start, end, m):
        return (m >= start or m < end) if end < start else (start <= m < end)
    night = (22 * 60, 7 * 60)
    assert contains(*night, 23 * 60) and contains(*night, 3 * 60) and not contains(*night, 12 * 60)
    assert contains(*night, 22 * 60) and not contains(*night, 7 * 60)
    assert sum(1 for m in range(1440) if contains(*night, m)) == 9 * 60
    assert sum(1 for m in range(1440) if contains(*night, m) and contains(6 * 60, 8 * 60, m)) == 60
    assert sum(1 for m in range(1440) if contains(*night, m) and contains(23 * 60, 60, m)) == 120


def exposure_ev():
    assert abs(math.log2(16 * 16 / (1 / 125)) - 15) < 0.05
    assert math.log2(1 / 1) == 0
    assert abs(math.log2(2 ** 2 / (1 / 100)) - math.log2((2 * math.sqrt(2)) ** 2 / (1 / 50))) < 1e-9
    assert abs((math.log2(16 * 16 / (1 / 125)) - math.log2(400 / 100)) - 13) < 0.05
    assert abs(16 * 16 / 2 ** 15 - 1 / 128) < 1e-9


CHECKS = [serial_date, col_letters, col_width, smart_quote, hyphen_soft, roman_num,
          crop_box, ico_dir, punycode, usb_desc, midi_note, ulid, jwt_split,
          iban_check, glob_match, mac_addr, rfc3339, quiet_hours, exposure_ev]


def main():
    for fn in CHECKS:
        fn()
    print(f'R39 reference PASS: {len(CHECKS)} independent mirrors agree '
          '(datetime/punycode-codec/base64/fnmatch/bigint-mod97 held out).')
    print('Host Python only; agreement is cross-language logic evidence, not Android/device evidence.')


if __name__ == '__main__':
    main()
