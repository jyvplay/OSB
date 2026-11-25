#!/usr/bin/env python3
"""Differential oracle for BeamTransferEtaEstimator (feature/beam)."""
import math
import unittest


def eta_sec(rem: int, bps: int) -> int:
    return 0 if rem == 0 else math.ceil(rem / bps)


class BeamTransferEtaEstimatorTest(unittest.TestCase):
    def test_eta(self):
        self.assertEqual(eta_sec(1001, 500), 3)
        self.assertEqual(eta_sec(0, 500), 0)


if __name__ == "__main__":
    unittest.main()
