#!/usr/bin/env python3
"""Independent Python mirrors of the 17 R38 kernels.

Host Python only; agreement is cross-language logic evidence, not
Android/device evidence. Where the standard library ships a held-out
implementation (uuid, quopri, colorsys, datetime.isocalendar, base64,
statistics) it is used directly so the mirror shares no code with the
Kotlin kernels.
"""
import base64
import binascii
import colorsys
import datetime
import json
import math
import statistics
import struct
import urllib.parse
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r38'


def magnet():
    q = ('xt=urn:btih:c12fe1c06bba254a9dc9f519b335aa7c1367a88a&dn=my+file%20name'
         '&tr=udp%3A%2F%2Ftracker.example%3A80&tr=http%3A%2F%2Fbackup.example%2Fann&x.pe=10.0.0.1%3A6881')
    pairs = urllib.parse.parse_qsl(q, keep_blank_values=True)
    xt = [v for k, v in pairs if k == 'xt']
    tr = [v for k, v in pairs if k == 'tr']
    dn = [v for k, v in pairs if k == 'dn']
    assert xt == ['urn:btih:c12fe1c06bba254a9dc9f519b335aa7c1367a88a']
    assert dn == ['my file name']
    assert tr == ['udp://tracker.example:80', 'http://backup.example/ann']
    b32 = base64.b32decode('CT76KHBPIIZZ7AMINCYQAAAAAAAAAAAA')
    assert b32.hex() == '14ffe51c2f42339f818868b10000000000000000'
    return {'trackers': len(tr), 'b32hex': b32.hex()[:8]}


def bdecode(data: bytes):
    def value(i, depth):
        assert depth < 32
        c = data[i:i + 1]
        if c == b'i':
            j = data.index(b'e', i)
            text = data[i + 1:j].decode()
            assert text not in ('', '-', '-0')
            digits = text[1:] if text.startswith('-') else text
            assert digits == '0' or not digits.startswith('0')
            return int(text), j + 1
        if c == b'l':
            i += 1
            items = []
            while data[i:i + 1] != b'e':
                v, i = value(i, depth + 1)
                items.append(v)
            return items, i + 1
        if c == b'd':
            i += 1
            entries = {}
            prev = None
            while data[i:i + 1] != b'e':
                assert data[i:i + 1].isdigit()
                k, i = value(i, depth + 1)
                assert prev is None or prev < k  # raw sorted, no dups
                prev = k
                v, i = value(i, depth + 1)
                entries[k] = v
            return entries, i + 1
        assert c.isdigit()
        j = data.index(b':', i)
        lt = data[i:j].decode()
        assert lt == '0' or not lt.startswith('0')
        ln = int(lt)
        assert j + 1 + ln <= len(data)
        return data[j + 1:j + 1 + ln], j + 1 + ln
    v, end = value(0, 0)
    assert end == len(data)
    return v


def bencode():
    assert bdecode(b'i3e') == 3 and bdecode(b'i-3e') == -3
    assert bdecode(b'4:spam') == b'spam'
    assert bdecode(b'l4:spam4:eggse') == [b'spam', b'eggs']
    assert bdecode(b'd3:cow3:moo4:spam4:eggse') == {b'cow': b'moo', b'spam': b'eggs'}
    assert bdecode(b'd8:msg_typei0e5:piecei0ee') == {b'msg_type': 0, b'piece': 0}
    for bad in (b'i-0e', b'i03e', b'01:a', b'i3ei4e', b'd1:b0:1:a0:e', b'd1:a0:1:a0:e'):
        try:
            bdecode(bad)
        except (AssertionError, ValueError):
            continue
        raise AssertionError(f'mirror accepted {bad!r}')
    return {'vectors': 5, 'refusals': 6}


def flac():
    raw = (FIX / 'flac.bin').read_bytes()
    assert raw[:4] == b'fLaC'
    head = raw[4]
    assert head & 0x80 and head & 0x7F == 0
    assert int.from_bytes(raw[5:8], 'big') == 34
    body = raw[8:42]
    mn, mx = struct.unpack('>HH', body[:4])
    assert (mn, mx) == (4096, 4096)
    acc = int.from_bytes(body[10:18], 'big')
    sr = acc >> 44
    ch = ((acc >> 41) & 7) + 1
    bps = ((acc >> 36) & 0x1F) + 1
    total = acc & ((1 << 36) - 1)
    assert (sr, ch, bps, total) == (44100, 2, 16, 88200)
    assert total * 1000 // sr == 2000
    return {'sr': sr, 'duration_ms': total * 1000 // sr}


def webp():
    raw = (FIX / 'webp.bin').read_bytes()
    assert raw[:4] == b'RIFF' and raw[8:12] == b'WEBP'
    assert struct.unpack('<I', raw[4:8])[0] == len(raw) - 8 == 22
    assert raw[12:16] == b'VP8X' and struct.unpack('<I', raw[16:20])[0] == 10
    flags = raw[20]
    w = int.from_bytes(raw[24:27], 'little') + 1
    h = int.from_bytes(raw[27:30], 'little') + 1
    assert flags == 0x30 and (w, h) == (1024, 768)
    assert w * h <= 0xFFFFFFFF
    return {'canvas': [w, h], 'flags': flags}


def semver_key(v):
    core, _, build = v.partition('+')
    core, _, pre = core.partition('-')
    nums = tuple(int(x) for x in core.split('.'))
    if not pre:
        return (nums, 1, ())
    idents = tuple((0, int(p), '') if p.isdigit() else (1, 0, p) for p in pre.split('.'))
    return (nums, 0, idents)


def semver():
    chain = ['1.0.0-alpha', '1.0.0-alpha.1', '1.0.0-alpha.beta', '1.0.0-beta',
             '1.0.0-beta.2', '1.0.0-beta.11', '1.0.0-rc.1', '1.0.0']
    keys = [semver_key(v) for v in chain]
    assert keys == sorted(keys) and len(set(keys)) == len(keys)
    assert semver_key('1.0.0+a') == semver_key('1.0.0+b')
    assert semver_key('1.0.0-1') < semver_key('1.0.0-a')
    return {'chain': len(chain)}


def uuid_mirror():
    u = uuid.UUID('017F22E2-79B0-7CC3-98C4-DC0C0C07398F')
    assert u.version == 7
    assert u.variant == uuid.RFC_4122  # stdlib name for the 10xx OSF variant
    assert u.int >> 80 == 1645557742000
    v1 = uuid.UUID('f81d4fae-7dec-11d0-a765-00a0c91e6bf6')
    assert v1.version == 1
    nil = uuid.UUID(int=0)
    assert nil.variant == uuid.RESERVED_NCS
    mx = uuid.UUID('ffffffff-ffff-ffff-ffff-ffffffffffff')
    assert mx.variant == uuid.RESERVED_FUTURE
    ms = uuid.UUID('12345678-1234-4123-c123-123456789abc')
    assert ms.variant == uuid.RESERVED_MICROSOFT
    return {'v7_ms': 1645557742000}


def httprange():
    def resolve(spec, length):
        unit, _, s = spec.partition('=')
        assert unit == 'bytes' and _ == '='
        out = []
        saw = False
        for part in s.split(','):
            part = part.strip(' \t')
            if not part:
                continue
            saw = True
            if part.startswith('-'):
                n = int(part[1:])
                assert str(n) == part[1:] or part[1:].lstrip('0') == str(n)
                if n == 0 or length == 0:
                    continue
                out.append((max(length - n, 0), length - 1))
            else:
                first, _, last = part.partition('-')
                first = int(first)
                if last:
                    assert int(last) >= first
                if first >= length:
                    continue
                end = length - 1 if not last or int(last) >= length else int(last)
                out.append((first, end))
        assert saw and out
        return out
    assert resolve('bytes=0-499', 1000) == [(0, 499)]
    assert resolve('bytes=500-', 1000) == [(500, 999)]
    assert resolve('bytes=-500', 1000) == [(500, 999)]
    assert resolve('bytes=-1000000', 1000) == [(0, 999)]
    assert resolve('bytes=0-9999', 1000) == [(0, 999)]
    assert resolve('bytes=0-0,-1', 1000) == [(0, 0), (999, 999)]
    assert resolve('bytes=5000-,0-0', 1000) == [(0, 0)]
    for bad, ln in (('bytes=5000-', 1000), ('bytes=-0', 1000), ('bytes=', 1000), ('bytes=-5', 0)):
        try:
            resolve(bad, ln)
        except (AssertionError, ValueError):
            continue
        raise AssertionError(f'mirror accepted {bad!r}')
    return {'vectors': 7, 'refusals': 4}


def qp():
    import quopri
    assert quopri.decodestring(b'ab=3Dcd') == b'ab=cd'
    assert quopri.decodestring(b'foo=\r\nbar') == b'foobar'
    assert quopri.decodestring(b'=41=42') == b'AB'
    # Kotlin encode -> stdlib decode round trip over the mixed vector
    enc = 'caf=E9 =3D\t100%'
    assert quopri.decodestring(enc.encode('latin-1')).decode('latin-1') == 'café =\t100%'
    return {'stdlib': 'quopri'}


def pem():
    body = base64.b64encode(b'hello').decode()
    assert body == 'aGVsbG8='
    assert base64.b64decode(body, validate=True) == b'hello'
    data = b'A' * 80
    b64 = base64.b64encode(data).decode()
    assert len(b64) == 108
    assert base64.b64decode(b64, validate=True) == data
    try:
        base64.b64decode('aGV!bG8=', validate=True)
        raise AssertionError('stdlib accepted non-alphabet base64')
    except binascii.Error:
        pass
    # QUJDRB== is non-canonical (trailing bits set); strict kernels refuse it,
    # stdlib b64decode is lenient here, so the mirror checks the bit math itself.
    bits = (17 << 6) | 1  # 'R','B' -> 010001 000001; low 4 bits after taking one byte
    assert bits & 0xF != 0
    return {'b64_108': len(b64)}


def isoweek():
    vectors = [
        ((2026, 1, 1), (2026, 1, 4)),
        ((2026, 10, 23), (2026, 43, 5)),
        ((2021, 1, 1), (2020, 53, 5)),
        ((2016, 1, 3), (2015, 53, 7)),
        ((2020, 12, 31), (2020, 53, 4)),
        ((1995, 1, 1), (1994, 52, 7)),
        ((2024, 12, 30), (2025, 1, 1)),
    ]
    for (y, m, d), expect in vectors:
        assert tuple(datetime.date(y, m, d).isocalendar()) == expect, (y, m, d)
    assert datetime.date(1970, 1, 1).isoweekday() == 4
    assert datetime.date(2000, 2, 29).isoweekday() == 2
    assert (datetime.date(2000, 3, 1) - datetime.date(1970, 1, 1)).days == 11017
    return {'vectors': len(vectors), 'stdlib': 'datetime.isocalendar'}


def hsl():
    r, g, b = colorsys.hls_to_rgb(210 / 360, 0.4, 0.5)  # note HLS order in stdlib
    assert abs(r - 0.2) < 1e-12 and abs(g - 0.4) < 1e-12 and abs(b - 0.6) < 1e-12
    assert colorsys.hls_to_rgb(0, 0.5, 1.0) == (1.0, 0.0, 0.0)
    r2, g2, b2 = colorsys.hls_to_rgb(120 / 360, 0.5, 1.0)
    assert (round(r2 * 255), round(g2 * 255), round(b2 * 255)) == (0, 255, 0)
    return {'stdlib': 'colorsys', 'pin': [0.2, 0.4, 0.6]}


def edid():
    raw = (FIX / 'edid.bin').read_bytes()
    assert len(raw) == 128
    assert raw[:8] == bytes([0, 255, 255, 255, 255, 255, 255, 0])
    assert sum(raw) % 256 == 0
    mfr = int.from_bytes(raw[8:10], 'big')
    assert mfr & 0x8000 == 0
    letters = ''.join(chr(ord('A') + ((mfr >> s) & 0x1F) - 1) for s in (10, 5, 0))
    assert letters == 'DEL' and raw[8:10] == bytes([0x10, 0xAC])
    assert int.from_bytes(raw[10:12], 'little') == 0xA0C5
    assert int.from_bytes(raw[12:16], 'little') == 0x01020304
    assert raw[16] == 2 and 1990 + raw[17] == 2020
    assert raw[18] == 1 and raw[19] == 4
    ibm = int.from_bytes(bytes([0x24, 0x4D]), 'big')
    assert ''.join(chr(ord('A') + ((ibm >> s) & 0x1F) - 1) for s in (10, 5, 0)) == 'IBM'
    return {'mfr': letters, 'year': 1990 + raw[17]}


def vtt():
    import re
    pat = re.compile(r'^(?:(\d{2,}):)?([0-5]\d):([0-5]\d)\.(\d{3})$')
    def ts(s):
        m = pat.match(s)
        assert m, s
        h = int(m.group(1) or 0)
        return ((h * 60 + int(m.group(2))) * 60 + int(m.group(3))) * 1000 + int(m.group(4))
    assert ts('00:01.000') == 1000
    assert ts('00:00:22.230') == 22230
    assert ts('9999:00:00.000') == 9999 * 3600000
    assert ts('01:02:03.004') == 3723004
    for bad in ('1:00:00.000', '60:00.000', '00:60.000', '00:01.00', '00:01,000'):
        assert pat.match(bad) is None, bad
    return {'vectors': 4, 'refusals': 5}


def luhn_valid(digits):
    total = 0
    for i, ch in enumerate(reversed(digits)):
        d = int(ch)
        if i % 2 == 1:
            d *= 2
            if d > 9:
                d -= 9
        total += d
    return total % 10 == 0


def luhn():
    assert luhn_valid('4111111111111111')
    assert luhn_valid('79927398713')
    assert not luhn_valid('79927398710')
    assert not luhn_valid('4111111111111112')
    assert luhn_valid('4222222222222')
    assert [d for d in range(10) if luhn_valid('7992739871' + str(d))] == [3]
    return {'vectors': 6}


def soundex_mirror(name):
    codes = {**dict.fromkeys('BFPV', 1), **dict.fromkeys('CGJKQSXZ', 2),
             **dict.fromkeys('DT', 3), 'L': 4, 'M': 5, 'N': 5, 'R': 6}
    up = name.upper()
    out = up[0]
    last = codes.get(up[0], 0 if up[0] in 'AEIOUY' else -1)
    for c in up[1:]:
        if c in 'HW':
            continue
        if c in 'AEIOUY':
            last = 0
            continue
        code = codes[c]
        if code != last:
            out += str(code)
        last = code
        if len(out) == 4:
            break
    return (out + '000')[:4]


def soundex():
    vectors = {'Tymczak': 'T522', 'Ashcraft': 'A261', 'Pfister': 'P236', 'Robert': 'R163',
               'Rupert': 'R163', 'Honeyman': 'H555', 'Washington': 'W252', 'Lee': 'L000',
               'Gutierrez': 'G362', 'Jackson': 'J250', 'VanDeusen': 'V532', 'Lloyd': 'L300',
               'Burroughs': 'B620'}
    for name, code in vectors.items():
        assert soundex_mirror(name) == code, (name, soundex_mirror(name))
    return {'vectors': len(vectors)}


def pdfname():
    def decode(token):
        assert token.startswith('/')
        out = []
        body = token[1:]
        i = 0
        while i < len(body):
            if body[i] == '#':
                v = int(body[i + 1:i + 3], 16)
                assert v != 0
                out.append(chr(v))
                i += 3
            else:
                assert body[i] not in '()<>[]{}/%' and body[i] not in '\x00\t\n\x0c\r '
                out.append(body[i])
                i += 1
        return ''.join(out)
    assert decode('/Name1') == 'Name1'
    assert decode('/paired#28#29parentheses') == 'paired()parentheses'
    assert decode('/The_Key_of_F#23_Minor') == 'The_Key_of_F#_Minor'
    assert decode('/A#42') == 'AB'
    assert decode('/') == ''
    for bad in ('/bad#00name', '/a b', '/a(b'):
        try:
            decode(bad)
        except (AssertionError, ValueError):
            continue
        raise AssertionError(f'mirror accepted {bad!r}')
    return {'vectors': 5, 'refusals': 3}


def cadence():
    human = [60.0, 140.0, 80.0, 120.0, 70.0, 130.0, 90.0, 110.0]
    mean = statistics.fmean(human)
    sd = statistics.pstdev(human)
    assert mean == 100.0
    assert abs(sd - math.sqrt(750.0)) < 1e-12
    assert abs(1000.0 / mean - 10.0) < 1e-12
    assert 1000.0 / 18.0 <= mean <= 1000.0 / 6.0 and 25.0 <= sd <= 70.0  # HUMAN_PLAUSIBLE window
    assert statistics.pstdev([100.0] * 10) == 0.0  # MACHINE_LIKE uniformity
    assert 1000.0 / 4.9 > 200.0
    return {'stdlib': 'statistics.pstdev', 'sd': sd}


def main():
    report = {
        'magnet': magnet(), 'bencode': bencode(), 'flac': flac(), 'webp': webp(),
        'semver': semver(), 'uuid': uuid_mirror(), 'httprange': httprange(), 'qp': qp(),
        'pem': pem(), 'isoweek': isoweek(), 'hsl': hsl(), 'edid': edid(), 'vtt': vtt(),
        'luhn': luhn(), 'soundex': soundex(), 'pdfname': pdfname(), 'cadence': cadence(),
    }
    print('R38 reference PASS:', json.dumps(report, sort_keys=True))
    print('Host Python only; agreement is cross-language logic evidence, not Android/device evidence.')


if __name__ == '__main__':
    main()
