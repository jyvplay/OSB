#!/usr/bin/env python3
"""Differential oracle for TerminalVt100Parser (feature/terminal)."""
import unittest


class TerminalVt100Parser:
    def __init__(self, rows=24, cols=80):
        self.rows, self.cols = rows, cols
        self.grid = [[" "] * cols for _ in range(rows)]
        self.r, self.c = 0, 0

    def feed(self, s: str):
        i = 0
        while i < len(s):
            ch = s[i]
            if ch == "\x1b" and i + 1 < len(s) and s[i + 1] == "[":
                i += 2
                st = i
                while i < len(s) and not ("@" <= s[i] <= "~"):
                    i += 1
                if i < len(s):
                    self._csi(s[st:i], s[i])
            elif ch == "\r":
                self.c = 0
            elif ch == "\n":
                self.c = 0
                if self.r < self.rows - 1:
                    self.r += 1
            elif ch >= " ":
                self.grid[self.r][self.c] = ch
                if self.c < self.cols - 1:
                    self.c += 1
            i += 1

    def _csi(self, params: str, cmd: str):
        parts = [int(p) if p.isdigit() else 0 for p in params.split(";")] if params else []
        if cmd in ("H", "f"):
            self.r = max(1, min(self.rows, parts[0] if len(parts) > 0 else 1)) - 1
            self.c = max(1, min(self.cols, parts[1] if len(parts) > 1 else 1)) - 1

    def line(self, r: int) -> str:
        return "".join(self.grid[r]).rstrip()


class TerminalVt100ParserTest(unittest.TestCase):
    def test_csi_cursor(self):
        t = TerminalVt100Parser(5, 20)
        t.feed("hi\x1b[2;4HOSB")
        self.assertEqual(t.line(0), "hi")
        self.assertEqual(t.line(1), "   OSB")


if __name__ == "__main__":
    unittest.main()
