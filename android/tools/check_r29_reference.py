#!/usr/bin/env python3
"""Independent Python recomputation of R29 kernel behavior from fixtures/sources.

Cross-checks share totals/digest, quota accounting, throttle tokens, sort
order, alias expansion, WCAG ratios, locale matching, retention sets, DIDL
fields and CSV sniff/translate with independent implementations. Agreement is
cross-language evidence for the LOGIC ONLY -- not Android, device, service, or
acceptance evidence.
"""
import csv
import hashlib
import io
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r29'

def main():
    report = {}
    # Shares: totals + order-free digest mirror.
    entries = []
    for line in (FIX / 'shares.txt').read_text().splitlines():
        if not line.strip() or line.startswith('#'):
            continue
        name, size, mime, sha = line.split('|')
        entries.append((name, int(size), mime, sha))
    assert len(entries) == 5
    assert sum(e[1] for e in entries) == 1076384256
    def digest(rows):
        h = hashlib.sha256()
        for name, size, mime, sha in sorted(rows, key=lambda r: r[0]):
            h.update(f'{name}\n{size}\n{mime}\n{sha}\n'.encode())
        return h.hexdigest()
    d = digest(entries)
    assert len(d) == 64 and digest(list(reversed(entries))) == d
    report['shares'] = {'entries': 5, 'total': 1076384256}
    # Quota mirror.
    quota, used = {'drafts': 1000, 'cache': 500}, {'drafts': 0, 'cache': 0}
    def alloc(s, b):
        if used[s] > quota[s] - b:
            return False
        used[s] += b
        return True
    assert alloc('drafts', 600) and not alloc('drafts', 401) and used['drafts'] == 600
    used['drafts'] = max(0, used['drafts'] - 200)
    assert quota['drafts'] - used['drafts'] == 600
    used['drafts'] = max(0, used['drafts'] - 99999)
    assert used['drafts'] == 0
    report['quota'] = {'scopes': 2}
    # Throttle token mirror.
    cap, rate, tok, last = 3.0, 1.0, 3.0, 0
    def acq(t):
        nonlocal tok, last
        t = max(t, last)
        tok = min(cap, tok + (t - last) / 1000.0 * rate)
        last = t
        if tok < 1.0:
            return False
        tok -= 1.0
        return True
    assert acq(0) and acq(0) and acq(0) and not acq(0)
    assert not acq(500) and acq(1000) and not acq(1000) and acq(60000)
    assert abs(tok - 2.0) < 1e-9
    report['throttle'] = {'final_tokens': tok}
    # FileSort mirror.
    rows = [('b.TXT', 30, 300, False), ('A.txt', 10, 100, False), ('docs', 0, 50, True),
            ('a.TXT', 20, 200, False), ('pic.PNG', 40, 400, False)]
    def ext(r):
        n, _, _, d = r
        if d or '.' not in n[1:]:
            return ''
        return n.rsplit('.', 1)[1].lower()
    ordered = sorted(rows, key=lambda r: (not r[3], r[0].lower(), r[0]))
    assert [r[0] for r in ordered] == ['docs', 'A.txt', 'a.TXT', 'b.TXT', 'pic.PNG']
    by_kind = sorted([r for r in rows if not r[3]], key=lambda r: (ext(r), r[0].lower(), r[0]))
    assert [r[0] for r in by_kind] == ['pic.PNG', 'A.txt', 'a.TXT', 'b.TXT']
    report['filesort'] = {'rows': 5}
    # Alias mirror.
    table = {'ll': ['ls', '-l'], 'la': ['ll', '-a']}
    def expand(argv):
        seen, head, tail = set(), argv[0], argv[1:]
        while head in table:
            assert head not in seen, 'cycle'
            seen.add(head)
            exp = table[head]
            head, tail = exp[0], exp[1:] + tail
        return [head] + tail
    assert expand(['la']) == ['ls', '-l', '-a']
    table.update({'x': ['y'], 'y': ['x']})
    try:
        expand(['x'])
        raise SystemExit('cycle accepted')
    except AssertionError as e:
        assert str(e) == 'cycle'
    report['alias'] = {'expansions': 1, 'cycles': 1}
    # WCAG mirror.
    def lum(rgb):
        def ch(c):
            v = c / 255.0
            return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
        return 0.2126 * ch(rgb[0]) + 0.7152 * ch(rgb[1]) + 0.0722 * ch(rgb[2])
    def ratio(a, b):
        l1, l2 = lum(a), lum(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    assert abs(ratio((255, 255, 255), (0, 0, 0)) - 21.0) < 1e-9
    assert ratio((0x76,) * 3, (255, 255, 255)) >= 4.5 > ratio((0x77,) * 3, (255, 255, 255))
    report['contrast'] = {'pairs': 3}
    # Locale mirror.
    def parse(tag):
        parts = tag.split('-')
        assert 1 <= len(parts) <= 2 and all(parts)
        lang = parts[0].lower()
        assert re.fullmatch(r'[a-z]{2,3}', lang)
        region = None
        if len(parts) == 2:
            region = parts[1].upper()
            assert re.fullmatch(r'[A-Z]{2}', region)
        return (lang, region)
    def match(req, supported):
        if not supported:
            return None
        want, have = parse(req), [parse(s) for s in supported]
        for h in have:
            if h == want:
                return h
        for h in have:
            if h[0] == want[0]:
                return h
        return have[0]
    fmt = lambda t: t[0] if t[1] is None else f'{t[0]}-{t[1]}'
    assert fmt(match('en-GB', ['en-US', 'fr-FR'])) == 'en-US'
    assert fmt(match('de', ['en-US', 'fr-FR'])) == 'en-US'
    assert match('en', []) is None
    report['locale'] = {'decisions': 3}
    # Retention mirror.
    snaps = [('a', 10, 1, 0), ('b', 10, 1, 0), ('c', 9, 1, 0), ('d', 3, 0, 0), ('e', 40, 5, 1)]
    days = sorted({s[1] for s in snaps}, reverse=True)[:2]
    kept = {s[0] for s in snaps if s[1] in days}
    for idx, k in ((2, 2), (3, 2)):
        for b in sorted({s[idx] for s in snaps}, reverse=True)[:k]:
            best = max([s for s in snaps if s[idx] == b], key=lambda s: (s[1], s[0]))
            kept.add(best[0])
    assert kept == {'a', 'b', 'e'}
    report['retention'] = {'kept': 3}
    # DIDL mirror.
    def items(path):
        root = ET.fromstring((FIX / path).read_text())
        out, containers = [], 0
        for el in root.iter():
            local = el.tag.rsplit('}', 1)[-1]
            if local == 'container':
                containers += 1
            elif local == 'item':
                out.append(el)
        return out, containers
    def child(item, local):
        for el in item:
            if el.tag.rsplit('}', 1)[-1] == local:
                return el
        raise AssertionError(local)
    def dur(s):
        h, m, sec = s.split(':')
        return int(h) * 3600.0 + int(m) * 60.0 + float(sec)
    audio, ac = items('didl_audio.xml')
    assert ac == 1 and len(audio) == 2
    assert child(audio[0], 'title').text == 'Night Owl & Friends'
    assert dur(child(audio[0], 'res').attrib['duration']) == 252.0
    video, vc = items('didl_video.xml')
    assert vc == 0 and len(video) == 1
    assert dur(child(video[0], 'res').attrib['duration']) == 5399.5
    report['didl'] = {'items': 3}
    # CSV sniff/translate mirror.
    def counts(line, d):
        n, q, i = 0, False, 0
        while i < len(line):
            c = line[i]
            if q:
                if c == '"':
                    if i + 1 < len(line) and line[i + 1] == '"':
                        i += 1
                    else:
                        q = False
            elif c == '"':
                q = True
            elif c == d:
                n += 1
            i += 1
        return n
    def sniff(text):
        lines = [l for l in text.splitlines() if l][:32]
        best, best_score = (None, 1), -1
        for d in [',', ';', '\t', '|']:
            cc = [counts(l, d) for l in lines]
            if sum(cc) == 0:
                continue
            score = sum(cc) * (2 if len(set(cc)) == 1 else 1)
            if score > best_score:
                best_score, best = score, (d, max(cc) + 1)
        return best
    assert sniff((FIX / 'semi.csv').read_text()) == (';', 3)
    assert sniff((FIX / 'tab.csv').read_text()) == ('\t', 3)
    assert sniff((FIX / 'pipe.csv').read_text()) == ('|', 3)
    semi_rows = list(csv.reader(io.StringIO((FIX / 'semi.csv').read_text()), delimiter=';'))
    assert len(semi_rows) == 4 and semi_rows[1][0] == 'Doe; Jane' and semi_rows[3][2] == 'multi\nline'
    report['csv'] = {'dialects': 3, 'rows': 4}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r29/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print('R29 reference PASS: shares 5/1076384256B, quota 2 scopes, throttle 2.0 tokens, '
          'filesort 5 rows, alias 1 chain + 1 cycle, contrast 3 pairs, locale 3, retention 3 kept, '
          'DIDL 3 items, CSV 3 dialects.')

if __name__ == '__main__':
    main()
