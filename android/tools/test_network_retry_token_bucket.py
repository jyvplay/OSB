#!/usr/bin/env python3
"""Differential oracle for NetworkRetryTokenBucket (core/network)."""
import unittest


class NetworkRetryTokenBucket:
    def __init__(self, cap=10.0, refill=2.0, base_ms=250, max_ms=30_000):
        self.cap, self.refill, self.base_ms, self.max_ms = cap, refill, base_ms, max_ms
        self.buckets = {}

    def try_acquire(self, host: str, now_ms: int, cost: float = 1.0) -> bool:
        b = self.buckets.setdefault(host, [self.cap, now_ms])
        if now_ms > b[1]:
            b[0] = min(self.cap, b[0] + ((now_ms - b[1]) / 1000.0) * self.refill)
            b[1] = now_ms
        if b[0] >= cost:
            b[0] -= cost
            return True
        return False

    def next_delay_ms(self, attempt: int, retry_after: str | None = None) -> int:
        exp = min(self.max_ms, self.base_ms << min(attempt, 20))
        if retry_after and retry_after.strip().isdigit():
            ra = min(self.max_ms, int(retry_after.strip()) * 1000)
            return max(exp, ra)
        return exp


class NetworkRetryTokenBucketTest(unittest.TestCase):
    def test_bucket_and_backoff(self):
        tb = NetworkRetryTokenBucket(cap=2.0, refill=1.0)
        self.assertTrue(tb.try_acquire("api.osb", 0))
        self.assertTrue(tb.try_acquire("api.osb", 0))
        self.assertFalse(tb.try_acquire("api.osb", 0))
        self.assertTrue(tb.try_acquire("api.osb", 1000))
        self.assertEqual(tb.next_delay_ms(2), 1000)
        self.assertEqual(tb.next_delay_ms(0, "5"), 5000)


if __name__ == "__main__":
    unittest.main()
