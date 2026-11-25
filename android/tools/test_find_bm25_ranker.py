#!/usr/bin/env python3
"""Differential oracle for FindBm25Ranker (feature/find)."""
import math
import re
import unittest


def tokenize(t: str):
    return [x for x in re.split(r"[^a-z0-9]+", t.lower()) if x]


class FindBm25Ranker:
    def __init__(self, k1=1.5, b=0.75):
        self.k1, self.b = k1, b
        self.docs, self.lens, self.df, self.total = {}, {}, {}, 0

    def add(self, doc_id: str, text: str):
        toks = tokenize(text)
        tf = {}
        for t in toks:
            tf[t] = tf.get(t, 0) + 1
        self.docs[doc_id] = tf
        self.lens[doc_id] = len(toks)
        self.total += len(toks)
        for t in tf:
            self.df[t] = self.df.get(t, 0) + 1

    def search(self, query: str, top_k=10):
        q = list(dict.fromkeys(tokenize(query)))
        n = len(self.docs)
        avg_dl = self.total / n
        out = []
        for did, tf in self.docs.items():
            dl = self.lens[did]
            sc = 0.0
            for term in q:
                f = tf.get(term, 0)
                if f == 0:
                    continue
                df = self.df.get(term, 0)
                idf = math.log((n - df + 0.5) / (df + 0.5) + 1.0)
                sc += idf * (f * (self.k1 + 1.0)) / (f + self.k1 * (1.0 - self.b + self.b * (dl / avg_dl)))
            if sc > 0:
                out.append((did, sc))
        return sorted(out, key=lambda x: -x[1])[:top_k]


class FindBm25RankerTest(unittest.TestCase):
    def test_bm25(self):
        r = FindBm25Ranker()
        r.add("d1", "kotlin android studio")
        r.add("d2", "kotlin kotlin coroutines")
        res = r.search("kotlin")
        self.assertEqual(res[0][0], "d2")


if __name__ == "__main__":
    unittest.main()
