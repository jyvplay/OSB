#!/usr/bin/env python3
"""Differential oracle for PerfPercentileHistogram (core/perf)."""
import unittest


class PerfHist:
    def __init__(self, cap=1024):
        self.cap, self.s = cap, []

    def record(self, ms):
        if len(self.s) == self.cap:
            self.s.pop(0)
        self.s.append(ms)

    def pct(self, p):
        ss = sorted(self.s)
        return ss[int((p / 100.0) * (len(ss) - 1))]


class PerfPercentileHistogramTest(unittest.TestCase):
    def test_pct(self):
        h = PerfHist()
        for v in [10.0, 20.0, 30.0, 40.0, 50.0]:
            h.record(v)
        self.assertEqual(h.pct(50.0), 30.0)


if __name__ == "__main__":
    unittest.main()
