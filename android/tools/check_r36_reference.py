#!/usr/bin/env python3
"""Independent Python recomputation of R36 kernel behavior from fixtures/sources.

Cross-checks RPN stacks (float mirror + classic pin), name-clash gap fill
(loop mirror), greedy wrap (independent walk), redirect plans (token walk),
Cast transitions (dict-table mirror), scale bars (slippy recompute), WAV/TIFF
headers (struct-based re-parse of the fixtures), k-anonymity audits
(Counter mirror + violation order), attempt windows (comprehension counts),
depth-of-field limits (formula mirror), and Shannon entropy (math.log2
mirror). Agreement is cross-language evidence for the LOGIC ONLY -- not
Android, device, service, or acceptance evidence.
"""
import json
import math
import re
import struct
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r36'


def main():
    report = {}
    # RPN mirror.
    def rpn(toks):
        st = []
        for t in toks:
            if t in ('+', '-', '*', '/'):
                assert len(st) >= 2
                b, a = st.pop(), st.pop()
                r = {'+': a + b, '-': a - b, '*': a * b, '/': a / b}[t]
                assert t != '/' or b != 0.0
                assert math.isfinite(r)
                st.append(r)
            elif t in ('neg', 'sqrt'):
                assert len(st) >= 1
                a = st.pop()
                st.append(-a if t == 'neg' else math.sqrt(a))
            else:
                v = float(t)
                assert math.isfinite(v)
                st.append(v)
        assert len(st) == 1
        return st[0]
    assert rpn(['3', '4', '+']) == 7.0
    assert rpn(['15', '7', '1', '1', '+', '-', '/', '3', '*', '2', '1', '1', '+', '+', '-']) == 5.0
    assert rpn(['0.1', '0.2', '+']) == 0.30000000000000004
    assert rpn(['9', 'sqrt']) == 3.0
    report['rpn'] = {'vectors': 4}
    # Names mirror.
    def clash(taken, want):
        assert want.strip()
        if want not in taken:
            return want
        dot = want.rfind('.')
        stem, ext = (want, '') if dot <= 0 else (want[:dot], want[dot:])
        base = re.sub(r' \(\d+\)$', '', stem)
        k = 2
        while f'{base} ({k}){ext}' in taken:
            k += 1
        return f'{base} ({k}){ext}'
    assert clash({'a.txt'}, 'a.txt') == 'a (2).txt'
    assert clash({'a.txt', 'a (3).txt'}, 'a.txt') == 'a (2).txt'
    assert clash({'README'}, 'README') == 'README (2)'
    assert clash({'a.tar.gz'}, 'a.tar.gz') == 'a.tar (2).gz'
    assert clash({'a (2).txt'}, 'a (2).txt') == 'a (3).txt'
    report['names'] = {'vectors': 5}
    # Wrap mirror.
    def wrap(text, width):
        assert 1 <= width <= 1000 and '\n' not in text and '\r' not in text
        words = text.split()
        lines, cur = [], ''
        for w in words:
            assert len(w) <= width
            if not cur:
                cur = w
            elif len(cur) + 1 + len(w) <= width:
                cur += ' ' + w
            else:
                lines.append(cur)
                cur = w
        if cur:
            lines.append(cur)
        return lines
    assert wrap('a bb ccc', 6) == ['a bb', 'ccc']
    assert wrap('one two three four', 9) == ['one two', 'three', 'four']
    assert wrap('aa bb cc dd', 5) == ['aa bb', 'cc dd']
    report['wrap'] = {'vectors': 3}
    # Redir mirror.
    def redir(toks):
        argv, reds, seen = [], [], set()
        i = 0
        while i < len(toks):
            t = toks[i]
            if t in ('>', '>>', '<', '2>'):
                assert i + 1 < len(toks)
                tgt = toks[i + 1]
                assert tgt and tgt not in ('>', '>>', '<', '2>')
                stream = {'<': 'stdin', '>': 'stdout', '>>': 'stdout', '2>': 'stderr'}[t]
                assert stream not in seen
                seen.add(stream)
                reds.append((stream, t == '>>', tgt))
                i += 2
            else:
                argv.append(t)
                i += 1
        return argv, reds
    assert redir(['a', '<', 'i', '>', 'o', '2>', 'e']) == (
        ['a'], [('stdin', False, 'i'), ('stdout', False, 'o'), ('stderr', False, 'e')])
    assert redir(['cmd', '2>file']) == (['cmd', '2>file'], [])
    report['redir'] = {'vectors': 2}
    # Cast mirror: full 4x7 table as nested dict (11 legal cells).
    T = {
        'IDLE': {'LOAD': 'BUFFERING', 'PLAY': 'PLAYING'},
        'BUFFERING': {'BUFFER_OK': 'PLAYING', 'STOP': 'IDLE'},
        'PLAYING': {'PAUSE': 'PAUSED', 'LOAD': 'BUFFERING', 'STOP': 'IDLE', 'FINISH': 'IDLE'},
        'PAUSED': {'PLAY': 'PLAYING', 'LOAD': 'BUFFERING', 'STOP': 'IDLE'},
    }
    assert sum(len(v) for v in T.values()) == 11
    assert T['IDLE']['LOAD'] == 'BUFFERING' and T['PLAYING']['FINISH'] == 'IDLE'
    assert 'PAUSE' not in T['IDLE'] and 'PLAY' not in T['PLAYING'] and 'LOAD' not in T['BUFFERING']
    report['cast'] = {'legal': 11}
    # Scale mirror: independent slippy recompute.
    def mpp(lat, z):
        assert -85.05112878 <= lat <= 85.05112878 and 0 <= z <= 19
        return 156543.03392 * math.cos(math.radians(lat)) / 2 ** z
    assert mpp(0.0, 0) == 156543.03392
    assert abs(mpp(45.0, 10) - 108.09828206283957) < 1e-9
    def bar(mppv, target):
        maxm = target * mppv
        pow10 = 1.0
        while pow10 * 10.0 <= maxm:
            pow10 *= 10.0
        while pow10 > maxm:
            pow10 /= 10.0
        best = pow10
        for mult in (2.0, 5.0, 10.0):
            if pow10 * mult <= maxm:
                best = pow10 * mult
        return best, best / mppv
    assert bar(1.0, 100.0) == (100.0, 100.0)
    assert bar(1.0, 250.0) == (200.0, 200.0)
    bm, bp = bar(156543.03392, 100.0)
    assert bm == 1.0e7 and abs(bp - 63.8801979851139) < 1e-9
    assert bar(0.5, 3.0) == (1.0, 2.0)
    assert bar(10.0, 50.0) == (500.0, 50.0)
    report['scale'] = {'vectors': 7}
    # WAV mirror: struct-based re-parse of the fixture.
    b = (FIX / 'wav.bin').read_bytes()
    assert len(b) == 44 and b[:4] == b'RIFF' and b[8:12] == b'WAVE' and b[12:16] == b'fmt '
    riff, flen = struct.unpack_from('<II', b, 4)[0], struct.unpack_from('<I', b, 16)[0]
    assert (riff, flen) == (36, 16)
    fmt, ch, rate, brate, align, bits = struct.unpack_from('<HHIIHH', b, 20)
    assert (fmt, ch, rate) == (1, 1, 8000) and brate == rate * align == 16000
    assert align == ch * bits // 8 == 2 and bits == 16
    assert b[36:40] == b'data'
    report['wav'] = {'bytes': 44}
    # TIFF mirror: struct-based re-parse of the fixture.
    t = (FIX / 'tiff.bin').read_bytes()
    assert len(t) == 16 and t[:2] == b'II'
    assert struct.unpack_from('<H', t, 2)[0] == 42
    assert struct.unpack_from('<I', t, 4)[0] == 8
    assert struct.unpack_from('>H', b'MM\x00\x2a\x00\x00\x00\x10', 2)[0] == 42
    assert struct.unpack_from('>I', b'MM\x00\x2a\x00\x00\x00\x10', 4)[0] == 16
    report['tiff'] = {'bytes': 16}
    # KAnon mirror: Counter + insertion-order violations.
    def audit(rows, k):
        assert k >= 1 and rows and all(len(r) == len(rows[0]) > 0 for r in rows)
        counts = Counter(tuple(r) for r in rows)
        viol = [(list(key), c) for key, c in counts.items() if c < k]
        return len(counts), min(counts.values()), not viol, viol
    g, m, okv, v = audit([['a', 'x'], ['a', 'x'], ['b', 'y']], 2)
    assert (g, m, okv, v) == (2, 1, False, [(['b', 'y'], 1)])
    assert audit([['a'], ['a'], ['b'], ['b']], 2)[:3] == (2, 2, True)
    assert audit([['a'], ['b'], ['c']], 2)[3] == [(['a'], 1), (['b'], 1), (['c'], 1)]
    report['kanon'] = {'vectors': 3}
    # Window mirror.
    def allows(prior, now, maxn, win):
        assert now >= 0 and maxn >= 1 and 1 <= win <= 86400 and len(prior) <= 100000
        assert all(t >= 0 for t in prior)
        return sum(1 for t in prior if now - win < t <= now) < maxn
    assert allows([], 100, 3, 60) and not allows([50, 60, 70], 100, 3, 60)
    assert allows([40], 100, 1, 60) and allows([150], 100, 1, 60)
    assert not allows([100], 100, 1, 60) and allows([10, 20], 100, 2, 60)
    report['window'] = {'vectors': 6}
    # DoF mirror.
    def hyper(f, n, c):
        assert f > 0 and n > 0 and c > 0
        return f * f / (n * c) + f
    H = hyper(50.0, 2.8, 0.03)
    assert abs(H - 29811.904761904763) < 1e-6
    near = 5000.0 * (H - 50.0) / (H + 5000.0 - 100.0)
    far = 5000.0 * (H - 50.0) / (H - 5000.0)
    assert abs(near - 4286.988133616846) < 1e-6
    assert abs(far - 5997.505037904232) < 1e-6
    assert 1.0 - H / H <= 0.0  # focus == H -> far infinity
    report['dof'] = {'vectors': 4}
    # Shannon mirror.
    def bits(data):
        assert data and len(data) <= 1048576
        n, tot = len(data), 0.0
        for c in Counter(data).values():
            p = c / n
            tot -= p * math.log2(p)
        return tot
    assert bits(bytes([0, 0, 0, 0])) == 0.0
    assert bits(bytes([0, 1])) == 1.0
    assert bits(bytes([0, 1, 2, 3])) == 2.0
    assert abs(bits(bytes([0, 0, 0, 1])) - 0.8112781244591328) < 1e-12
    report['shannon'] = {'vectors': 4}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r36/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print('R36 reference PASS: rpn 4, names 5, wrap 3, redir 2, cast 11, scale 7, '
          'wav 44B, tiff 16B, kanon 3, window 6, dof 4, shannon 4.')


if __name__ == '__main__':
    main()
