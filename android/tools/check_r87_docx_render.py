#!/usr/bin/env python3
"""R87 independent renderer lane: read our own `.docx` packages through a third-party HTML renderer.

The renderer is `docx-preview` (pinned in `tools/r87node/package.json`) running under Node with jsdom.
It is not Word: it has no pagination, no font metrics and no tab-stop arithmetic. What it can prove is
that an implementation which never saw our writer turns the declared bytes into the structure we meant:
a shaded paragraph comes out with the declared `background-color`, an indented paragraph comes out with
`margin-inline`/`text-indent` in points derived from our twips, a heading comes out with a heading class,
and a `w:tab` comes out as a tab span.

Three artifact directories are checked, each against its own intent table:

* R87 `shade.tsv`  — paragraph shading and the tab-character path (`tools/check_r87_writer_shade.py`
  covers the bytes; this covers what a renderer does with them);
* R84 `indent.tsv` — the R84 indent slice, whose twips must appear as the matching point values;
* R86 `tabs.tsv`   — enumeration only: the renderer must see exactly the declared body paragraphs.

Host-only. A rendering engine that is not Word is not Word rendering evidence, and this checker says so
in its own output.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / 'tools/r87node/render_docx.mjs'
NODE_MODULES = ROOT / 'tools/r87node/node_modules'


class Fail(Exception):
    pass


def require(ok: bool, message: str) -> None:
    if not ok:
        raise Fail(message)


def intent_rows(path: Path, fields: int) -> dict[str, list[list[str]]]:
    require(path.is_file(), f'missing intent table {path}')
    table: dict[str, list[list[str]]] = {}
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), start=1):
        if not line.strip():
            continue
        parts = line.split('\t')
        require(len(parts) == fields, f'{path.name} line {number}: {len(parts)} fields, expected {fields}')
        table.setdefault(parts[0], []).append(parts)
    require(table, f'{path.name} is empty')
    return table


def optional_int(text: str) -> int | None:
    return None if text == '-' else int(text)


def normalise(text: str) -> str:
    return re.sub(r'\s+', ' ', text).strip()


def unescape(text: str) -> str:
    out: list[str] = []
    index = 0
    while index < len(text):
        char = text[index]
        if char == '\\' and index + 1 < len(text):
            token = text[index + 1]
            out.append({'n': '\n', 't': '\t', '\\': '\\'}.get(token, token))
            index += 2
            continue
        out.append(char)
        index += 1
    return ''.join(out)


def css_rgb(fill: str) -> str:
    red, green, blue = (int(fill[i:i + 2], 16) for i in (0, 2, 4))
    return f'rgb({red}, {green}, {blue})'


def css_points(style: dict[str, str], key: str) -> float | None:
    raw = style.get(key)
    if raw is None:
        return None
    match = re.fullmatch(r'(-?\d+(?:\.\d+)?)pt', raw.strip())
    require(match is not None, f'unexpected {key} value {raw!r}')
    return float(match.group(1))


def inline_margins(style: dict[str, str]) -> tuple[float | None, float | None]:
    """docx-preview writes `margin-inline: <left>pt <right>pt` when both sides are set, the one-value
    shorthand when they are equal, and `margin-inline-start`/`-end` when only one side is."""
    raw = style.get('margin-inline')
    if raw:
        parts = raw.split()
        require(1 <= len(parts) <= 2, f'unexpected margin-inline {raw!r}')
        values = []
        for part in parts:
            match = re.fullmatch(r'(-?\d+(?:\.\d+)?)pt', part)
            require(match is not None, f'unexpected margin-inline part {part!r}')
            values.append(float(match.group(1)))
        return values[0], values[-1]
    left = css_points(style, 'margin-inline-start')
    if left is None:
        left = css_points(style, 'margin-left')
    right = css_points(style, 'margin-inline-end')
    if right is None:
        right = css_points(style, 'margin-right')
    return left, right


def compare_points(actual: float | None, twips: int | None, label: str, where: str) -> None:
    if twips is None:
        require(actual is None, f'{where}: {label} rendered as {actual} but nothing was declared')
        return
    require(actual is not None, f'{where}: {label} declared {twips} twips but the renderer produced none')
    expected = twips / 20.0
    require(abs(actual - expected) < 0.01,
            f'{where}: {label} rendered {actual}pt, expected {expected}pt from {twips} twips')


def render(directory: Path, node: str, workdir: Path, name: str) -> dict:
    output = workdir / f'{name}.json'
    subprocess.run([node, str(RENDERER), str(directory), str(output)], check=True,
                   text=True, capture_output=True)
    payload = json.loads(output.read_text())
    require(not payload['errors'], f'{name}: renderer failed on {payload["errors"]}')
    return payload


def lookup(payload: dict, name: str) -> dict:
    files = {entry['name']: entry for entry in payload['files']}
    require(name in files, f'{name}: the renderer produced no document for this package')
    return files[name]


def check_r87(payload: dict, artifacts: Path) -> tuple[int, int]:
    rows = intent_rows(artifacts / 'shade.tsv', 6)
    require(sorted(rows) == sorted(entry['name'][:-5] for entry in payload['files']),
            'shade.tsv and the rendered packages disagree about which files exist')
    paragraphs = 0
    shaded = 0
    for name, entries in rows.items():
        document = lookup(payload, f'{name}.docx')
        rendered = document['paragraphs']
        require(len(rendered) == len(entries),
                f'{name}: renderer produced {len(rendered)} article paragraphs, declared {len(entries)}')
        for entry, paragraph in zip(entries, rendered):
            where = f'{name} paragraph {entry[1]}'
            declared_text = normalise(unescape(entry[5]))
            require(paragraph['text'] == declared_text,
                    f'{where}: rendered text {paragraph["text"]!r} != declared {declared_text!r}')
            fill = entry[3]
            background = paragraph['style'].get('background-color')
            if fill == '-':
                require(background is None, f'{where}: undeclared fill rendered as {background}')
            else:
                require(background == css_rgb(fill),
                        f'{where}: fill {fill} rendered as {background}, expected {css_rgb(fill)}')
                shaded += 1
            heading = 'docx_heading' in ' '.join(paragraph['classes'])
            require(heading == (entry[2] == 'heading'),
                    f'{where}: kind {entry[2]} rendered with classes {paragraph["classes"]}')
            tabs = unescape(entry[5]).count('\t')
            require(paragraph['tabSpans'] == tabs,
                    f'{where}: {tabs} tab character(s) but the renderer produced {paragraph["tabSpans"]} tab span(s)')
            paragraphs += 1
    return paragraphs, shaded


def check_r84(payload: dict, artifacts: Path) -> tuple[int, int]:
    rows = intent_rows(artifacts / 'indent.tsv', 8)
    paragraphs = 0
    asserted = 0
    for name, entries in rows.items():
        document = lookup(payload, f'{name}.docx')
        rendered = document['paragraphs']
        require(len(rendered) == len(entries),
                f'{name}: renderer produced {len(rendered)} article paragraphs, declared {len(entries)}')
        for entry, paragraph in zip(entries, rendered):
            where = f'{name} paragraph {entry[1]}'
            left, right = inline_margins(paragraph['style'])
            first_line = css_points(paragraph['style'], 'text-indent')
            declared_left = optional_int(entry[3])
            declared_right = optional_int(entry[4])
            declared_first = optional_int(entry[5])
            compare_points(left, declared_left, 'left indent', where)
            compare_points(right, declared_right, 'right indent', where)
            compare_points(first_line, declared_first, 'first line', where)
            if any(value is not None for value in (declared_left, declared_right, declared_first)):
                asserted += 1
            paragraphs += 1
    return paragraphs, asserted


def check_r86(payload: dict, artifacts: Path) -> int:
    rows = intent_rows(artifacts / 'tabs.tsv', 6)
    paragraphs = 0
    for name, entries in rows.items():
        document = lookup(payload, f'{name}.docx')
        require(len(document['paragraphs']) == len(entries),
                f'{name}: renderer produced {len(document["paragraphs"])} paragraphs, declared {len(entries)}')
        for paragraph in document['paragraphs']:
            # The R86 fixtures declare tab stops but no tab characters, so the renderer must see none.
            require(paragraph['tabSpans'] == 0,
                    f'{name}: a tab span appeared in a document that declares no tab character')
        paragraphs += len(entries)
    return paragraphs


def verify(args: argparse.Namespace) -> dict:
    workdir = Path(tempfile.mkdtemp(prefix='r87-render-'))
    try:
        node = args.node or shutil.which('node')
        require(node is not None, 'node is not on PATH; the renderer lane needs Node 18+')
        if args.renderer_json:
            payloads = {name: json.loads(Path(path).read_text()) for name, path in args.renderer_json}
            require(sorted(payloads) == sorted({k for k in ('r87', 'r84', 'r86') if getattr(args, k)}),
                    'every artifact directory needs its renderer JSON')
        else:
            require(NODE_MODULES.is_dir(),
                    f'missing {NODE_MODULES}; run `npm ci --prefix tools/r87node` first')
            payloads = {k: render(getattr(args, k), node, workdir, k)
                        for k in ('r87', 'r84', 'r86') if getattr(args, k)}
        totals = {'r87_paragraphs': 0, 'r87_shaded': 0, 'r84_paragraphs': 0, 'r84_asserted': 0,
                  'r86_paragraphs': 0, 'packages': 0}
        if args.r87:
            totals['r87_paragraphs'], totals['r87_shaded'] = check_r87(payloads['r87'], args.r87)
            totals['packages'] += len(payloads['r87']['files'])
        if args.r84:
            totals['r84_paragraphs'], totals['r84_asserted'] = check_r84(payloads['r84'], args.r84)
            totals['packages'] += len(payloads['r84']['files'])
        if args.r86:
            totals['r86_paragraphs'] = check_r86(payloads['r86'], args.r86)
            totals['packages'] += len(payloads['r86']['files'])
        totals['renderer'] = payloads[next(iter(payloads))]['renderer']
        return totals
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--r87', type=Path, help='R87 shade artifacts (shade.tsv)')
    parser.add_argument('--r84', type=Path, help='R84 indent artifacts (indent.tsv)')
    parser.add_argument('--r86', type=Path, help='R86 tab artifacts (tabs.tsv)')
    parser.add_argument('--node', help='node executable (default: PATH lookup)')
    parser.add_argument('--renderer-json', nargs=2, action='append', metavar=('LANE', 'PATH'),
                        help='use a precomputed renderer dump for LANE instead of running Node')
    args = parser.parse_args(argv)
    if not any((args.r87, args.r84, args.r86)):
        parser.error('at least one artifact directory is required')
    try:
        totals = verify(args)
    except Fail as failure:
        print(f'R87 render verification FAILED: {failure}')
        return 1
    except subprocess.CalledProcessError as failure:
        print(f'R87 render verification FAILED: renderer exited {failure.returncode}: '
              f'{(failure.stderr or "").strip()[-400:]}')
        return 1
    parts = []
    if args.r87:
        parts.append(f'{totals["r87_paragraphs"]} shaded-path paragraphs '
                     f'({totals["r87_shaded"]} with a fill)')
    if args.r84:
        parts.append(f'{totals["r84_paragraphs"]} R84 paragraphs '
                     f'({totals["r84_asserted"]} with a declared indent)')
    if args.r86:
        parts.append(f'{totals["r86_paragraphs"]} R86 paragraphs enumerated')
    print(f'R87 {totals["renderer"]} rendered {totals["packages"]} packages: ' + '; '.join(parts) + '. '
          'A third-party HTML renderer is not Word: no pagination, font metric or tab-stop arithmetic '
          'is claimed.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
