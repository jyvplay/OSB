#!/usr/bin/env python3
"""Differential oracle for CalendarRruleExpander (feature/calendar)."""
import unittest


def expand_epoch_days(start: int, rrule: str, max_occ: int = 100):
    parts = dict(kv.split("=", 1) for kv in rrule.upper().split(";") if "=" in kv)
    freq = parts["FREQ"]
    interval = max(1, int(parts.get("INTERVAL", 1)))
    count = min(max_occ, max(1, int(parts.get("COUNT", max_occ))))
    step = {"DAILY": interval, "WEEKLY": interval * 7, "MONTHLY": interval * 30}[freq]
    return [start + i * step for i in range(count)]


class CalendarRruleExpanderTest(unittest.TestCase):
    def test_rrule(self):
        self.assertEqual(expand_epoch_days(100, "FREQ=WEEKLY;INTERVAL=2;COUNT=3"), [100, 114, 128])


if __name__ == "__main__":
    unittest.main()
