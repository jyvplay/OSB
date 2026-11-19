#!/usr/bin/env python3
"""R112 visual-evidence contracts: the static half of "a clipped notice must fail the build".

The R60 run found a real defect that only an emulator frame exposed: scrolling the format dialog to a checkbox
clipped the leading disclosure, yet the test that "checked visibility" passed. R112 repaired that class of defect in
`NativeVisualEvidence.assertFullTextVisible` (reveal the node in its own scroll viewport before judging its fit) and
made the details viewport nameable so a run records its height. Nothing in the JVM lane can compile or run Compose
layout, so these five contracts are checked statically against the sources that implement and call that evidence:

  R112-1 reveal-before-judge   the judging helper reveals a scrolled node first, then asserts.
  R112-2 single judge          no test file computes its own text-fit verdict from clipped bounds.
  R112-3 pinned stays pinned   the safety summary still precedes (is outside) the scrolling details column.
  R112-4 capture tags exist    every dialog tag a capture names is defined by product code, so the run cannot die on
                               a typo only after an 18-minute emulator boot.
  R112-5 bounded viewport      the details column keeps a bounded height instead of growing unboundedly.
  R112-6 clip control survives the deliberate-clip control still requires an AssertionError, and its clip is the
                               node's own viewport rather than an ancestor the repaired helper would scroll.

Each rule is a source predicate, not a claim about rendering. A passing gate means "the evidence code kept its
shape"; it is not a visual pass and not a substitute for the device run.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

DEFAULT_ROOT = Path(__file__).resolve().parents[1]
TEST_DIR = 'app/src/androidTest/java/dev/fusion/shell'
EVIDENCE = f'{TEST_DIR}/NativeVisualEvidence.kt'
EDITOR = 'feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt'
CONTROL = f'{TEST_DIR}/NativeVisualEvidenceDeviceTest.kt'
CONTROL_TEST = 'textFitCheckerAcceptsVisibleTextAndRejectsClippedScrollableText'
HELPER = 'fun assertFullTextVisible'
SAFETY_TAG = 'writer-structured-safety'
SCROLL_TAG = 'writer-structured-scroll'

RULES = {
    'R112-1': 'the text-fit judge reveals a scroll-clipped node before it judges fit',
    'R112-2': 'no androidTest file computes its own text-fit verdict from clipped bounds',
    'R112-3': 'the pinned safety summary is not inside the scrolling details column',
    'R112-4': 'every tag named by a dialog capture is defined by product code',
    'R112-5': 'the details column keeps a bounded height',
    'R112-6': 'the deliberate-clip control survives and clips through the node itself, not an ancestor',
}


def read(root: Path, rel: str) -> str:
    path = root / rel
    if not path.is_file():
        raise SystemExit(f'R112-0: missing source {rel}')
    return path.read_text(encoding='utf-8')


def helper_body(text: str) -> str:
    start = text.find(HELPER)
    if start < 0:
        return ''
    end = len(text)
    for marker in ('\n    fun ', '\n    private fun ', '\n    internal fun '):
        nxt = text.find(marker, start + len(HELPER))
        if 0 <= nxt < end:
            end = nxt
    return text[start:end]


def capture_tags(text: str) -> list[str]:
    """Every string literal a `captureDialogDisplay(...)` call passes after the capture name."""
    tags: list[str] = []
    for match in re.finditer(r'captureDialogDisplay\s*\(', text):
        depth, index = 1, match.end()
        while index < len(text) and depth:
            char = text[index]
            depth += 1 if char == '(' else -1 if char == ')' else 0
            index += 1
        literals = re.findall(r'"((?:[^"\\]|\\.)*)"', text[match.end():index - 1])
        tags.extend(literals[1:])  # literals[0] is the capture filename, not a tag
    return tags


def product_tag(text: str) -> set[str]:
    return set(re.findall(r'testTag\(\s*"([^"]+)"', text))


def violations(root: Path) -> list[str]:
    found: list[str] = []
    evidence = read(root, EVIDENCE)
    editor = read(root, EDITOR)

    body = helper_body(evidence)
    reveal = body.find('hasAnyAncestor(hasScrollAction())')
    scroll = body.find('performScrollTo()')
    judge = body.find('assertTrue(')
    if reveal < 0 or scroll < 0:
        found.append('R112-1: assertFullTextVisible no longer reveals a scrolled node before judging it')
    elif judge >= 0 and not reveal < scroll < judge:
        found.append('R112-1: the reveal must precede the assertion that judges fit')

    tests = sorted((root / TEST_DIR).glob('*.kt'))
    for path in tests:
        if path.name == 'NativeVisualEvidence.kt':
            continue
        if 'boundsInRoot.height' in path.read_text(encoding='utf-8'):
            found.append(f'R112-2: {path.name} judges fit from clipped bounds instead of the shared helper')

    pinned, viewport = editor.find(f'testTag("{SAFETY_TAG}")'), editor.find(f'testTag("{SCROLL_TAG}")')
    if pinned < 0 or viewport < 0:
        found.append('R112-3: the safety summary or the scroll viewport tag is missing from the dialog')
    elif pinned > viewport:
        found.append('R112-3: the safety summary moved inside the scrolling details column')

    defined: set[str] = set()
    for path in (root / 'app/src/main').rglob('*.kt'):
        defined |= product_tag(path.read_text(encoding='utf-8'))
    for path in (root / 'feature').rglob('src/main/**/*.kt'):
        defined |= product_tag(path.read_text(encoding='utf-8'))
    for path in (root / 'core').rglob('src/main/**/*.kt'):
        defined |= product_tag(path.read_text(encoding='utf-8'))
    for path in tests:
        if path.name == 'NativeVisualEvidence.kt':
            continue
        for tag in capture_tags(path.read_text(encoding='utf-8')):
            if tag not in defined:
                found.append(f'R112-4: {path.name} captures tag "{tag}" that no product source defines')

    line = next((line for line in editor.splitlines() if f'testTag("{SCROLL_TAG}")' in line), '')
    if SCROLL_TAG and not re.search(r'heightIn\(\s*max\s*=', line):
        found.append('R112-5: the details column lost its bounded height (heightIn(max = ...))')

    control = root / CONTROL
    text = control.read_text(encoding='utf-8') if control.is_file() else ''
    body = text.split(CONTROL_TEST, 1)[1][:1200] if CONTROL_TEST in text else ''
    if 'AssertionError' not in body:
        found.append('R112-6: the deliberate-clip control no longer requires an AssertionError')
    clipped = next((line for line in text.splitlines() if 'testTag("clipped")' in line), '')
    if 'verticalScroll(' not in clipped:
        found.append('R112-6: the deliberate clip must come from the node\'s own viewport, or the reveal would hide it')
    return found


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=DEFAULT_ROOT)
    parser.add_argument('--list', action='store_true')
    args = parser.parse_args()
    if args.list:
        for rule, text in RULES.items():
            print(f'{rule} {text}')
        return 0
    found = violations(args.root)
    if found:
        for item in found:
            print(item)
        return 1
    print(f'R112_VISUAL_CONTRACTS_OK rules={len(RULES)} test_files={len(list((args.root / TEST_DIR).glob("*.kt")))}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
