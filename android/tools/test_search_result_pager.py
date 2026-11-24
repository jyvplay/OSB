#!/usr/bin/env python3
"""Independent differential oracle for SearchResultPager (feature/find).

Paginates search result IDs into bounded-size pages with next/prev
navigation. Cross-checks against the Kotlin contract in
feature/find/src/main/kotlin/dev/fusion/find/SearchResultPager.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_RESULTS = 10_000
MAX_ID_LEN = 256
MAX_PAGE_SIZE = 100
MAX_PAGES = 10_000
ID_RE = re.compile(r"[a-zA-Z0-9_-]{1,64}")


class SearchResultPager:
    def __init__(self, results, page_size):
        assert len(results) <= MAX_RESULTS, f"too many results: {MAX_RESULTS}"
        for rid in results:
            assert ID_RE.fullmatch(rid), f"invalid result id: {rid}"
            assert len(rid) <= MAX_ID_LEN, "result id too long"
        assert 1 <= page_size <= MAX_PAGE_SIZE, "page size out of bounds"
        total_pages = (len(results) + page_size - 1) // page_size if results else 0
        assert total_pages <= MAX_PAGES, f"too many pages: {MAX_PAGES}"
        self._results = list(results)
        self._page_size = page_size

    def total_results(self):
        return len(self._results)

    def total_pages(self):
        if not self._results:
            return 0
        return (len(self._results) + self._page_size - 1) // self._page_size

    def page(self, page_index):
        total = self.total_pages()
        assert 0 <= page_index < total, "page index out of bounds"
        start = page_index * self._page_size
        end = min(start + self._page_size, len(self._results))
        return self._results[start:end]

    def next_page(self, page_index):
        total = self.total_pages()
        return page_index + 1 if page_index + 1 < total else None

    def previous_page(self, page_index):
        return page_index - 1 if page_index > 0 else None

    def has_page(self, page_index):
        total = self.total_pages()
        return 0 <= page_index < total

    def page_size(self):
        return self._page_size


class SearchResultPagerTest(unittest.TestCase):
    def test_single_page(self):
        p = SearchResultPager(["a", "b", "c"], 10)
        self.assertEqual(p.total_results(), 3)
        self.assertEqual(p.total_pages(), 1)
        self.assertEqual(p.page(0), ["a", "b", "c"])

    def test_multiple_pages(self):
        ids = [f"r{i}" for i in range(25)]
        p = SearchResultPager(ids, 10)
        self.assertEqual(p.total_pages(), 3)
        self.assertEqual(p.page(0), ids[:10])
        self.assertEqual(p.page(1), ids[10:20])
        self.assertEqual(p.page(2), ids[20:])

    def test_next_prev(self):
        ids = [f"r{i}" for i in range(25)]
        p = SearchResultPager(ids, 10)
        self.assertEqual(p.next_page(0), 1)
        self.assertEqual(p.next_page(1), 2)
        self.assertIsNone(p.next_page(2))
        self.assertIsNone(p.previous_page(0))
        self.assertEqual(p.previous_page(1), 0)
        self.assertEqual(p.previous_page(2), 1)

    def test_empty(self):
        p = SearchResultPager([], 10)
        self.assertEqual(p.total_pages(), 0)
        self.assertFalse(p.has_page(0))

    def test_invalid_page(self):
        p = SearchResultPager(["a"], 10)
        with self.assertRaises(AssertionError):
            p.page(-1)
        with self.assertRaises(AssertionError):
            p.page(1)

    def test_invalid_result_id(self):
        with self.assertRaises(AssertionError):
            SearchResultPager(["Bad Id!"], 10)

    def test_invalid_page_size(self):
        with self.assertRaises(AssertionError):
            SearchResultPager(["a"], 0)
        with self.assertRaises(AssertionError):
            SearchResultPager(["a"], MAX_PAGE_SIZE + 1)


if __name__ == "__main__":
    unittest.main()
