#!/usr/bin/env python3
"""Independent differential oracle for AnsiParser (feature/terminal).

Parses ANSI CSI escape sequences and SGR (Select Graphic Rendition)
sub-parameters. Cross-checks against the Kotlin contract in
feature/terminal/src/main/kotlin/dev/fusion/terminal/AnsiParser.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_INPUT = 1 << 14
MAX_PARAMS = 16
MAX_SEQUENCES = 256
ESC = "\x1b"
CSI_RE = re.compile(r"\x1b\[([0-9;]*)([a-zA-Z])")


def parse(input_str: str):
    assert len(input_str) <= MAX_INPUT, f"input exceeds {MAX_INPUT} chars"
    plain = []
    sequences = []
    sgr_list = []
    i = 0
    while i < len(input_str):
        c = input_str[i]
        if c == ESC and i + 1 < len(input_str) and input_str[i + 1] == "[":
            m = CSI_RE.match(input_str, i)
            if not m:
                break
            param_str = m.group(1)
            letter = m.group(2)
            params = [int(p) for p in param_str.split(";") if p] if param_str else []
            assert len(params) <= MAX_PARAMS, "too many CSI params"
            sequences.append((params, letter))
            if letter == "m":
                sgr_list.append(_parse_sgr(params))
            plain_text = None  # CSI doesn't add to plain
            i = m.end()
        elif c == ESC:
            raise ValueError(f"non-CSI escape at position {i}")
        else:
            plain.append(c)
            i += 1
    return ("".join(plain), sequences, sgr_list)


def _parse_sgr(params):
    reset = False
    bold = dim = italic = underline = reverse = False
    fg = None
    bg = None
    for p in params:
        if p == 0:
            reset = True
            bold = dim = italic = underline = reverse = False
            fg = bg = None
        elif p == 1:
            bold = True
        elif p == 2:
            dim = True
        elif p == 3:
            italic = True
        elif p == 4:
            underline = True
        elif p == 7:
            reverse = True
        elif p == 22:
            bold = dim = False
        elif p == 23:
            italic = False
        elif p == 24:
            underline = False
        elif p == 27:
            reverse = False
        elif 30 <= p <= 37:
            fg = p - 30
        elif 90 <= p <= 97:
            fg = p - 90 + 8
        elif 40 <= p <= 47:
            bg = p - 40
        elif 100 <= p <= 107:
            bg = p - 100 + 8
    return (reset, bold, dim, italic, underline, reverse, fg, bg)


def plain_text(input_str: str) -> str:
    return parse(input_str)[0]


class AnsiParserTest(unittest.TestCase):
    def test_plain_text_only(self):
        plain, seqs, sgrs = parse("hello world")
        self.assertEqual(plain, "hello world")
        self.assertEqual(seqs, [])

    def test_sgr_reset(self):
        plain, seqs, sgrs = parse("\x1b[0m")
        self.assertEqual(plain, "")
        self.assertEqual(len(sgrs), 1)
        self.assertTrue(sgrs[0][0])  # reset

    def test_sgr_bold(self):
        plain, seqs, sgrs = parse("\x1b[1m")
        self.assertTrue(sgrs[0][1])  # bold

    def test_sgr_colors(self):
        plain, seqs, sgrs = parse("\x1b[31;42m")
        self.assertEqual(sgrs[0][6], 1)  # fg red
        self.assertEqual(sgrs[0][7], 2)  # bg green

    def test_multiple_sgr(self):
        plain, seqs, sgrs = parse("\x1b[1m\x1b[3m\x1b[0m")
        self.assertEqual(len(sgrs), 3)
        self.assertTrue(sgrs[0][1])  # bold
        self.assertTrue(sgrs[1][3])  # italic (tuple index 3)
        self.assertTrue(sgrs[2][0])  # reset

    def test_plain_text_extraction(self):
        self.assertEqual(plain_text("hello\x1b[1mworld"), "helloworld")

    def test_non_csi_escape_refused(self):
        with self.assertRaises(ValueError):
            parse("\x1bX")

    def test_bright_fg(self):
        plain, seqs, sgrs = parse("\x1b[91m")
        self.assertEqual(sgrs[0][6], 9)  # bright red = 8 + 1 = 9

    def test_bright_bg(self):
        plain, seqs, sgrs = parse("\x1b[106m")
        self.assertEqual(sgrs[0][7], 14)  # bright cyan bg = 6 + 8


if __name__ == "__main__":
    unittest.main()
