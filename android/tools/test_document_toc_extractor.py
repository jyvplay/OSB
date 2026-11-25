#!/usr/bin/env python3
"""Differential oracle for DocumentTocExtractor (feature/document)."""
import re
import unittest


def extract_toc(md: str):
    out, seen, in_fence = [], {}, False
    for line in md.splitlines():
        t = line.strip()
        if t.startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        m = re.match(r"^(#{1,6})\s+(.+)$", t)
        if not m:
            continue
        lvl, title = len(m.group(1)), m.group(2).strip()
        base = re.sub(r"\s+", "-", re.sub(r"[^a-z0-9\s-]", "", title.lower()).strip())
        c = seen.get(base, 0)
        seen[base] = c + 1
        slug = base if c == 0 else f"{base}-{c}"
        out.append((lvl, title, slug))
    return out


class DocumentTocExtractorTest(unittest.TestCase):
    def test_toc(self):
        md = "# Intro\n```\n# Not a heading\n```\n## Intro\n"
        self.assertEqual(extract_toc(md), [(1, "Intro", "intro"), (2, "Intro", "intro-1")])


if __name__ == "__main__":
    unittest.main()
