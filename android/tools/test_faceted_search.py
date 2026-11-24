#!/usr/bin/env python3
"""Independent differential oracle for FacetedSearch (core/index).

Implements bounded faceted search in pure Python and cross-checks against
the Kotlin contract described in
core/index/src/main/kotlin/dev/fusion/index/FacetedSearch.kt.

No Android SDK, Kotlin compiler or Gradle is required; this runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest
from typing import List, Set, Optional

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))


MAX_DOCUMENTS = 100_000
MAX_FACETS_PER_DOC = 64
MAX_VALUES_PER_FACET = 256
MAX_FACET_NAME = 128
MAX_FACET_VALUE = 512
MAX_QUERY_TERMS = 64
MAX_RESULTS = 10_000


class FacetedSearch:
    def __init__(self):
        self._docs = {}

    def add_document(self, doc_id: str, facets: dict, text: str):
        assert len(self._docs) < MAX_DOCUMENTS, "too many documents"
        assert doc_id and len(doc_id) <= 256, "invalid document id"
        assert len(facets) <= MAX_FACETS_PER_DOC, "too many facets per document"
        for name, values in facets.items():
            assert len(name) <= MAX_FACET_NAME, "facet name too long"
            assert len(values) <= MAX_VALUES_PER_FACET, "too many values per facet"
            for v in values:
                assert len(v) <= MAX_FACET_VALUE, "facet value too long"
        assert len(text) <= 65_536, "document text too long"
        self._docs[doc_id] = {"id": doc_id, "facets": facets, "text": text}

    def search(self, facet_filters: list, term: Optional[str] = None) -> dict:
        assert len(facet_filters) <= 64, "too many facet filters"
        for ff in facet_filters:
            assert len(ff["facet"]) <= MAX_FACET_NAME, "facet name too long"
            assert len(ff["values"]) <= MAX_VALUES_PER_FACET, "too many filter values"
        assert term is None or len(term) <= 512, "query term too long"

        term_lc = term.lower() if term else None
        matching = []

        for doc_id, doc in self._docs.items():
            all_match = True
            for ff in facet_filters:
                doc_values = doc["facets"].get(ff["facet"], set())
                if not doc_values.intersection(ff["values"]):
                    all_match = False
                    break
            if not all_match:
                continue
            if term_lc and term_lc not in doc["text"].lower():
                continue
            matching.append(doc_id)
            if len(matching) >= MAX_RESULTS:
                break

        matching.sort()
        return {
            "documentIds": matching,
            "totalScanned": len(self._docs),
            "totalMatching": len(matching),
        }

    def clear(self):
        self._docs.clear()

    def facet_names(self) -> list:
        names = set()
        for doc in self._docs.values():
            names.update(doc["facets"].keys())
        return sorted(names)

    def facet_values(self, facet: str) -> list:
        values = set()
        for doc in self._docs.values():
            values.update(doc["facets"].get(facet, set()))
        return sorted(values)


class FacetedSearchTest(unittest.TestCase):
    def setUp(self):
        self.fs = FacetedSearch()

    def test_basic_facet_and_term(self):
        self.fs.add_document("doc1", {"color": {"red"}, "size": {"large"}}, "The quick brown fox")
        self.fs.add_document("doc2", {"color": {"blue"}, "size": {"small"}}, "The lazy dog")
        self.fs.add_document("doc3", {"color": {"red", "blue"}, "size": {"large"}}, "Red fox jumps")

        result = self.fs.search([{"facet": "color", "values": {"red"}}], term="fox")
        self.assertEqual(result["documentIds"], ["doc1", "doc3"])
        self.assertEqual(result["totalScanned"], 3)

    def test_and_across_facets(self):
        self.fs.add_document("doc1", {"color": {"red"}, "size": {"large"}}, "text1")
        self.fs.add_document("doc2", {"color": {"red"}, "size": {"small"}}, "text2")
        self.fs.add_document("doc3", {"color": {"blue"}, "size": {"large"}}, "text3")

        result = self.fs.search([
            {"facet": "color", "values": {"red"}},
            {"facet": "size", "values": {"large"}},
        ])
        self.assertEqual(result["documentIds"], ["doc1"])

    def test_or_within_facet(self):
        self.fs.add_document("doc1", {"color": {"red"}}, "t1")
        self.fs.add_document("doc2", {"color": {"blue"}}, "t2")
        self.fs.add_document("doc3", {"color": {"green"}}, "t3")

        result = self.fs.search([{"facet": "color", "values": {"red", "blue"}}])
        self.assertEqual(result["documentIds"], ["doc1", "doc2"])

    def test_no_match(self):
        self.fs.add_document("doc1", {"color": {"red"}}, "t1")
        result = self.fs.search([{"facet": "color", "values": {"blue"}}])
        self.assertEqual(result["documentIds"], [])
        self.assertEqual(result["totalMatching"], 0)

    def test_sorted_results(self):
        for i in range(10):
            self.fs.add_document(f"doc{i:02d}", {"color": {"red"}}, f"text{i}")
        result = self.fs.search([{"facet": "color", "values": {"red"}}])
        self.assertEqual(result["documentIds"], sorted(result["documentIds"]))

    def test_document_count(self):
        self.assertEqual(self.fs.search([])["totalScanned"], 0)
        self.fs.add_document("doc1", {}, "t1")
        self.assertEqual(self.fs.search([])["totalScanned"], 1)

    def test_facet_names_and_values(self):
        self.fs.add_document("doc1", {"color": {"red", "blue"}, "size": {"large"}}, "t1")
        self.fs.add_document("doc2", {"color": {"green"}, "size": {"small"}}, "t2")
        self.assertEqual(self.fs.facet_names(), ["color", "size"])
        self.assertEqual(self.fs.facet_values("color"), ["blue", "green", "red"])
        self.assertEqual(self.fs.facet_values("size"), ["large", "small"])

    def test_too_many_documents_is_error(self):
        for i in range(MAX_DOCUMENTS):
            self.fs.add_document(f"doc{i}", {}, "t")
        with self.assertRaises(AssertionError):
            self.fs.add_document("extra", {}, "t")


if __name__ == "__main__":
    unittest.main()
