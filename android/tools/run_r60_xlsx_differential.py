#!/usr/bin/env python3
"""Differential R22 XLSX projection: original pinned source vs candidate, same immutable corpus.

Requires the R22 host libraries from run_r22_contracts.py and the bootstrapped Python/Kotlin cache.
Reference is a prior OSB commit, not an external or independent XLSX implementation. Expected frames
are independently assembled in Python; host benchmarks do not establish Android worker performance.
"""
import hashlib
import io
import json
import os
from pathlib import Path
import random
import re
import statistics
import struct
import subprocess
import sys
import zipfile
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'testing/build/r60-xlsx'
BASELINE = 'ab4840b27b6d5f9cf19526461551f4e6efe696b0'
PARSER = 'format/ooxml/src/main/kotlin/dev/fusion/ooxml/LiteralXlsxImport.kt'
sys.path.insert(0, str(ROOT / 'tools'))
import bootstrap_host_tools


def sha(data):
    return hashlib.sha256(data).hexdigest()


def frame(cells):
    if not cells:
        return struct.pack('>iii', 0x46435431, 0, 0)
    height = max(r for r, _, _ in cells)
    width = max(c for _, c, _ in cells)
    values = {(r, c): value for r, c, value in cells}
    result = bytearray(struct.pack('>iii', 0x46435431, height, width))
    for r in range(1, height + 1):
        for c in range(1, width + 1):
            text = values.get((r, c), '').encode('utf-8')
            result += struct.pack('>i', len(text)) + text
    return bytes(result)


def column(n):
    s = ''
    while n:
        n, rem = divmod(n - 1, 26)
        s = chr(ord('A') + rem) + s
    return s


def data_xml(cells):
    rows = {}
    for r, c, value in cells:
        assert r > 0 and c > 0
        rows.setdefault(r, []).append((c, value))
    result = ['<sheetData>']
    for r, row in sorted(rows.items()):
        result.append(f'<row r="{r}">')
        for c, value in sorted(row):
            safe = escape(value)
            result.append(f'<c r="{column(c)}{r}" t="inlineStr"><is><t xml:space="preserve">{safe}</t></is></c>')
        result.append('</row>')
    return ''.join(result) + '</sheetData>'


def build_corpus():
    from openpyxl import Workbook
    from datetime import datetime
    wb = Workbook()
    wb.properties.created = wb.properties.modified = datetime(2026, 1, 1)
    wb.active['A1'] = 'base'
    stream = io.BytesIO()
    wb.save(stream)
    with zipfile.ZipFile(io.BytesIO(stream.getvalue())) as z:
        parts = {name: z.read(name) for name in z.namelist()}
    # openpyxl rewrites the modified timestamp at save, so freeze it for a reproducible corpus.
    parts['docProps/core.xml'], changed = re.subn(
        rb'(<dcterms:modified[^>]*>)[^<]+(</dcterms:modified>)',
        rb'\g<1>2026-01-01T00:00:00Z\g<2>', parts['docProps/core.xml'])
    assert changed == 1
    worksheet = parts['xl/worksheets/sheet1.xml']
    pattern = re.compile(rb'<sheetData(?:\s[^>]*)?>.*?</sheetData>|<sheetData\s*/>', re.DOTALL)
    assert len(pattern.findall(worksheet)) == 1

    def book(raw_data):
        edited = dict(parts)
        edited['xl/worksheets/sheet1.xml'] = pattern.sub(lambda _: raw_data.encode('utf-8'), worksheet)
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_DEFLATED) as z:
            for name, raw in edited.items():
                info = zipfile.ZipInfo(name)
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, raw)
        return stream.getvalue()

    cases = []
    def valid(name, cells):
        reference = frame(cells)
        assert len(reference) <= 131072
        cases.append((name, book(data_xml(cells)), 'OK\t' + sha(reference)))

    valid('empty', [])
    valid('one-empty-cell', [(1, 1, '')])
    valid('sparse-far-corner', [(1, 1, 'α'), (127, 17, '=literal'), (256, 32, '😀 & < >')])
    valid('exact-cell-ascii', [(1, 1, 'x' * 4096)])
    valid('exact-cell-utf16', [(1, 1, '😀' * 2048)])
    valid('dense-short', [(r, c, str((r + c) % 10)) for r in range(1, 257) for c in range(1, 33)])
    exact = [(r, c, '' if (r, c) == (1, 1) else 'x' * 12) for r in range(1, 257) for c in range(1, 33)]
    assert len(frame(exact)) == 131072
    valid('exact-frame-budget', exact)
    over = [(r, c, 'x' if (r, c) == (1, 1) else text) for r, c, text in exact]
    assert len(frame(over)) == 131073
    cases.append(('over-frame-budget', book(data_xml(over)), 'REFUSED'))

    rng = random.Random(60022)
    choices = ['', 'literal', '=1+2', 'α😀', 'a\nb\tc', ' & < > ', '_x0_']
    for i in range(120):
        coords = set()
        while len(coords) < 1 + i % 23:
            coords.add((rng.randrange(1, 257), rng.randrange(1, 33)))
        cells = [(r, c, rng.choice(choices) + str(i)) for r, c in sorted(coords)]
        valid(f'sparse-{i:03}', cells)

    base = data_xml([(1, 1, 'alpha'), (1, 2, 'beta'), (2, 1, 'omega')])
    first = '<c r="A1" t="inlineStr"><is><t xml:space="preserve">alpha</t></is></c>'
    second = '<c r="B1" t="inlineStr"><is><t xml:space="preserve">beta</t></is></c>'
    assert first + second in base
    mutations = {
        'duplicate-cell': base.replace(first + second, first + first + second),
        'out-of-order-columns': base.replace(first + second, second + first),
        'duplicate-row': base.replace('</sheetData>', '<row r="2"/></sheetData>'),
        'out-of-order-rows': base.replace('<row r="2">', '<row r="1">'),
        'row-cell-disagree': base.replace('r="B1"', 'r="B2"'),
        'row-257': data_xml([(257, 1, 'bad')]),
        'column-33': data_xml([(1, 33, 'bad')]),
        'lowercase-coordinate': base.replace('r="A1"', 'r="a1"'),
        'invalid-cell-type': base.replace('t="inlineStr"', 't="e"', 1),
        'cell-style': base.replace('r="A1"', 'r="A1" s="1"'),
        'formula': base.replace('<is><t xml:space="preserve">alpha</t></is>', '<f>1+1</f><v>2</v>'),
        'escaped-token': base.replace('alpha', '_x0041_'),
        'external-entity': base.replace('alpha', '&unknown;'),
        'over-cell-ascii': data_xml([(1, 1, 'x' * 4097)]),
        'over-cell-utf16': data_xml([(1, 1, '😀' * 2049)]),
        'nested-cell': base.replace('alpha</t>', 'alpha<c/></t>'),
        'hidden-row': base.replace('<row r="1">', '<row r="1" hidden="1">'),
        'unsupported-column-zero': base.replace('r="A1"', 'r="A0"'),
        'boolean-outside-domain': base.replace(first, '<c r="A1" t="b"><v>2</v></c>'),
        'shared-string-out-of-range': base.replace(first, '<c r="A1" t="s"><v>9999</v></c>'),
        'numeric-malformed': base.replace(first, '<c r="A1" t="n"><v>1e99999</v></c>'),
    }
    for name, xml in mutations.items():
        assert xml != base, name
        cases.append((name, book(xml), 'REFUSED'))
    cases.append(('over-input-budget', b'x' * 2097153, 'REFUSED'))
    return cases


def verify_results(cases, baseline, candidate):
    if len(cases) != len(baseline) or len(cases) != len(candidate):
        raise AssertionError('missing or extra results')
    for (name, _, expected), old, new in zip(cases, baseline, candidate):
        if old != expected:
            raise AssertionError(f'{name}: baseline {old!r} differs from independent expected {expected!r}')
        if new != expected:
            raise AssertionError(f'{name}: candidate {new!r} differs from independent expected {expected!r}')
    return len(cases)


def main():
    env = bootstrap_host_tools.environment(Path.home() / '.cache/fusion-host-r23')
    OUT.mkdir(parents=True, exist_ok=True)
    case_dir = OUT / 'cases'
    case_dir.mkdir(exist_ok=True)
    libraries = [ROOT / 'testing/build/r22' / (name + '.jar') for name in
                 ('StrictZip', 'BoundedXml', 'LiteralWorkbook', 'CsvTable')]
    for path in libraries:
        if not path.is_file():
            raise SystemExit('First run python3 tools/run_r22_contracts.py with the host tool environment')
    source = subprocess.run(['git', 'show', f'{BASELINE}:android/{PARSER}'], cwd=ROOT,
                            capture_output=True, check=True).stdout
    old = OUT / 'BaselineLiteralXlsxImport.kt'
    old.write_bytes(source)
    baseline = OUT / 'baseline.jar'
    candidate = OUT / 'candidate.jar'
    probe = OUT / 'probe.jar'
    cp = os.pathsep.join(map(str, libraries))
    for src, jar in ((old, baseline), (ROOT / PARSER, candidate)):
        jar.unlink(missing_ok=True)
        subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-classpath', cp, str(src), '-d', str(jar)], check=True, env=env)
    probe.unlink(missing_ok=True)
    subprocess.run([env['KOTLINC'], '-jvm-target', '17', '-classpath', cp + os.pathsep + str(candidate),
                    '-include-runtime', str(ROOT / 'testing/src/r60xlsx/kotlin/dev/fusion/testing/R60XlsxProbe.kt'),
                    '-d', str(probe)], check=True, env=env)
    java = str(Path(env['JAVA_HOME']) / 'bin/java')
    def execute(jar, args=(), stdin=''):
        classpath = os.pathsep.join(map(str, [probe, *libraries, jar]))
        result = subprocess.run([java, '-Dfile.encoding=UTF-8', '-cp', classpath,
                                 'dev.fusion.testing.R60XlsxProbe', *args], input=stdin, capture_output=True,
                                text=True, check=True, env=env, timeout=240)
        return result.stdout.splitlines()

    cases = build_corpus()
    paths = []
    for i, (_, raw, _) in enumerate(cases):
        path = case_dir / f'{i:04}.xlsx'
        path.write_bytes(raw)
        paths.append(path)
    stdin = ''.join(str(path) + '\n' for path in paths)
    baseline_results = execute(baseline, stdin=stdin)
    candidate_results = execute(candidate, stdin=stdin)
    count = verify_results(cases, baseline_results, candidate_results)

    # Alternate independently started JVMs. Warmup and fixed measured runs are deliberately the same.
    timings = {}
    for name in ('dense-short', 'exact-frame-budget'):
        index = next(i for i, case in enumerate(cases) if case[0] == name)
        for label, jar in (('baseline', baseline), ('candidate', candidate)):
            result = execute(jar, ['--bench', str(paths[index]), '3', '7'])
            if len(result) != 1 or not result[0].startswith('BENCH\t'):
                raise AssertionError('missing benchmark result')
            timings[name + '-' + label] = [int(v) / 1_000_000 for v in result[0].split('\t')[1].split(',')]
    report = {'baseline_revision': BASELINE, 'baseline_source_sha256': sha(source),
              'candidate_source_sha256': sha((ROOT / PARSER).read_bytes()),
              'cases': count, 'accepted': sum(expected.startswith('OK') for _, _, expected in cases),
              'refused': sum(expected == 'REFUSED' for _, _, expected in cases),
              'corpus_sha256': sha(b''.join(hashlib.sha256(raw).digest() for _, raw, _ in cases)),
              'timings_ms': timings, 'median_ms': {k: round(statistics.median(v), 3) for k, v in timings.items()},
              'limits': 'Host timing only; not Android, independent Office compatibility, or proof of watchdog causation.'}
    (OUT / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
