#!/usr/bin/env python3
"""Read Kotlin source as code, not as prose: strip comments, and optionally the literals too.

The three static gates (R112 visual contracts, R113 include closure, R115 office inventory) all read Kotlin text and
all need "the code, not the prose". The obvious implementation — regex the block comments out, then the line comments,
then the strings — is wrong in this repository: a string such as `"content://provider/document/*"` contains both
comment markers, so the block-comment pass deletes everything up to the next `*/`, and a whole test method disappears.

That failure was observed, not hypothesised: it silently removed `CopyContractDeviceTest.selectedResultRetainsItsOwn
Identity` from R115's inventory. Comments must therefore be recognised only where a scanner can tell them apart from
string and character literals, which means one left-to-right pass.

Two views come out of that pass:

  code_only(text)        comments gone and literals gone — for finding annotations and declarations.
  strip_comments(text)   comments gone, literals intact   — for reading the arguments of a call site.

Both preserve every newline of the input, so a line number in the view is a line number in the file.
"""
from __future__ import annotations


def _skip_raw(text: str, index: int) -> int:
    end = text.find('"""', index + 3)
    return len(text) if end < 0 else end + 3


def _skip_quoted(text: str, index: int, quote: str) -> int:
    cursor = index + 1
    while cursor < len(text):
        if text[cursor] == '\\':
            cursor += 2
            continue
        if text[cursor] == quote:
            return cursor + 1
        cursor += 1
    return len(text)


def _blank(fragment: str) -> str:
    """Same length, same newlines, no content — so the view keeps every line and column of the file."""
    return ''.join('\n' if character == '\n' else ' ' for character in fragment)


def _scan(text: str, keep_literals: bool) -> str:
    pieces: list[str] = []
    index, length = 0, len(text)
    while index < length:
        if text.startswith('"""', index):
            end = _skip_raw(text, index)
        elif text[index] == '"':
            end = _skip_quoted(text, index, '"')
        elif text[index] == "'":
            end = _skip_quoted(text, index, "'")
        elif text.startswith('//', index):
            newline = text.find('\n', index)
            end = length if newline < 0 else newline
        elif text.startswith('/*', index):
            close = text.find('*/', index + 2)
            end = length if close < 0 else close + 2
        else:
            pieces.append(text[index])
            index += 1
            continue
        fragment = text[index:end]
        literal = fragment[0] in '"\''
        pieces.append(fragment if keep_literals and literal else _blank(fragment))
        index = end
    return ''.join(pieces)


def code_only(text: str) -> str:
    """Kotlin source with comments and string/character literals blanked out."""
    return _scan(text, keep_literals=False)


def strip_comments(text: str) -> str:
    """Kotlin source with comments blanked out and literals left exactly as written."""
    return _scan(text, keep_literals=True)
