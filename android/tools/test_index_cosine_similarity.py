#!/usr/bin/env python3
"""Differential oracle for IndexCosineSimilarity (core/index)."""
import math
import unittest


def cosine(a: dict, b: dict) -> float:
    na = math.sqrt(sum(v * v for v in a.values()))
    nb = math.sqrt(sum(v * v for v in b.values()))
    if na == 0 or nb == 0:
        return 0.0
    return sum(va * b[k] for k, va in a.items() if k in b) / (na * nb)


def intersect(a: list, b: list) -> list:
    out, i, j = [], 0, 0
    while i < len(a) and j < len(b):
        if a[i] == b[j]:
            out.append(a[i])
            i += 1
            j += 1
        elif a[i] < b[j]:
            i += 1
        else:
            j += 1
    return out


class IndexCosineSimilarityTest(unittest.TestCase):
    def test_cosine_and_postings(self):
        self.assertAlmostEqual(cosine({"x": 1.0, "y": 0.0}, {"x": 1.0, "y": 1.0}), 1.0 / math.sqrt(2.0))
        self.assertEqual(intersect([1, 3, 5, 7], [2, 3, 7, 9]), [3, 7])


if __name__ == "__main__":
    unittest.main()
