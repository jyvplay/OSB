#!/usr/bin/env python3
"""R87/R88 independent renderer lane: read our own `.docx` packages through a third-party HTML renderer.

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
* R86 `tabs.tsv`   — enumeration only: the renderer must see exactly the declared body paragraphs;
* R88 `borders.tsv` — paragraph borders: every `w:pBdr` edge on the four sides must come out as the CSS
  border width (eighths of a point), style token and colour that the intent table declares. `w:between`
  and `w:bar` are counted as *not rendered* by docx-preview rather than waved through.

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


CSS_STYLE = {'single': 'solid', 'thick': 'solid', 'double': 'double', 'dashed': 'dashed',
             'dotted': 'dotted', 'nil': 'none'}
CSS_NAMED = {'black': (0, 0, 0), 'white': (255, 255, 255), 'red': (255, 0, 0),
             'green': (0, 128, 0), 'blue': (0, 0, 255), 'gray': (128, 128, 128),
             'grey': (128, 128, 128), 'yellow': (255, 255, 0)}
SIDES = ['top', 'right', 'bottom', 'left']
SIDE_PROPERTIES = ('width', 'style', 'color')


def css_tokens(raw: str) -> list[str]:
    """Split a CSS value on whitespace, keeping `rgb(1, 2, 3)` in one piece."""
    tokens: list[str] = []
    current = ''
    depth = 0
    for char in raw:
        if char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
        if char.isspace() and depth == 0:
            if current:
                tokens.append(current)
                current = ''
            continue
        current += char
    if current:
        tokens.append(current)
    return tokens


def css_colour(raw: str) -> tuple[int, int, int] | None:
    token = raw.strip().lower()
    if token.startswith('rgb(') and token.endswith(')'):
        parts = [part.strip() for part in token[4:-1].split(',')]
        if len(parts) >= 3 and all(part.isdigit() for part in parts[:3]):
            return (int(parts[0]), int(parts[1]), int(parts[2]))
        return None
    if token.startswith('#') and len(token) == 7:
        return (int(token[1:3], 16), int(token[3:5], 16), int(token[5:7], 16))
    return CSS_NAMED.get(token)


def css_width(raw: str) -> float | None:
    match = re.fullmatch(r'(-?\d+(?:\.\d+)?)pt', raw.strip())
    return float(match.group(1)) if match else None


def expand(values: list[str]) -> list[str | None]:
    """CSS 1-to-4 value expansion in top/right/bottom/left order."""
    if len(values) == 1:
        values = values * 4
    elif len(values) == 2:
        values = [values[0], values[1], values[0], values[1]]
    elif len(values) == 3:
        values = [values[0], values[1], values[2], values[1]]
    if len(values) != 4:
        return [None] * 4
    return list(values)


def side_borders(style: dict) -> dict[str, dict[str, object]]:
    """Reconstruct the four side boxes from shorthand, per-side and `border-<side>` declarations."""
    sides: dict[str, dict[str, object]] = {side: {'width': None, 'style': None, 'color': None}
                                           for side in SIDES}
    for property_name in ('border-width', 'border-style', 'border-color'):
        raw = style.get(property_name)
        if not raw:
            continue
        for side, value in zip(SIDES, expand(css_tokens(raw))):
            if value is not None:
                sides[side][property_name.split('-')[1]] = value
    for side in SIDES:
        shorthand = style.get(f'border-{side}')
        if shorthand:
            for token in css_tokens(shorthand):
                if css_width(token) is not None:
                    sides[side]['width'] = token
                elif css_colour(token) is not None:
                    sides[side]['color'] = token
                else:
                    sides[side]['style'] = token
        for property_name in SIDE_PROPERTIES:
            value = style.get(f'border-{side}-{property_name}')
            if value:
                sides[side][property_name] = value
    return sides


def compare_edge(declared: dict, rendered: dict[str, object], where: str, side: str) -> None:
    width = rendered['width']
    expected_width = declared['size'] / 8.0
    require(width is not None, f'{where}: the {side} border has no width but {declared["size"]}/8 pt was declared')
    actual_width = css_width(str(width))
    require(actual_width is not None and abs(actual_width - expected_width) < 0.01,
            f'{where}: the {side} border width is {width}, expected {expected_width}pt')
    expected_style = CSS_STYLE[declared['style']]
    require(rendered['style'] == expected_style,
            f'{where}: the {side} border style is {rendered["style"]}, expected {expected_style}')
    colour = rendered['color']
    require(colour is not None, f'{where}: the {side} border has no colour but one was declared')
    actual = css_colour(str(colour))
    want = (0, 0, 0) if declared['color'] == 'auto' else (
        int(declared['color'][0:2], 16), int(declared['color'][2:4], 16), int(declared['color'][4:6], 16))
    require(actual == want, f'{where}: the {side} border colour is {colour}, expected rgb{want}')


def check_r88(payload: dict, artifacts: Path) -> tuple[int, int, int, int]:
    rows = intent_rows(artifacts / 'borders.tsv', 6)
    require(sorted(rows) == sorted(entry['name'][:-5] for entry in payload['files']),
            'borders.tsv and the rendered packages disagree about which files exist')
    paragraphs = 0
    bordered = 0
    sides = 0
    between = 0
    for name, entries in rows.items():
        document = lookup(payload, f'{name}.docx')
        rendered = document['paragraphs']
        require(len(rendered) == len(entries),
                f'{name}: renderer produced {len(rendered)} paragraphs, declared {len(entries)}')
        for entry, paragraph in zip(entries, rendered):
            where = f'{name} paragraph {entry[1]}'
            heading = 'docx_heading' in ' '.join(paragraph['classes'])
            require(heading == (entry[2] == 'heading'),
                    f'{where}: kind {entry[2]} rendered with classes {paragraph["classes"]}')
            box = side_borders(paragraph['style'])
            declared = {}
            if entry[3] != '-':
                for spec in entry[3].split(';'):
                    edge, style_token, size, space, color = spec.split(':')
                    declared[edge] = {'style': style_token, 'size': int(size), 'space': int(space),
                                      'color': color}
            if not declared:
                for side in SIDES:
                    require(box[side]['style'] in (None, 'none'),
                            f'{where}: undeclared {side} border rendered as {box[side]}')
                paragraphs += 1
                continue
            for side in SIDES:
                if side in declared:
                    compare_edge(declared[side], box[side], where, side)
                    sides += 1
                else:
                    require(box[side]['style'] in (None, 'none'),
                            f'{where}: undeclared {side} border rendered as {box[side]}')
            between += sum(1 for edge in ('between', 'bar') if edge in declared)
            bordered += 1
            paragraphs += 1
    return paragraphs, bordered, sides, between


def verify(args: argparse.Namespace) -> dict:
    workdir = Path(tempfile.mkdtemp(prefix='r87-render-'))
    try:
        node = args.node or shutil.which('node')
        require(node is not None, 'node is not on PATH; the renderer lane needs Node 18+')
        lanes = {name: getattr(args, name, None) for name in ('r87', 'r84', 'r86', 'r88')}
        if args.renderer_json:
            payloads = {name: json.loads(Path(path).read_text()) for name, path in args.renderer_json}
            require(sorted(payloads) == sorted(name for name, value in lanes.items() if value),
                    'every artifact directory needs its renderer JSON')
        else:
            require(NODE_MODULES.is_dir(),
                    f'missing {NODE_MODULES}; run `npm ci --prefix tools/r87node` first')
            payloads = {k: render(value, node, workdir, k)
                        for k, value in lanes.items() if value}
        totals = {'r87_paragraphs': 0, 'r87_shaded': 0, 'r84_paragraphs': 0, 'r84_asserted': 0,
                  'r86_paragraphs': 0, 'r88_paragraphs': 0, 'r88_bordered': 0, 'r88_sides': 0,
                  'r88_unrendered': 0, 'packages': 0}
        if lanes['r87']:
            totals['r87_paragraphs'], totals['r87_shaded'] = check_r87(payloads['r87'], args.r87)
            totals['packages'] += len(payloads['r87']['files'])
        if lanes['r84']:
            totals['r84_paragraphs'], totals['r84_asserted'] = check_r84(payloads['r84'], args.r84)
            totals['packages'] += len(payloads['r84']['files'])
        if lanes['r86']:
            totals['r86_paragraphs'] = check_r86(payloads['r86'], args.r86)
            totals['packages'] += len(payloads['r86']['files'])
        if lanes['r88']:
            totals['r88_paragraphs'], totals['r88_bordered'], totals['r88_sides'], \
                totals['r88_unrendered'] = check_r88(payloads['r88'], lanes['r88'])
            totals['packages'] += len(payloads['r88']['files'])
        totals['renderer'] = payloads[next(iter(payloads))]['renderer']
        return totals
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--r87', type=Path, help='R87 shade artifacts (shade.tsv)')
    parser.add_argument('--r84', type=Path, help='R84 indent artifacts (indent.tsv)')
    parser.add_argument('--r86', type=Path, help='R86 tab artifacts (tabs.tsv)')
    parser.add_argument('--r88', type=Path, help='R88 border artifacts (borders.tsv)')
    parser.add_argument('--node', help='node executable (default: PATH lookup)')
    parser.add_argument('--renderer-json', nargs=2, action='append', metavar=('LANE', 'PATH'),
                        help='use a precomputed renderer dump for LANE instead of running Node')
    args = parser.parse_args(argv)
    if not any((args.r87, args.r84, args.r86, args.r88)):
        parser.error('at least one artifact directory is required')
    try:
        totals = verify(args)
    except Fail as failure:
        print(f'R87/R88 render verification FAILED: {failure}')
        return 1
    except subprocess.CalledProcessError as failure:
        print(f'R87/R88 render verification FAILED: renderer exited {failure.returncode}: '
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
    if args.r88:
        parts.append(f'{totals["r88_paragraphs"]} R88 paragraphs ({totals["r88_bordered"]} bordered, '
                     f'{totals["r88_sides"]} sides CSS-verified, {totals["r88_unrendered"]} '
                     'between/bar edges that docx-preview does not render)')
    print(f'R87/R88 {totals["renderer"]} rendered {totals["packages"]} packages: ' + '; '.join(parts) + '. '
          'A third-party HTML renderer is not Word: no pagination, font metric or tab-stop arithmetic '
          'is claimed.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
