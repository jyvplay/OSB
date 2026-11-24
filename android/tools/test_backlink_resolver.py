#!/usr/bin/env python3
"""Independent differential oracle for BacklinkResolver (feature/notes).

Extracts [[backlink target|display text]] and [[bare target]] references
from markdown-like text, normalizes targets to note ids (lowercase, spaces
to underscores). Cross-checks against the Kotlin contract in
feature/notes/src/main/kotlin/dev/fusion/notes/BacklinkResolver.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_INPUT = 1 << 14
MAX_LINKS = 512
MAX_TARGET_LEN = 256
MAX_DISPLAY_LEN = 512
LINK_RE = re.compile(r"\[\[([^\|\]]+)(\|([^\]]*))?\]\]")


def resolve(input_str: str):
    assert len(input_str) <= MAX_INPUT, f"input exceeds {MAX_INPUT} chars"
    links = []
    search_from = 0
    while True:
        found = LINK_RE.search(input_str, search_from)
        if not found:
            break
        if len(links) >= MAX_LINKS:
            break
        raw_target = found.group(1)
        display = found.group(3)
        if (raw_target and len(raw_target) <= MAX_TARGET_LEN and
                _valid_target(raw_target)):
            note_id = raw_target.lower().replace(" ", "_")[:MAX_TARGET_LEN]
            disp = display if (display and len(display) <= MAX_DISPLAY_LEN) else None
            links.append((raw_target, disp, note_id))
        search_from = found.end()
    return links


def note_ids(input_str: str):
    return {link[2] for link in resolve(input_str)}


def _valid_target(s: str) -> bool:
    return all(
        c in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-|"
        for c in s
    )


class BacklinkResolverTest(unittest.TestCase):
    def test_bare_link(self):
        links = resolve("See [[Meeting Notes]]")
        self.assertEqual(len(links), 1)
        self.assertEqual(links[0][0], "Meeting Notes")
        self.assertIsNone(links[0][1])
        self.assertEqual(links[0][2], "meeting_notes")

    def test_link_with_display(self):
        links = resolve("See [[Project Plan|Plan]]")
        self.assertEqual(len(links), 1)
        self.assertEqual(links[0][0], "Project Plan")
        self.assertEqual(links[0][1], "Plan")
        self.assertEqual(links[0][2], "project_plan")

    def test_multiple_links(self):
        links = resolve("[[A]] and [[B|Bee]]")
        self.assertEqual(len(links), 2)
        self.assertEqual(links[0][2], "a")
        self.assertEqual(links[1][2], "b")

    def test_no_links(self):
        self.assertEqual(resolve("just plain text"), [])
        self.assertEqual(resolve(""), [])

    def test_note_ids(self):
        self.assertEqual(note_ids("[[Foo]] [[Bar]]"), {"foo", "bar"})

    def test_unmatched_brackets_literal(self):
        links = resolve("[not a link]] and [[also not")
        self.assertEqual(links, [])

    def test_target_normalization(self):
        links = resolve("[[My Document]]")
        self.assertEqual(links[0][2], "my_document")


if __name__ == "__main__":
    unittest.main()
