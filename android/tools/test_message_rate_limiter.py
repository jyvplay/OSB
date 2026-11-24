#!/usr/bin/env python3
"""Differential oracle for MessageRateLimiter (feature/messages)."""
import unittest


class MessageRateLimiter:
    def __init__(self, cap: float, refill: float):
        self.cap = cap
        self.refill = refill
        self.tokens = cap
        self.last_ms = 0

    def try_consume(self, now_ms: int, cost: float = 1.0) -> bool:
        assert now_ms >= self.last_ms
        self.tokens = min(self.cap, self.tokens + ((now_ms - self.last_ms) / 1000.0) * self.refill)
        self.last_ms = now_ms
        if self.tokens >= cost:
            self.tokens -= cost
            return True
        return False


class MessageRateLimiterTest(unittest.TestCase):
    def test_rate_limit(self):
        lim = MessageRateLimiter(2.0, 1.0)
        self.assertTrue(lim.try_consume(0))
        self.assertTrue(lim.try_consume(0))
        self.assertFalse(lim.try_consume(0))
        self.assertTrue(lim.try_consume(1000))


if __name__ == "__main__":
    unittest.main()
