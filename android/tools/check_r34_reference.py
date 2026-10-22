#!/usr/bin/env python3
"""Independent Python recomputation of R34 kernel behavior from fixtures/sources.

Cross-checks descriptive stats (naive + statistics-module mirrors, NOT the
kernel's Welford update), MIME guesses (stdlib mimetypes agreement), Markdown
outlines (regex-based re-scan), bracket mates (stack-based full scan, NOT the
kernel's depth counter), SOAP faults (xml.etree parse, NOT substring search),
pipeline splits (split-then-repair, NOT the kernel's char scanner), JPEG
markers (independent walk), zlib headers (stdlib roundtrip), VTODOs
(dict-based re-parse), and pseudonyms (JVM output vs Python hmac interop).
Agreement is cross-language evidence for the LOGIC ONLY -- not Android,
device, service, or acceptance evidence.
"""
import argparse
import hmac
import hashlib
import json
import mimetypes
import re
import statistics
import struct
import subprocess
import sys
import zlib
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'tools/fixtures/r34'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tools', type=Path, default=Path.home() / '.cache/fusion-host-r23')
    a = p.parse_args()
    report = {}
    # Stats mirror: naive sums + statistics module (kernel uses Welford).
    def naive(values):
        assert values and all(v == v and v not in (float('inf'), float('-inf')) for v in values)
        n = len(values)
        s = sum(values)
        mean = s / n
        var = sum((v - mean) ** 2 for v in values) / n
        ordered = sorted(values)
        med = ordered[n // 2] if n % 2 else (ordered[n // 2 - 1] + ordered[n // 2]) / 2
        return (n, s, mean, var, min(values), max(values), med)
    assert naive([1.0, 2.0, 3.0, 4.0]) == (4, 10.0, 2.5, 1.25, 1.0, 4.0, 2.5)
    assert naive([7.0]) == (1, 7.0, 7.0, 0.0, 7.0, 7.0, 7.0)
    big = [1e9 + 1, 1e9 + 2, 1e9 + 3]
    assert statistics.mean(big) == 1000000002.0
    assert abs(statistics.pvariance(big) - 2.0 / 3.0) < 1e-9
    assert statistics.median([5.0, 1.0, 3.0]) == 3.0
    report['stats'] = {'vectors': 4}
    # MIME mirror: stdlib mimetypes agreement on stable mappings.
    agree = [('a.pdf', 'application/pdf'), ('a.txt', 'text/plain'), ('a.csv', 'text/csv'),
             ('a.json', 'application/json'), ('a.html', 'text/html'), ('a.png', 'image/png'),
             ('a.jpg', 'image/jpeg'), ('a.gif', 'image/gif'), ('a.svg', 'image/svg+xml'),
             ('a.mp3', 'audio/mpeg'), ('a.wav', 'audio/wav'), ('a.mp4', 'video/mp4'),
             ('a.zip', 'application/zip'), ('a.xml', 'application/xml')]
    for name, want in agree:
        got, _ = mimetypes.guess_type(name)
        norm = 'audio/wav' if got == 'audio/x-wav' else got
        assert norm == want, (name, got)
    assert mimetypes.guess_type('PHOTO.JPG')[0] == 'image/jpeg'
    # Documented convention split: stdlib layers ('application/x-tar', 'gzip') while the
    # kernel returns the single last-extension label 'application/gzip' by design.
    assert mimetypes.guess_type('archive.tar.gz') == ('application/x-tar', 'gzip')
    report['mime'] = {'stdlib_agree': len(agree) + 2}
    # Outline mirror: regex heading detection + fence tracking.
    md = ("# Title\n\ntext\n\n## Section A\n\n```\n# not a heading\n```\n\n### Deep\n\n"
          "#nope\n\n####### seven\n\n# \n\n~~~\n## tilde fake\n~~~\n\n## After ##\n")
    entries, fence = [], None
    for ln, raw in enumerate(md.split('\n'), 1):
        s = raw.strip()
        if fence is None:
            m = re.match(r'^(```+|~~~+)', s)
            if m:
                fence = m.group(1)[0]
                continue
            h = re.match(r'^(#{1,6})[ \t]+(.*?)\s*$', s)
            if h:
                title = re.sub(r'\s+#+$', '', h.group(2)).strip()
                if title:
                    entries.append((len(h.group(1)), title, ln))
        else:
            if re.match(r'^' + re.escape(fence) + r'{3,}\s*$', s):
                fence = None
    assert entries == [(1, 'Title', 1), (2, 'Section A', 5), (3, 'Deep', 11), (2, 'After', 23)], entries
    report['outline'] = {'entries': 4}
    # Bracket mirror: stack-based full scan (kernel counts depth from cursor).
    pairs = {'(': ')', '[': ']', '{': '}'}
    def mates(text):
        stack, out = [], {}
        for i, c in enumerate(text):
            if c in pairs:
                stack.append((c, i))
            elif c in pairs.values():
                if stack and pairs[stack[-1][0]] == c:
                    _, j = stack.pop()
                    out[j] = i
                    out[i] = j
        return out
    m = mates('[{()}]')
    assert (m[0], m[1], m[2], m[5], m[4], m[3]) == (5, 4, 3, 0, 1, 2)
    assert mates('(a)')[0] == 2 and mates('(a)')[2] == 0
    assert 0 not in mates('(a') and 0 not in mates(')a(')
    report['brackets'] = {'cases': 4}
    # SOAP mirror: real XML parse (kernel uses substring search).
    xml = ('<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault>'
           '<faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>'
           '<UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>501</errorCode>'
           '<errorDescription>Action Failed</errorDescription></UPnPError></detail>'
           '</s:Fault></s:Body></s:Envelope>')
    root = ET.fromstring(xml)
    fault = next(el for el in root.iter() if el.tag.endswith('}Fault'))
    def text(tag):
        return next(el for el in fault.iter() if el.tag == tag or el.tag.endswith('}' + tag)).text
    assert text('faultcode') == 's:Client'
    assert text('faultstring') == 'UPnPError'
    assert text('errorCode') == '501'
    assert text('errorDescription') == 'Action Failed'
    report['soap'] = {'fields': 4}
    # Pipe mirror: split-then-repair (kernel scans char by char).
    def qcount(s):
        return (s.count("'") - s.count("\\'")) % 2 == 0 and (s.count('"') - s.count('\\"')) % 2 == 0
    def split_pipes(line):
        assert '\\' not in line or not line.rstrip().endswith('\\') or line.rstrip().endswith('\\\\')
        assert not line.rstrip().endswith('\\') or line.rstrip()[-2:-1] == '\\'
        parts, cur = [], ''
        for seg in line.split('|'):
            cur = seg if not cur else cur + '|' + seg
            if qcount(cur):
                parts.append(cur.strip())
                cur = ''
        assert not cur, 'unbalanced quotes'
        assert all(p for p in parts), 'empty stage'
        return parts
    assert split_pipes('ls') == ['ls']
    assert split_pipes('cat f | grep x | wc -l') == ['cat f', 'grep x', 'wc -l']
    assert split_pipes("echo 'a|b' | wc") == ["echo 'a|b'", 'wc']
    assert split_pipes('echo "x|y"|cat') == ['echo "x|y"', 'cat']
    for bad in ['| a', 'a |', 'a || b', '   ', "echo 'abc", 'echo "abc']:
        try:
            split_pipes(bad)
        except AssertionError:
            pass
        else:
            raise AssertionError(f'no refusal: {bad!r}')
    report['pipes'] = {'vectors': 10}
    # JPEG mirror: independent marker walk over the fixture.
    b = (FIX / 'tiny.jpg').read_bytes()
    assert b[:2] == b'\xff\xd8' and len(b) == 141
    segs, i = [], 2
    while True:
        assert b[i] == 0xFF
        j = i + 1
        while b[j] == 0xFF:
            j += 1
        mk = b[j]
        if mk == 0xD9:
            segs.append((mk, i, 0))
            break
        if mk == 0xDA:
            ln = struct.unpack('>H', b[j + 1:j + 3])[0]
            segs.append((mk, i, ln))
            break
        if mk in range(0xD0, 0xD8) or mk == 0x01:
            segs.append((mk, i, 0))
            i = j + 1
            continue
        ln = struct.unpack('>H', b[j + 1:j + 3])[0]
        assert ln >= 2 and j + 1 + ln <= len(b)
        segs.append((mk, i, ln))
        i = j + 1 + ln
    assert [s[0] for s in segs] == [0xE0, 0xDB, 0xC0, 0xC4, 0xDA]
    assert [s[1] for s in segs] == [2, 20, 89, 102, 124]
    assert [s[2] for s in segs] == [16, 67, 11, 20, 8]
    sof = b[89:]
    assert sof[0:2] == b'\xff\xc0'
    h, w = struct.unpack('>H', sof[5:7])[0], struct.unpack('>H', sof[7:9])[0]
    assert (w, h, sof[9]) == (1, 1, 1)
    report['jpeg'] = {'segments': 5, 'dims': '1x1'}
    # Zlib mirror: stdlib header bits + full decompress roundtrip.
    z = (FIX / 'zlib.bin').read_bytes()
    assert len(z) == 31 and z[:2] == b'\x78\x9c'
    assert (z[0] * 256 + z[1]) % 31 == 0
    assert z[0] & 0x0F == 8 and (z[0] >> 4) == 7 and z[1] & 0x20 == 0
    assert (z[1] >> 6) == 2
    assert zlib.decompress(z) == b'hello fusion world! ' * 8
    report['zlib'] = {'roundtrip': True}
    # VTODO mirror: dict-based re-parse with folding.
    v = ("BEGIN:VTODO\r\nUID:task-1\r\nSUMMARY:Write report\r\nSTATUS:IN-PROGRESS\r\n"
         "DUE:20261022T120000Z\r\nPRIORITY:1\r\nPERCENT-COMPLETE:50\r\nEND:VTODO")
    lines = []
    for ln in v.split('\r\n'):
        if ln.startswith(' ') and lines:
            lines[-1] += ln[1:]
        else:
            lines.append(ln)
    assert lines[0] == 'BEGIN:VTODO' and lines[-1] == 'END:VTODO'
    props = {}
    for ln in lines[1:-1]:
        k, _, val = ln.partition(':')
        props[k.split(';')[0]] = val
    assert props == {'UID': 'task-1', 'SUMMARY': 'Write report', 'STATUS': 'IN-PROGRESS',
                     'DUE': '20261022T120000Z', 'PRIORITY': '1', 'PERCENT-COMPLETE': '50'}
    assert re.fullmatch(r'\d{8}(T\d{6}Z?)?', props['DUE'])
    assert props['STATUS'] in ('NEEDS-ACTION', 'IN-PROGRESS', 'COMPLETED', 'CANCELLED')
    report['vtodo'] = {'props': 6}
    # Pseudonym mirror: JVM interop -- compile Pseudonym + printer, compare to hmac.
    sys.path.insert(0, str(ROOT / 'tools'))
    import bootstrap_host_tools
    env = bootstrap_host_tools.environment(a.tools.expanduser().resolve())
    build = ROOT / 'testing/build/r34'
    build.mkdir(parents=True, exist_ok=True)
    printer = build / 'PseudonymPrint.kt'
    printer.write_text('import dev.fusion.privacy.Pseudonym\n'
                       'fun main() {\n'
                       '  val key = ByteArray(32) { (it * 7 + 1).toByte() }\n'
                       '  println(Pseudonym.derive(key, "crash", "user-1"))\n'
                       '  println(Pseudonym.derive(key, "abuse", "user-1"))\n'
                       '}\n')
    kern = ROOT / 'privacy/src/main/java/dev/fusion/privacy/Pseudonym.kt'
    jar = build / 'r34-pseudonym-print.jar'
    subprocess.run([env['KOTLINC'], str(kern), str(printer), '-include-runtime', '-d', str(jar)],
                   env=env, check=True, capture_output=True, text=True, timeout=600)
    r = subprocess.run([env['JAVA_HOME'] + '/bin/java', '-jar', str(jar)],
                       env=env, check=True, capture_output=True, text=True, timeout=300)
    jvm = r.stdout.split()
    key = bytes((i * 7 + 1) % 256 for i in range(32))
    want_crash = hmac.new(key, b'crash\x00user-1', hashlib.sha256).hexdigest()[:32]
    want_abuse = hmac.new(key, b'abuse\x00user-1', hashlib.sha256).hexdigest()[:32]
    assert jvm == [want_crash, want_abuse], (jvm, want_crash)
    assert want_crash != want_abuse
    report['pseudonym'] = {'jvm_hmac_agree': 2, 'crash': want_crash}
    report['scope'] = 'Independent logic recomputation only; NOT Android/device/service/acceptance evidence.'
    out = ROOT / 'testing/build/r34/reference.json'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(report, indent=2) + '\n')
    print('R34 reference PASS: stats 4 (statistics-module), mime 16 (mimetypes), outline 4, '
          'brackets 4 (stack scan), soap 4 (ElementTree), pipes 10 (split-repair), '
          'jpeg 5 segs 1x1, zlib 31B roundtrip, vtodo 6, pseudonym 2 (JVM=hmac).')


if __name__ == '__main__':
    main()
