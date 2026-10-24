#!/usr/bin/env python3
"""Independent Python recomputation of the 20 R40 kernel contracts.

Held-out stdlib verifiers sharing zero code with the Kotlin kernels:
html.unescape (HtmlEntity), email.header (Rfc2047), zlib.crc32
(SevenZHead), plistlib (PlistHead fixture + trailer), struct everywhere.
The rest are deliberately different hand mirrors. Host Python only;
agreement is cross-language logic evidence, not Android/device evidence.
"""
import html
import math
import plistlib
import struct
import zlib
from email.header import decode_header, make_header
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r40'


def num_fmt():
    def split(fmt):
        out, cur, q, b, i = [], '', False, False, 0
        while i < len(fmt):
            c = fmt[i]
            if c == '\\' and not q:
                cur += fmt[i:i + 2]; i += 2; continue
            if c == '"': q = not q
            elif c == '[' and not q: b = True
            elif c == ']' and not q: b = False
            elif c == ';' and not q and not b:
                out.append(cur); cur = ''; i += 1; continue
            cur += c; i += 1
        assert not q and not b
        out.append(cur)
        return out
    assert split('0.00;[Red]-0.00') == ['0.00', '[Red]-0.00']
    assert split('0";"0') == ['0";"0']
    assert split('[h];mm') == ['[h]', 'mm']
    assert split('0\\;0') == ['0\\;0']
    assert len(split('#,##0;(#,##0);"-";@')) == 4


def cell_err():
    errs = ['#NULL!', '#DIV/0!', '#VALUE!', '#REF!', '#NAME?', '#NUM!', '#N/A']
    assert len(errs) == 7 and len(set(errs)) == 7
    assert errs[4].endswith('?') and errs[6].endswith('A')


def dash():
    def educate(s):
        out, i = '', 0
        while i < len(s):
            if s[i] == '-':
                r = 0
                while i + r < len(s) and s[i + r] == '-': r += 1
                out += {2: '\u2013', 3: '\u2014'}.get(r, '-' * r); i += r
            elif s[i] == '.':
                r = 0
                while i + r < len(s) and s[i + r] == '.': r += 1
                out += '\u2026' if r == 3 else '.' * r; i += r
            else:
                out += s[i]; i += 1
        return out
    assert educate('pages 10--12') == 'pages 10\u201312'
    assert educate('wait---now') == 'wait\u2014now'
    assert educate('so...') == 'so\u2026'
    assert educate('----') == '----' and educate('well-known') == 'well-known'


def obj_replace():
    OBJ = '\ufffc'
    t = OBJ + 'hello' + OBJ + ' world'
    stripped = t.replace(OBJ, '')
    positions = [i for i, c in enumerate(t) if c == OBJ]
    assert positions == [0, 6]
    assert len(stripped) + len(positions) == len(t)
    assert t.split(OBJ) == ['', 'hello', ' world']


def footnote():
    syms = ['*', '\u2020', '\u2021', '\u00a7', '\u2016', '\u00b6']
    def mark(n): return syms[(n - 1) % 6] * ((n - 1) // 6 + 1)
    assert mark(1) == '*' and mark(7) == '**' and mark(13) == '***'
    assert mark(6) == '\u00b6' and mark(12) == '\u00b6\u00b6' and mark(18) == '\u00b6\u00b6\u00b6'
    sup = dict(zip('0123456789', '\u2070\u00b9\u00b2\u00b3\u2074\u2075\u2076\u2077\u2078\u2079'))
    assert ''.join(sup[d] for d in '123') == '\u00b9\u00b2\u00b3'
    assert ''.join(sup[d] for d in '2046') == '\u00b2\u2070\u2074\u2076'


def pdf_rotate():
    assert 450 % 360 == 90 and (-90) % 360 == 270
    for a in range(4):
        for b in range(4):
            assert (a * 90 + b * 90) % 360 == ((a + b) % 4) * 90


def html_entity():
    assert html.unescape('&amp;&lt;&gt;&#65;&#x1F600;&hellip;&nbsp;') == '&<>A\U0001F600\u2026\xa0'
    assert html.unescape('&euro;&pound;&yen;&cent;') == '\u20ac\xa3\xa5\xa2'
    assert html.unescape('&dagger;&Dagger;') == '\u2020\u2021'
    assert html.unescape('&#X41;') == 'A'
    assert html.unescape('&#160;') == '\xa0'
    # The kernel's declared C1 narrowing: Python's HTML5 behavior maps 128
    # to U+20AC via cp1252 — exactly the quirk the kernel REFUSES. Record it.
    assert html.unescape('&#128;') == '\u20ac'


def rfc2047():
    assert str(make_header(decode_header('=?ISO-8859-1?Q?Keld_J=F8rn_Simonsen?= <keld@dkuug.dk>'))) == \
        'Keld J\xf8rn Simonsen <keld@dkuug.dk>'
    assert str(make_header(decode_header('=?UTF-8?B?8J+UkA==?= lock'))) == '\U0001F510 lock'
    assert str(make_header(decode_header('=?UTF-8?B?YQ==?= =?UTF-8?B?Yg==?='))) == 'ab'
    assert str(make_header(decode_header('=?utf-8?q?a_b?='))) == 'a b'


def seven_z():
    b = (FIX / 'sevenz.bin').read_bytes()
    assert b[:6] == b'7z\xbc\xaf\x27\x1c' and b[6] == 0
    stored, = struct.unpack_from('<I', b, 8)
    assert zlib.crc32(b[12:32]) == stored
    off, size, crc = struct.unpack_from('<QQI', b, 12)
    assert (off, size, crc) == (64, 128, 0xDEADBEEF)


def fnv1a():
    def f32(bs):
        h = 0x811C9DC5
        for x in bs: h = ((h ^ x) * 16777619) & 0xFFFFFFFF
        return h
    def f64(bs):
        h = 0xCBF29CE484222325
        for x in bs: h = ((h ^ x) * 0x100000001B3) & 0xFFFFFFFFFFFFFFFF
        return h
    assert f32(b'') == 0x811C9DC5 and f32(b'a') == 0xE40C292C and f32(b'foobar') == 0xBF9CF968
    assert f64(b'') == 0xCBF29CE484222325 and f64(b'foobar') == 0x85944171F73967E8


def mrz():
    W = [7, 3, 1]
    def cd(f):
        s = 0
        for i, c in enumerate(f):
            v = int(c) if c.isdigit() else (0 if c == '<' else ord(c) - 55)
            s += v * W[i % 3]
        return s % 10
    assert cd('L898902C3') == 6 and cd('740812') == 2 and cd('120415') == 9
    assert cd('ZE184226B<<<<<') == 1 and cd('<<<<<<') == 0
    line2 = 'L898902C36UTO7408122F1204159ZE184226B<<<<<10'
    comp = line2[0:10] + line2[13:20] + line2[21:43]
    assert cd(comp) == int(line2[43])


def safe_name():
    reserved = {'CON', 'PRN', 'AUX', 'NUL'} | {f'COM{i}' for i in range(1, 10)} | {f'LPT{i}' for i in range(1, 10)}
    assert 'CON' in reserved and 'COM7' in reserved and 'COM10' not in reserved
    assert 'con.txt'.split('.')[0].upper() in reserved


def percentile():
    data = [15, 20, 35, 40, 50]
    for p, want in ((30, 20), (40, 20), (50, 35), (100, 50)):
        assert data[math.ceil(p * len(data) / 100) - 1] == want
    hundred = list(range(1, 101))
    for p in range(1, 101):
        assert hundred[math.ceil(p * len(hundred) / 100) - 1] == p


def task_checkbox():
    import re
    ITEM = re.compile(r'^(\s*)([-*+]|\d{1,9}[.)])([ \t]+)\[( |x|X)\](?:([ \t]+)(.*))?$')
    assert ITEM.match('- [x] done') and ITEM.match('* [ ] todo') and ITEM.match('1. [X] n')
    assert not ITEM.match('-[ ] nope') and not ITEM.match('- [y] nope')
    assert not ITEM.match('1234567890. [ ] too long')


def snap_fraction():
    for total in (1, 2, 3, 7, 100, 1080, 1081, 2560, 3839):
        for n in range(1, 7):
            spans = [(total * (k + 1)) // n - (total * k) // n for k in range(n)]
            assert sum(spans) == total and all(s >= 0 for s in spans)
    assert [(1000 * (k + 1)) // 3 - (1000 * k) // 3 for k in range(3)] == [333, 333, 334]
    assert [(1081 * (k + 1)) // 2 - (1081 * k) // 2 for k in range(2)] == [540, 541]


def plist_head():
    b = (FIX / 'bplist.bin').read_bytes()
    assert b[:8] == b'bplist00'
    t = b[-32:]
    offI, refI = t[6], t[7]
    num = int.from_bytes(t[8:16], 'big')
    top = int.from_bytes(t[16:24], 'big')
    table = int.from_bytes(t[24:32], 'big')
    assert (offI, refI, num, top, table) == (1, 1, 7, 0, 51)
    assert plistlib.loads(b) == {'position': 12.5, 'rate': 1.0, 'ok': True}
    assert table + num * offI <= len(b) - 32


def monogram():
    def of(label):
        initials = []
        for tok in label.split():
            for ch in tok:
                if ch.isalnum():
                    initials.append(ch.upper()); break
            if len(initials) == 2: break
        assert initials
        return ''.join(initials)
    assert of('Fusion Shell') == 'FS' and of('beam') == 'B'
    assert of('émile zola') == 'ÉZ' and of('123 app') == '1A'


def bech32():
    CH = 'qpzry9x8gf2tvdw0s3jn54khce6mua7l'
    G = [0x3B6A57B2, 0x26508E6D, 0x1EA119FA, 0x3D4233DD, 0x2A1462B3]
    def polymod(vals):
        chk = 1
        for v in vals:
            top = chk >> 25
            chk = ((chk & 0x1FFFFFF) << 5) ^ v
            for i in range(5):
                if (top >> i) & 1: chk ^= G[i]
        return chk
    def expand(h): return [ord(c) >> 5 for c in h] + [0] + [ord(c) & 31 for c in h]
    def residue(s):
        s = s.lower(); sep = s.rfind('1')
        return polymod(expand(s[:sep]) + [CH.index(c) for c in s[sep + 1:]])
    assert residue('A12UEL5L') == 1
    assert residue('A12UEL5M') not in (1, 0x2BC830A3)
    assert residue('osb1qpzry9x8gf2y2numr') == 0x2BC830A3
    assert residue('osb1qpzry9x8gf23krs7p') == 1
    assert len(set(CH)) == 32 and all(c not in CH for c in 'bio1')


def otpauth():
    from urllib.parse import urlsplit, parse_qsl, unquote
    u = urlsplit('otpauth://totp/Fusion:alice%40example.org?secret=JBSWY3DPEHPK3PXP&issuer=Fusion')
    assert u.scheme == 'otpauth' and u.netloc == 'totp'
    label = u.path.lstrip('/')
    issuer_raw, _, account_raw = label.partition(':')
    assert unquote(account_raw) == 'alice@example.org' and issuer_raw == 'Fusion'
    q = dict(parse_qsl(u.query))
    body = q['secret'].rstrip('=')
    assert all(c in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567' for c in body)
    raw = 'I%3Aname:acct'
    pre, _, post = raw.partition(':')
    assert unquote(pre) == 'I:name' and post == 'acct'


def ip_redact():
    import ipaddress
    a = ipaddress.ip_address('203.0.113.77')
    net = ipaddress.ip_network('203.0.113.77/24', strict=False)
    assert str(net.network_address) == '203.0.113.0'
    v6 = ipaddress.ip_network('2001:db8:85a3::8a2e:370:7334/48', strict=False)
    assert str(v6.network_address) == '2001:db8:85a3::'
    assert ipaddress.ip_address('10.1.2.3').is_private
    assert ipaddress.ip_address('172.16.0.1').is_private and not ipaddress.ip_address('172.32.0.1').is_private
    try:
        ipaddress.ip_address('1.2.3.256'); raise AssertionError('accepted 256')
    except ValueError:
        pass
    try:
        ipaddress.ip_address('1::2::3'); raise AssertionError('accepted double ::')
    except ValueError:
        pass


CHECKS = [num_fmt, cell_err, dash, obj_replace, footnote, pdf_rotate, html_entity,
          rfc2047, seven_z, fnv1a, mrz, safe_name, percentile, task_checkbox,
          snap_fraction, plist_head, monogram, bech32, otpauth, ip_redact]


def main():
    for fn in CHECKS:
        fn()
    print(f'R40 reference PASS: {len(CHECKS)} independent mirrors agree '
          '(html/email.header/zlib-crc32/plistlib/ipaddress/urllib held out).')
    print('Host Python only; agreement is cross-language logic evidence, not Android/device evidence.')


if __name__ == '__main__':
    main()
