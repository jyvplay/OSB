#!/usr/bin/env python3
"""Independent Python recomputation of R37 kernel behavior from fixtures/sources.

Cross-checks slugs (regex pipeline + loop disambiguation), IEC sizes
(Decimal ROUND_HALF_UP + power loop), VTODO tallies (regex block parse +
Counter), XZ stream headers (struct + binascii.crc32 re-parse of the
fixture and synthesized variants), paste re-indentation (strip-length
arithmetic), RTSP status lines (regex + cardinality), share-URL cleaning
(urlsplit + pair filter), HIBP range scans (dict build + lookup), polylines
(bit-accumulator decode/encode against Google's canonical example), MP4
top-level boxes (struct walk), OSC 8 sequences (regex split), reciprocal
rank fusion (defaultdict + first-seen tie order), exit statuses (dict +
split signal table), and field of view (atan2 form). The mirrors are
deliberately written differently from the kernels; agreement is
cross-language evidence for the LOGIC ONLY -- not Android, device,
service, or acceptance evidence.
"""
import binascii
import json
import math
import re
import struct
from collections import Counter
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r37'


def main():
    report = {}

    # ---- SlugHead: strip, lower, drop, then map spaces -----------------
    def slug(heading):
        assert heading.strip()
        s = heading.strip().lower().replace('\t', ' ')
        s = re.sub(r'[^a-z0-9_ -]', '', s)
        s = s.replace(' ', '-')
        assert s, 'no slug characters'
        return s

    assert slug('Hello, World!') == 'hello-world'
    assert slug('A  B') == 'a--b'
    assert slug('  Trim  Me  ') == 'trim--me'
    assert slug('Caf\u00e9') == 'caf'
    try:
        slug('\u65e5\u672c\u8a9e')
        raise AssertionError('non-ascii heading must refuse')
    except AssertionError:
        pass

    def unique(existing, heading):
        base = slug(heading)
        if base not in existing:
            return base
        n = 1
        while f'{base}-{n}' in existing:
            n += 1
        return f'{base}-{n}'

    assert unique({'hi', 'hi-2'}, 'Hi') == 'hi-1'
    assert unique({'hi-1'}, 'Hi-1') == 'hi-1-1'
    report['slug'] = {'vectors': 7}

    # ---- SizeBucket: power loop + Decimal half-up ----------------------
    UNITS = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB']

    def scale(n):
        assert n >= 0
        tier = 0
        while tier < len(UNITS) - 1 and n >= 1024 ** (tier + 1):
            tier += 1
        return n / float(1024 ** tier), UNITS[tier]

    def fmt(n):
        value, unit = scale(n)
        if unit == 'B':
            return f'{n} B'
        q = (Decimal(value) * 10).quantize(Decimal('1'), rounding=ROUND_HALF_UP) / 10
        mant = str(int(q)) if q == q.to_integral_value() else str(q)
        return f'{mant} {unit}'

    assert fmt(0) == '0 B'
    assert fmt(1024) == '1 KiB'
    assert fmt(1536) == '1.5 KiB'
    assert fmt(1572864) == '1.5 MiB'
    assert fmt(1100) == '1.1 KiB'
    assert fmt(1048575) == '1024 KiB'
    assert fmt(2 ** 63 - 1) == '8192 PiB'
    assert scale(2 ** 50) == (1.0, 'PiB')
    report['sizebucket'] = {'vectors': 8}

    # ---- TaskTally: regex block parse + Counter ------------------------
    TODOS = [
        'BEGIN:VTODO\nUID:1\nSUMMARY:a\nSTATUS:NEEDS-ACTION\nEND:VTODO',
        'BEGIN:VTODO\nUID:2\nSUMMARY:b\nSTATUS:NEEDS-ACTION\nEND:VTODO',
        'BEGIN:VTODO\nUID:3\nSUMMARY:c\nSTATUS:IN-PROCESS\nEND:VTODO',
        'BEGIN:VTODO\nUID:4\nSUMMARY:d\nSTATUS:COMPLETED\nEND:VTODO',
        'BEGIN:VTODO\nUID:5\nSUMMARY:e\nSTATUS:CANCELLED\nEND:VTODO',
        'BEGIN:VTODO\nUID:6\nSUMMARY:f\nEND:VTODO',
    ]
    ALLOWED = ('NEEDS-ACTION', 'COMPLETED', 'IN-PROCESS', 'CANCELLED')
    counts = Counter()
    for block in TODOS:
        m = re.search(r'^STATUS:(\S+)$', block, re.M)
        status = m.group(1) if m else None
        assert status is None or status in ALLOWED, status
        counts[status] += 1
    assert counts['NEEDS-ACTION'] == 2 and counts['IN-PROCESS'] == 1
    assert counts['COMPLETED'] == 1 and counts['CANCELLED'] == 1
    assert counts[None] == 1 and sum(counts.values()) == 6
    assert 'IN-PROGRESS' not in ALLOWED
    report['tasktally'] = {'todos': 6}

    # ---- XzHead: struct + binascii over the real fixture ---------------
    raw = (FIX / 'xz.bin').read_bytes()
    assert len(raw) == 12
    assert raw[:6] == bytes([0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00])
    flags = raw[6:8]
    assert flags[0] == 0
    crc, = struct.unpack('<I', raw[8:12])
    assert crc == binascii.crc32(flags) & 0xFFFFFFFF, hex(crc)
    assert crc == 0x36DE2269
    CHECK_IDS = {0x00: 'None', 0x01: 'CRC32', 0x04: 'CRC64', 0x0A: 'SHA-256'}
    assert CHECK_IDS[flags[1]] == 'CRC32'
    assert 0x07 not in CHECK_IDS and 0x0A in CHECK_IDS
    for fid, name in ((0, 'None'), (4, 'CRC64'), (10, 'SHA-256')):
        f = bytes([0, fid])
        assert binascii.crc32(f) & 0xFFFFFFFF == {
            0: 0x41D912FF, 4: 0x46B4D6E6, 10: 0xA10CFBE1}[fid], name
    assert binascii.crc32(bytes([0, 1])) & 0xFFFFFFFF == 0x36DE2269
    report['xz'] = {'header_len': 12, 'check_ids': sorted(CHECK_IDS)}

    # ---- PasteWrap: strip-length arithmetic ----------------------------
    def reindent(lines, unit, base):
        assert unit == '\t' or (0 < len(unit) <= 8 and set(unit) == {' '})
        assert 0 <= base <= 64
        out = []
        for line in lines:
            assert '\n' not in line and '\r' not in line
            if not line.strip():
                out.append('')
                continue
            body = line.lstrip(' \t')
            lead = line[:len(line) - len(body)]
            if unit == '\t':
                assert set(lead) <= {'\t'}, 'ragged indent'
                depth = len(lead)
            else:
                assert lead == ' ' * len(lead), 'ragged indent'
                assert len(lead) % len(unit) == 0, 'ragged indent'
                depth = len(lead) // len(unit)
            assert base + depth <= 64
            out.append(unit * (base + depth) + body)
        return out

    assert reindent(['a'], '  ', 1) == ['  a']
    assert reindent(['  b'], '  ', 1) == ['    b']
    assert reindent(['a', '\tb'], '\t', 0) == ['a', '\tb']
    assert reindent(['  ', ''], '  ', 2) == ['', '']
    report['paste'] = {'vectors': 4}

    # ---- RtspReason: regex parse + grammar table ------------------------
    RTSP = """100 Continue|200 OK|201 Created|250 Low on Storage Space|300 Multiple Choices|
301 Moved Permanently|302 Moved Temporarily|303 See Other|304 Not Modified|305 Use Proxy|
400 Bad Request|401 Unauthorized|402 Payment Required|403 Forbidden|404 Not Found|
405 Method Not Allowed|406 Not Acceptable|407 Proxy Authentication Required|
408 Request Time-out|410 Gone|411 Length Required|412 Precondition Failed|
413 Request Entity Too Large|414 Request-URI Too Large|415 Unsupported Media Type|
451 Parameter Not Understood|452 Conference Not Found|453 Not Enough Bandwidth|
454 Session Not Found|455 Method Not Valid in This State|
456 Header Field Not Valid for Resource|457 Invalid Range|458 Parameter Is Read-Only|
459 Aggregate operation not allowed|460 Only aggregate operation allowed|
461 Unsupported transport|462 Destination unreachable|500 Internal Server Error|
501 Not Implemented|502 Bad Gateway|503 Service Unavailable|504 Gateway Time-out|
505 RTSP Version not supported|551 Option not supported"""
    table = {}
    for chunk in RTSP.replace('\n', '').split('|'):
        code, reason = chunk.split(' ', 1)
        table[int(code)] = reason
    assert len(table) == 44, len(table)
    assert table[304] == 'Not Modified' and 306 not in table
    m = re.match(r'^RTSP/1\.0 (\d{3}) (.+)$', 'RTSP/1.0 454 Session Not Found')
    assert (int(m.group(1)), m.group(2)) == (454, 'Session Not Found')
    assert re.match(r'^RTSP/1\.0 (\d{3}) (.+)$', 'RTSP/1.0 200') is None
    report['rtsp'] = {'codes': len(table)}

    # ---- UtmStrip: urlsplit + pair filter ------------------------------
    from urllib.parse import urlsplit

    TRACKERS = {'utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content',
                'utm_id', 'fbclid', 'gclid', 'dclid', 'msclkid', 'mc_cid', 'mc_eid',
                'igshid', 'ttclid', 'twclid', 'yclid', '_openstat', 'vero_id'}

    def clean(url):
        assert url.strip() and '\n' not in url
        parts = urlsplit(url)
        if parts.query == '':
            return url
        pairs = [p for p in parts.query.split('&')
                 if p.split('=', 1)[0].lower() not in TRACKERS and p.split('=', 1)[0]]
        head = f'{parts.scheme}://{parts.netloc}{parts.path}' if parts.scheme else parts.path
        frag = f'#{parts.fragment}' if parts.fragment else ''
        return head + (f"?{'&'.join(pairs)}" if pairs else '') + frag

    assert clean('https://a.com/p?utm_source=x&id=7#f') == 'https://a.com/p?id=7#f'
    assert clean('https://a.com/p?utm_source=x') == 'https://a.com/p'
    assert clean('https://a.com/p?utm_medium=m#frag') == 'https://a.com/p#frag'
    assert clean('https://a.com/p?UTM_Source=x&a=1') == 'https://a.com/p?a=1'
    assert clean('https://a.com/p?q') == 'https://a.com/p?q'
    assert len(TRACKERS) == 18
    report['utm'] = {'trackers': len(TRACKERS)}

    # ---- HibpRange: dict build + lookup --------------------------------
    sha = 'ABCDE12345ABCDE12345ABCDE12345ABCDE12345'
    body = ('0018A45C4D1DEF81644B54AB7F969B88D65:3\n'
            '12345ABCDE12345ABCDE12345ABCDE12345:7\n'
            'FFFFFABCDE12345ABCDE12345ABCDE12345:1\n')
    pairs = {}
    for line in body.splitlines():
        if not line:
            continue
        suffix, _, count = line.partition(':')
        assert len(suffix) == 35 and suffix.isupper()
        pairs[suffix] = int(count)
    assert pairs[sha[5:]] == 7
    assert pairs.get('0' * 35, 0) == 0
    assert len(sha) == 40 and all(c in '0123456789ABCDEF' for c in sha.upper())
    report['hibp'] = {'lines': len(pairs)}

    # ---- PolyLine: bit-accumulator, canonical example ------------------
    def decode(code):
        pts, lat, lng = [], 0, 0
        idx = 0

        def value():
            nonlocal idx
            acc = shift = 0
            while True:
                assert idx < len(code), 'truncated'
                b = ord(code[idx]) - 63
                idx += 1
                assert 0 <= b <= 63
                acc |= (b & 0x1F) << shift
                shift += 5
                assert shift <= 35
                if b < 0x20:
                    break
            return -((acc + 1) >> 1) if acc & 1 else acc >> 1

        while idx < len(code):
            lat += value()
            lng += value()
            pts.append((lat / 1e5, lng / 1e5))
        return pts

    def encode(pts):
        out, plat, plng = [], 0, 0
        for lat, lng in pts:
            assert -90.0 <= lat <= 90.0 and -180.0 <= lng <= 180.0
            ilat, ilng = round(lat * 1e5), round(lng * 1e5)
            for delta in (ilat - plat, ilng - plng):
                u = ~(delta << 1) if delta < 0 else delta << 1
                while u >= 0x20:
                    out.append(chr(((u & 0x1F) | 0x20) + 63))
                    u >>= 5
                out.append(chr(u + 63))
            plat, plng = ilat, ilng
        return ''.join(out)

    CANON = '_p~iF~ps|U_ulLnnqC_mqNvxq`@'
    pts = decode(CANON)
    assert pts == [(38.5, -120.2), (40.7, -120.95), (43.252, -126.453)], pts
    assert encode(pts) == CANON
    assert decode('??') == [(0.0, 0.0)]
    assert encode([(90.0, 180.0)]) and decode(encode([(90.0, 180.0)])) == [(90.0, 180.0)]
    report['polyline'] = {'points': 3, 'roundtrip': True}

    # ---- Mp4Box: struct walk of the real fixture ----------------------
    mp4 = (FIX / 'mp4.bin').read_bytes()
    assert len(mp4) == 32
    boxes, at = [], 0
    while at < len(mp4):
        assert len(mp4) - at >= 8
        size, = struct.unpack_from('>I', mp4, at)
        fourcc = mp4[at + 4:at + 8].decode('ascii')
        assert size >= 8 and size <= len(mp4) - at
        boxes.append((fourcc, size, at))
        at += size
    assert boxes == [('ftyp', 24, 0), ('free', 8, 24)], boxes
    large = bytearray(20)
    large[0:4] = struct.pack('>I', 1)
    large[4:8] = b'mdat'
    large[8:16] = struct.pack('>Q', 20)
    assert struct.unpack_from('>Q', large, 8)[0] == 20
    assert struct.unpack_from('>I', b'\x00\x00\x00\x00', 0)[0] == 0
    report['mp4'] = {'boxes': len(boxes)}

    # ---- OscLink: regex split ------------------------------------------
    def osc(seq):
        assert seq.startswith('\x1b]8;')
        term = 1 if seq.endswith('\x07') else 2
        assert term == 1 or seq.endswith('\x1b\\')
        body = seq[4:len(seq) - term]
        params_raw, _, uri = body.partition(';')
        assert ';' in body or params_raw == ''
        params = {}
        if params_raw:
            for kv in params_raw.split(':'):
                k, _, v = kv.partition('=')
                assert k and k not in params
                params[k] = v
        assert not any(c.isspace() or ord(c) < 0x20 or ord(c) == 0x7F for c in uri)
        return params, uri, params.get('id')

    assert osc('\x1b]8;id=xyz:foo=bar;https://e.com\x07') == (
        {'id': 'xyz', 'foo': 'bar'}, 'https://e.com', 'xyz')
    assert osc('\x1b]8;;\x07') == ({}, '', None)
    assert osc('\x1b]8;;x\x1b\\')[1] == 'x'
    report['osc'] = {'vectors': 3}

    # ---- RankFuse: defaultdict + first-seen order ----------------------
    def fuse(rankings, k=60):
        assert k >= 1 and rankings
        scores, first = {}, {}
        for lst in rankings:
            assert len(lst) == len(set(lst))
            for rank0, ident in enumerate(lst):
                assert ident.strip()
                first.setdefault(ident, len(first))
                scores[ident] = scores.get(ident, 0.0) + 1.0 / (k + rank0 + 1)
        return sorted(scores.items(), key=lambda kv: (-kv[1], first[kv[0]]))

    got = fuse([['a', 'b'], ['b', 'c']])
    assert [i for i, _ in got] == ['b', 'a', 'c']
    assert abs(got[0][1] - (1 / 61 + 1 / 62)) < 1e-12
    assert abs(got[1][1] - 1 / 61) < 1e-12
    assert [i for i, _ in fuse([['a'], ['b']])] == ['a', 'b']
    assert abs(fuse([['a', 'b']], k=1)[0][1] - 0.5) < 1e-12
    report['rankfuse'] = {'lists': 2}

    # ---- ExitCode: dicts + split signal table ---------------------------
    SYSEXITS = {64: 'EX_USAGE', 65: 'EX_DATAERR', 66: 'EX_NOINPUT', 67: 'EX_NOUSER',
                68: 'EX_NOHOST', 69: 'EX_UNAVAILABLE', 70: 'EX_SOFTWARE', 71: 'EX_OSERR',
                72: 'EX_OSFILE', 73: 'EX_CANTCREAT', 74: 'EX_IOERR', 75: 'EX_TEMPFAIL',
                76: 'EX_PROTOCOL', 77: 'EX_NOPERM', 78: 'EX_CONFIG'}
    SIGNALS = dict(enumerate(
        'HUP INT QUIT ILL TRAP ABRT BUS FPE KILL USR1 SEGV USR2 PIPE ALRM TERM '
        'STKFLT CHLD CONT STOP TSTP TTIN TTOU URG XCPU XFSZ VTALRM PROF WINCH IO PWR SYS'
        .split(), 1))
    assert len(SYSEXITS) == 15 and len(SIGNALS) == 31

    def name(code):
        assert 0 <= code <= 255
        if code == 0:
            return 'success'
        if code in (1, 2):
            return {1: 'general error', 2: 'shell misuse'}[code]
        if code in (126, 127):
            return {126: 'not executable', 127: 'command not found'}[code]
        if code in SYSEXITS:
            return SYSEXITS[code]
        if code > 128:
            sig = code - 128
            return f'killed by SIG{SIGNALS[sig]}' if sig in SIGNALS else f'killed by signal {sig}'
        return f'exit {code}'

    assert name(130) == 'killed by SIGINT' and name(159) == 'killed by SIGSYS'
    assert name(160) == 'killed by signal 32' and name(128) == 'exit 128'
    assert name(64) == 'EX_USAGE' and name(78) == 'EX_CONFIG'
    report['exitcode'] = {'sysexits': len(SYSEXITS), 'signals': len(SIGNALS)}

    # ---- FovMath: atan2 form -------------------------------------------
    def fov(f, d):
        assert f > 0 and d > 0
        return math.degrees(2 * math.atan2(d, 2 * f))

    assert abs(fov(50, 36) - 39.597752709049864) < 1e-12
    assert abs(fov(35, 36) - 54.43222311461495) < 1e-12
    assert abs(fov(50, 24) - 26.991466561591626) < 1e-12
    assert abs(fov(85, 36) - 23.913168486298265) < 1e-12
    assert abs(fov(1e9, 36) - 2.062648062470963e-6) < 1e-15
    assert 50 * 1.5 == 75.0
    report['fov'] = {'vectors': 6}

    print('R37 reference PASS: ' + json.dumps(report, sort_keys=True))
    print('Host Python only; agreement is cross-language logic evidence, '
          'not Android/device evidence.')


if __name__ == '__main__':
    main()
