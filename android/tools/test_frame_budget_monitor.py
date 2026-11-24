#!/usr/bin/env python3
"""Independent differential oracle for FrameBudgetMonitor (core/perf).

Tracks frame presentation times against a target frame budget, classifies
frames as on-time or over-budget, and tracks a consecutive-over-budget
streak that triggers a thermal-throttle advisory. Cross-checks against
the Kotlin contract in
core/perf/src/main/kotlin/dev/fusion/perf/FrameBudgetMonitor.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_FRAMES = 10_000
MAX_BUDGET_MS = 1000.0
MAX_ELAPSED_MS = 10_000.0
MAX_OVER_BUDGET_STREAK = 100


class FrameBudgetMonitor:
    def __init__(self, budget_ms: float):
        assert 0.0 < budget_ms <= MAX_BUDGET_MS, "budget out of bounds"
        self.budget_ms = budget_ms
        self.frames = []

    def frame(self, elapsed_ms: float):
        assert 0.0 <= elapsed_ms <= MAX_ELAPSED_MS, "elapsedMs out of bounds"
        assert len(self.frames) < MAX_FRAMES, "too many frames"
        over_budget = elapsed_ms > self.budget_ms
        streak = 0
        if over_budget:
            s = 1
            for i in range(len(self.frames) - 1, -1, -1):
                if self.frames[i][1]:
                    s += 1
                else:
                    break
            streak = min(s, MAX_OVER_BUDGET_STREAK)
        self.frames.append((elapsed_ms, over_budget))
        over_count = sum(1 for _, ob in self.frames if ob)
        avg = sum(e for e, _ in self.frames) / len(self.frames)
        return (avg, over_count, streak, streak >= MAX_OVER_BUDGET_STREAK)

    def status(self):
        over_count = sum(1 for _, ob in self.frames if ob)
        streak = 0
        for i in range(len(self.frames) - 1, -1, -1):
            if self.frames[i][1]:
                streak += 1
            else:
                break
        avg = sum(e for e, _ in self.frames) / len(self.frames) if self.frames else 0.0
        return (avg, over_count, streak, streak >= MAX_OVER_BUDGET_STREAK)

    def frame_count(self):
        return len(self.frames)

    def reset(self):
        self.frames = []


class FrameBudgetMonitorTest(unittest.TestCase):
    def setUp(self):
        self.monitor = FrameBudgetMonitor(16.67)

    def test_on_time_frame(self):
        avg, over, streak, throttle = self.monitor.frame(10.0)
        self.assertEqual(avg, 10.0)
        self.assertEqual(over, 0)
        self.assertEqual(streak, 0)
        self.assertFalse(throttle)

    def test_over_budget_frame(self):
        self.monitor.frame(20.0)
        avg, over, streak, throttle = self.monitor.frame(20.0)
        self.assertEqual(over, 2)
        self.assertEqual(streak, 2)
        self.assertFalse(throttle)

    def test_thermal_throttle_advised(self):
        for _ in range(MAX_OVER_BUDGET_STREAK):
            self.monitor.frame(100.0)
        _, _, _, throttle = self.monitor.frame(100.0)
        self.assertTrue(throttle)

    def test_streak_resets_on_time(self):
        self.monitor.frame(100.0)
        self.monitor.frame(10.0)
        _, _, streak, _ = self.monitor.frame(10.0)
        self.assertEqual(streak, 0)

    def test_status_empty(self):
        avg, over, streak, throttle = self.monitor.status()
        self.assertEqual(avg, 0.0)
        self.assertEqual(over, 0)
        self.assertEqual(streak, 0)
        self.assertFalse(throttle)

    def test_budget_too_large(self):
        with self.assertRaises(AssertionError):
            FrameBudgetMonitor(MAX_BUDGET_MS + 1)

    def test_budget_zero(self):
        with self.assertRaises(AssertionError):
            FrameBudgetMonitor(0.0)

    def test_elapsed_negative(self):
        with self.assertRaises(AssertionError):
            self.monitor.frame(-1.0)


if __name__ == "__main__":
    unittest.main()
