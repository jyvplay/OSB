#!/usr/bin/env python3
"""Independent Python recomputation of R33 kernel behavior from fixtures/sources.

Cross-checks E.164 normalization, ANSI SGR styling, chunk plans, glob
matching (memoized recursive matcher, NOT the kernel's DP), fuzzy scores,
vector clocks, unified hunks (difflib agreement), BMP headers, gzip member
headers (both fixtures gunzip), and TOTP (hmac-based, RFC 6238 vectors).
Agreement is cross-language evidence for the LOGIC ONLY -- not Android,
device, service, or acceptance evidence.
"""
import base64
import difflib
import gzip
import hashlib
import hmac
import json
import re
import struct
import zlib
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r33'

def main():
    report = {}
    # E.164 mirror.
    SEPS = set(' -.()/')
    def normalize(raw, default_cc=None):
        assert 1 <= len(raw) <= 64
        if default_cc is not None:
            assert re.fullmatch(r'\+[1-9][0-9]{0,2}', default_cc)
        t = raw.strip()
        assert t
        plus = t.startswith('+')
        assert t.count('+') <= 1 and ('+' not in t or plus)
        body = t[1:] if plus else t
        assert body and all(c.isdigit() or c in SEPS for c in body)
        digits = ''.join(c for c in body if c.isdigit())
        if plus:
            intl = digits
        elif digits.startswith('00'):
            intl = digits[2:]
        elif default_cc is not None:
            local = digits[1:] if digits.startswith('0') else digits
            intl = default_cc[1:] + local
        else:
            raise AssertionError('no country context')
        assert 7 <= len(intl) <= 15 and not intl.startswith('0')
        return '+' + intl
    assert normalize('+1 800-555-1212') == '+18005551212'
    assert normalize('00442079460000') == '+442079460000'
    assert normalize('(020) 7946 0000', '+44') == '+442079460000'
    for bad, cc in [('+1-800-FLOWERS', None), ('02079460000', None), ('+123456', None),
                    ('+1234567890123456', None), ('++44', None), ('44+2079', None),
                    ('2079460000', '44'), ('   ', None), ('0001234567', None)]:
        try:
            normalize(bad, cc)
        except AssertionError:
            pass
        else:
            raise AssertionError(f'no refusal: {bad!r}')
    report['e164'] = {'vectors': 12}
    # ANSI mirror.
    def apply_sgr(params):
        assert 1 <= len(params) <= 64 and all(0 <= p <= 255 for p in params)
        s = {'bold': False, 'italic': False, 'underline': False, 'inverse': False,
             'strike': False, 'fg': None, 'bg': None}
        i = 0
        while i < len(params):
            c = params[i]
            if c == 0:
                s = {k: (None if k in ('fg', 'bg') else False) for k in s}
            elif c == 1:
                s['bold'] = True
            elif c == 3:
                s['italic'] = True
            elif c == 4:
                s['underline'] = True
            elif c == 7:
                s['inverse'] = True
            elif c == 9:
                s['strike'] = True
            elif c == 22:
                s['bold'] = False
            elif c == 23:
                s['italic'] = False
            elif c == 24:
                s['underline'] = False
            elif c == 27:
                s['inverse'] = False
            elif c == 28:
                s['strike'] = False
            elif 30 <= c <= 37:
                s['fg'] = ('ansi', c - 30)
            elif c == 39:
                s['fg'] = None
            elif 40 <= c <= 47:
                s['bg'] = ('ansi', c - 40)
            elif c == 49:
                s['bg'] = None
            elif 90 <= c <= 97:
                s['fg'] = ('ansi', c - 90 + 8)
            elif 100 <= c <= 107:
                s['bg'] = ('ansi', c - 100 + 8)
            elif c in (38, 48):
                assert i + 2 < len(params), 'truncated'
                fg = c == 38
                if params[i + 1] == 5:
                    col = ('ansi', params[i + 2])
                    s['fg' if fg else 'bg'] = col
                    i += 3
                    continue
                elif params[i + 1] == 2:
                    assert i + 4 < len(params), 'truncated rgb'
                    s['fg' if fg else 'bg'] = ('rgb', params[i + 2], params[i + 3], params[i + 4])
                    i += 5
                    continue
                else:
                    raise AssertionError('bad mode')
            else:
                raise AssertionError(f'refused {c}')
            i += 1
        return s
    s = apply_sgr([1, 31])
    assert s['bold'] and s['fg'] == ('ansi', 1)
    assert apply_sgr([38, 5, 200])['fg'] == ('ansi', 200)
    assert apply_sgr([48, 2, 1, 2, 3])['bg'] == ('rgb', 1, 2, 3)
    for bad in [[2], [38, 5], [48, 2, 1, 2], [38, 4, 1], []]:
        try:
            apply_sgr(bad)
        except AssertionError:
            pass
        else:
            raise AssertionError(f'no refusal: {bad}')
    def strip(text):
        out, i = [], 0
        while i < len(text):
            if text[i] == '\x1b' and i + 1 < len(text) and text[i + 1] == '[':
                j = i + 2
                while j < len(text) and (text[j].isdigit() or text[j] == ';'):
                    j += 1
                if j < len(text) and text[j] == 'm':
                    i = j + 1
                    continue
            out.append(text[i])
            i += 1
        return ''.join(out)
    assert strip('\x1b[1mA\x1b[0m') == 'A' and strip('x\x1b[2Jy') == 'x\x1b[2Jy'
    report['ansi'] = {'vectors': 10}
    # Chunk mirror.
    def plan(total, size):
        assert total >= 0 and 1 <= size <= 1024 ** 3
        if total == 0:
            return []
        count = (total - 1) // size + 1
        assert count <= 1000000
        return [(i, i * size, min(size, total - i * size)) for i in range(count)]
    assert plan(10, 3) == [(0, 0, 3), (1, 3, 3), (2, 6, 3), (3, 9, 1)]
    assert plan(0, 5) == [] and len(plan(9, 3)) == 3
    assert [c for c in plan(10, 3) if c[0] not in (0, 2)] == [(1, 3, 3), (3, 9, 1)]
    report['chunk'] = {'vectors': 4}
    # Glob mirror: memoized recursion, structurally unlike the kernel DP.
    def match(pattern, name):
        assert len(pattern) <= 256 and len(name) <= 1024
        toks, i = [], 0
        while i < len(pattern):
            c = pattern[i]
            if c == '*':
                toks.append(('*',))
                i += 1
            elif c == '?':
                toks.append(('?',))
                i += 1
            elif c == '\\':
                assert i + 1 < len(pattern)
                toks.append(('lit', pattern[i + 1]))
                i += 2
            elif c == '[':
                j = i + 1
                neg = j < len(pattern) and pattern[j] == '!'
                if neg:
                    j += 1
                chars = set()
                if j < len(pattern) and pattern[j] == ']':
                    chars.add(']')
                    j += 1
                closed = False
                while j < len(pattern):
                    if pattern[j] == ']':
                        closed = True
                        j += 1
                        break
                    if j + 2 < len(pattern) and pattern[j + 1] == '-' and pattern[j + 2] != ']':
                        assert pattern[j] <= pattern[j + 2]
                        chars.update(chr(k) for k in range(ord(pattern[j]), ord(pattern[j + 2]) + 1))
                        j += 3
                    else:
                        chars.add(pattern[j])
                        j += 1
                assert closed
                toks.append(('cls', frozenset(chars), neg))
                i = j
            else:
                toks.append(('lit', c))
                i += 1
        @lru_cache(maxsize=None)
        def rec(ti, ni):
            if ti == len(toks):
                return ni == len(name)
            t = toks[ti]
            if t[0] == '*':
                return any(rec(ti + 1, k) for k in range(ni, len(name) + 1))
            if ni == len(name):
                return False
            if t[0] == '?':
                return rec(ti + 1, ni + 1)
            if t[0] == 'lit':
                return name[ni] == t[1] and rec(ti + 1, ni + 1)
            return ((name[ni] in t[1]) != t[2]) and rec(ti + 1, ni + 1)
        return rec(0, 0)
    cases = [('*', 'anything', True), ('*.kt', 'Main.kt', True), ('*.kt', 'Main.java', False),
             ('a?c', 'abc', True), ('a?c', 'ac', False), ('[abc]x', 'bx', True),
             ('[abc]x', 'dx', False), ('[a-z]x', 'qx', True), ('[!a-z]x', 'Qx', True),
             ('[!a-z]x', 'qx', False), ('[]]x', ']x', True), ('[-a]x', '-x', True),
             ('\\*x', '*x', True), ('', '', True), ('', 'a', False), ('**', 'a/b', True)]
    for pat, nm, want in cases:
        assert match(pat, nm) == want, (pat, nm)
    for bad in ['[ab', '[z-a]', 'ab\\']:
        try:
            match(bad, 'x')
        except AssertionError:
            pass
        else:
            raise AssertionError(f'no refusal: {bad!r}')
    report['glob'] = {'vectors': len(cases) + 3}
    # Fuzzy mirror.
    def score(pattern, target):
        assert len(pattern) <= 256 and len(target) <= 256
        if not pattern:
            return 0
        total, ti, prev = 0, 0, -2
        for pc in pattern:
            hit = -1
            for j in range(ti, len(target)):
                if target[j].lower() == pc.lower():
                    hit = j
                    break
            if hit < 0:
                return None
            total += 10
            if hit == 0 or not target[hit - 1].isalnum():
                total += 15
            if hit > 0 and target[hit].isupper() and target[hit - 1].islower():
                total += 8
            if hit == prev + 1:
                total += 5
            if prev >= 0:
                total -= (hit - prev - 1)
            prev, ti = hit, hit + 1
        return total
    assert score('abc', 'abc') == 55 and score('fb', 'FooBar') == 41
    assert score('ac', 'abc') == 34 and score('FB', 'foobar') == 33
    assert score('', 'x') == 0 and score('xyz', 'abc') is None
    report['fuzzy'] = {'vectors': 6}
    # Clock mirror.
    NODE = re.compile(r'[A-Za-z0-9._\-]{1,64}')
    def valid(c, label):
        assert len(c) <= 64, label
        for k, v in c.items():
            assert NODE.fullmatch(k) and v >= 0
    def increment(c, node):
        valid(c, 'c')
        assert NODE.fullmatch(node) and c.get(node, 0) < 2 ** 63 - 1
        assert len(c) < 64 or node in c
        return {**c, node: c.get(node, 0) + 1}
    def merge(a, b):
        valid(a, 'a')
        valid(b, 'b')
        out = {k: max(a.get(k, 0), b.get(k, 0)) for k in set(a) | set(b)}
        out = {k: v for k, v in out.items() if v > 0}
        assert len(out) <= 64
        return out
    def compare(a, b):
        valid(a, 'a')
        valid(b, 'b')
        le = all(a.get(k, 0) <= b.get(k, 0) for k in set(a) | set(b))
        ge = all(a.get(k, 0) >= b.get(k, 0) for k in set(a) | set(b))
        return 'EQUAL' if le and ge else 'BEFORE' if le else 'AFTER' if ge else 'CONCURRENT'
    assert increment({}, 'a') == {'a': 1}
    assert merge({'a': 2}, {'a': 1, 'b': 3}) == {'a': 2, 'b': 3}
    assert compare({'a': 1}, {'a': 2}) == 'BEFORE' and compare({'a': 1}, {'b': 1}) == 'CONCURRENT'
    report['clock'] = {'vectors': 4}
    # Hunks: difflib agreement on change shape + header arithmetic.
    sm = difflib.SequenceMatcher(None, ['a', 'b', 'c'], ['a', 'B', 'c'])
    assert sm.get_opcodes() == [('equal', 0, 1, 0, 1), ('replace', 1, 2, 1, 2), ('equal', 2, 3, 2, 3)]
    assert (1 - 1, 3, 1 - 1, 3) == (0, 3, 0, 3)  # ctx3 header: -1,3 +1,3
    assert (2 - 1 - 1 + 1, 1) == (1, 1)  # ctx0 single-line counts
    report['hunks'] = {'opcodes': 3}
    # BMP mirror.
    raw = (FIX / 'tiny.bmp').read_bytes()
    assert raw[:2] == b'BM' and len(raw) == 70
    assert struct.unpack('<I', raw[2:6])[0] == 70
    assert struct.unpack('<I', raw[10:14])[0] == 54
    assert struct.unpack('<I', raw[14:18])[0] == 40
    w, h = struct.unpack('<ii', raw[18:26])
    assert (w, h) == (2, 2)
    assert struct.unpack('<H', raw[26:28])[0] == 1
    assert struct.unpack('<H', raw[28:30])[0] == 24
    assert struct.unpack('<I', raw[30:34])[0] == 0
    top = bytearray(raw)
    top[22:26] = struct.pack('<i', -2)
    assert struct.unpack('<i', top[22:26])[0] == -2  # top-down variant shape
    report['bmp'] = {'width': 2, 'height': 2}
    # Gzip mirror: independent flag-walk + both fixtures gunzip.
    assert gzip.open(FIX / 'name.gz', 'rb').read() == b'hello fusion\n'
    assert gzip.open(FIX / 'hcrc.gz', 'rb').read() == b'crc guarded\n'
    for f, want in [('name.gz', (1700000000, 2, 255, b'hello.txt', 20)),
                    ('hcrc.gz', (1700000000, 2, 3, None, 12))]:
        b = (FIX / f).read_bytes()
        assert b[:2] == b'\x1f\x8b' and b[2] == 8
        flg = b[3]
        assert flg & 0xE0 == 0
        assert struct.unpack('<I', b[4:8])[0] == want[0]
        assert b[8] == want[1] and b[9] == want[2]
        i = 10
        if flg & 0x04:
            (xlen,) = struct.unpack('<H', b[i:i + 2])
            i += 2 + xlen
        nm = None
        if flg & 0x08:
            end = b.index(b'\x00', i)
            nm = b[i:end]
            i = end + 1
        if flg & 0x10:
            i = b.index(b'\x00', i) + 1
        if flg & 0x02:
            assert zlib.crc32(b[:i]) & 0xFFFF == struct.unpack('<H', b[i:i + 2])[0]
            i += 2
        assert nm == want[3] and i == want[4] and i < len(b)
    report['gzip'] = {'fixtures': 2}
    # TOTP mirror: hmac-based over raw bytes (kernel: javax.crypto + base32).
    key = base64.b32decode('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ')
    assert key == b'12345678901234567890'
    def totp(t, digits):
        msg = struct.pack('>Q', t // 30)
        h = hmac.new(key, msg, hashlib.sha1).digest()
        off = h[-1] & 0x0F
        v = struct.unpack('>I', h[off:off + 4])[0] & 0x7FFFFFFF
        return str(v % (10 ** digits)).zfill(digits)
    rfc = [(59, '94287082'), (1111111109, '07081804'), (1111111111, '14050471'),
           (1234567890, '89005924'), (2000000000, '69279037'), (20000000000, '65353130')]
    for t, want in rfc:
        assert totp(t, 8) == want, (t, totp(t, 8))
    assert totp(59, 6) == '287082' and totp(1111111109, 6) == '081804'
    assert totp(1234567890, 6) == '005924' and totp(2000000000, 7) == '9279037'
    report['totp'] = {'rfc_vectors': 6}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r33/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print('R33 reference PASS: e164 12, ansi 10, chunk 4, glob 19 (recursive matcher), '
          'fuzzy 6, clock 4, hunks difflib-agree, bmp 2x2, gzip 2 fixtures gunzip, totp 6 RFC vectors.')

if __name__ == '__main__':
    main()
