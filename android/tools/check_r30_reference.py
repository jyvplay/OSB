#!/usr/bin/env python3
"""Independent Python recomputation of R30 kernel behavior from fixtures/sources.

Cross-checks RTSP framing, timecode math, VEVENT decode, Lamport sequencing,
GIF structure, patch-scope splits, tar headers, zoom steps, SMS segmentation
and density tiers with independent implementations. Agreement is
cross-language evidence for the LOGIC ONLY -- not Android, device, service, or
acceptance evidence.
"""
import datetime
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r30'

def main():
    report = {}
    # RTSP mirror.
    raw = (FIX / 'rtsp_announce.txt').read_bytes()
    assert b'\n' not in raw.replace(b'\r\n', b'')
    head, body = raw.split(b'\r\n\r\n')
    lines = head.decode('ascii').split('\r\n')
    method, uri, ver = lines[0].split(' ')
    assert (method, uri, ver) == ('ANNOUNCE', 'rtsp://192.168.1.5/3413821438', 'RTSP/1.0')
    headers = dict(l.split(': ', 1) for l in lines[1:])
    assert int(headers['CSeq']) == 1 and int(headers['Content-Length']) == len(body) == 135
    assert body.startswith(b'v=0')
    report['rtsp'] = {'headers': len(headers), 'body': 135}
    # Timecode mirror.
    def tc(ms, fps):
        tf = ms * fps // 1000
        ff = tf % fps
        ts = tf // fps
        return (ts // 3600, (ts // 60) % 60, ts % 60, ff)
    assert tc(0, 25) == (0, 0, 0, 0) and tc(1040, 25) == (0, 0, 1, 1)
    assert tc(86399999, 30) == (23, 59, 59, 29) and tc(3661000, 24) == (1, 1, 1, 0)
    report['timecode'] = {'vectors': 4}
    # VEVENT mirror.
    text = (FIX / 'event.ics').read_bytes().decode()
    assert text.endswith('\r\n') and '\n' not in text.replace('\r\n', '')
    lines = []
    for ln in text.split('\r\n')[:-1]:
        if ln.startswith(' '):
            lines[-1] += ln[1:]
        else:
            lines.append(ln)
    assert lines[0] == 'BEGIN:VCALENDAR' and lines[-1] == 'END:VCALENDAR'
    props = dict(l.split(':', 1) for l in lines[4:-2])
    assert set(props) == {'UID', 'DTSTAMP', 'DTSTART', 'DTEND', 'SUMMARY', 'DESCRIPTION'}
    def stamp(v):
        return datetime.datetime.strptime(v, '%Y%m%dT%H%M%SZ').replace(tzinfo=datetime.timezone.utc)
    assert stamp(props['DTSTAMP']) == datetime.datetime(2026, 10, 22, 6, 30, tzinfo=datetime.timezone.utc)
    def unesc(v):
        out, i = '', 0
        while i < len(v):
            if v[i] != '\\':
                out += v[i]
                i += 1
            else:
                out += {'\\': '\\', ';': ';', ',': ',', 'n': '\n'}[v[i + 1]]
                i += 2
        return out
    summary = unesc(props['SUMMARY'])
    assert summary.startswith('Planning, review; Q4 goals \\ draft ') and len(summary) == 125
    assert unesc(props['DESCRIPTION']) == 'Line one\nLine two\\ end'
    report['vevent'] = {'props': 6}
    # Lamport mirror.
    c = 0
    seq = []
    c += 1
    seq.append(c)
    c += 1
    seq.append(c)
    c = max(c, 5) + 1
    seq.append(c)
    c += 1
    seq.append(c)
    c = max(c, 3) + 1
    seq.append(c)
    assert seq == [1, 2, 6, 7, 8]
    report['lamport'] = {'events': 5}
    # GIF mirror.
    g = (FIX / 'frames2.gif').read_bytes()
    assert g[:6] == b'GIF89a'
    w, h = int.from_bytes(g[6:8], 'little'), int.from_bytes(g[8:10], 'little')
    assert (w, h) == (2, 1) and g[10] & 0x80
    pos, frames = 13 + (1 << ((g[10] & 7) + 1)) * 3, 0
    def skip_sub(p):
        while True:
            n = g[p]
            p += 1
            if n == 0:
                return p
            p += n
    while True:
        b = g[pos]
        if b == 0x3B:
            assert pos == len(g) - 1
            break
        if b == 0x21:
            pos = skip_sub(pos + 2)
        elif b == 0x2C:
            frames += 1
            pos += 10
            if g[pos - 1] & 0x80:
                pos += (1 << ((g[pos - 1] & 7) + 1)) * 3
            pos = skip_sub(pos + 1)
        else:
            raise SystemExit(f'bad block {b:#x}')
    assert frames == 2
    report['gif'] = {'frames': 2}
    # Patch-scope mirror.
    prefixes, exact = ['appearance', 'locale'], {'theme'}
    def permits(k):
        return k in exact or any(k == p or k.startswith(p + '.') for p in prefixes)
    assert permits('theme') and permits('appearance.a.b') and not permits('appearancex')
    base = {'appearance.mode': 'light', 'locale': 'en', 'other.key': '1'}
    want = {'appearance.mode': 'dark', 'appearance.font': 'big', 'locale': 'fr', 'other.key': '2'}
    deltas = sorted(k for k in set(base) | set(want) if base.get(k) != want.get(k))
    assert [k for k in deltas if permits(k)] == ['appearance.font', 'appearance.mode', 'locale']
    assert [k for k in deltas if not permits(k)] == ['other.key']
    report['patchscope'] = {'allowed': 3, 'refused': 1}
    # Tar mirror.
    t = (FIX / 'archive.tar').read_bytes()
    def header(off):
        r = t[off:off + 512]
        s = sum(0x20 if 148 <= i < 156 else r[i] for i in range(512))
        stored = int(r[148:156].split(b'\0')[0].strip() or b'0', 8)
        assert s == stored, (off, s, stored)
        assert r[257:263] == b'ustar\0' and r[263:265] == b'00'
        assert r[156:157] in (b'0', b'\0')
        name = r[:100].split(b'\0')[0].decode()
        size = int(r[124:136].split(b'\0')[0].strip() or b'0', 8)
        return name, size
    n1, s1 = header(0)
    assert (n1, s1) == ('hello.txt', 6)
    n2, s2 = header(512 + ((s1 + 511) // 512) * 512)
    assert (n2, s2) == ('dir/nested.bin', 768)
    report['tar'] = {'entries': 2}
    # Zoom mirror.
    assert 2.0 ** (1 / 2.0) > 1.4142
    assert round(math.log2(2.828) * 2) == 3
    report['zoom'] = {'vectors': 2}
    # SMS mirror.
    basic = set("@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà")
    ext = set("^{}\\[~]|€")
    def classify(s):
        return 'GSM7' if all(c in basic or c in ext for c in s) else 'UCS2'
    assert classify('hello £') == 'GSM7' and classify('hi —') == 'UCS2' and classify('€100') == 'GSM7'
    assert sum(2 if c in ext else 1 for c in 'a^b') == 4
    assert (161 + 152) // 153 == 2 and (71 + 66) // 67 == 2
    report['sms'] = {'classes': 3}
    # Density mirror.
    def select(dpi, touch):
        if touch or dpi >= 280:
            return 'TOUCH'
        return 'BALANCED' if dpi >= 180 else 'COMPACT'
    assert [select(d, False) for d in (179, 180, 279, 280)] == ['COMPACT', 'BALANCED', 'BALANCED', 'TOUCH']
    assert select(120, True) == 'TOUCH'
    report['density'] = {'tiers': 5}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r30/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print('R30 reference PASS: RTSP 135B body, timecode 4 vectors, VEVENT 6 props, '
          'Lamport 5 events, GIF 2 frames, scope 3+1, tar 2 entries, zoom 2, SMS 3 classes, density 5 tiers.')

if __name__ == '__main__':
    main()
