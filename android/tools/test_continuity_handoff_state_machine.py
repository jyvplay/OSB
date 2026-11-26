#!/usr/bin/env python3
"""Differential oracle for ContinuityHandoffStateMachine (feature/continuity)."""
import unittest


class Handoff:
    def __init__(self):
        self.state = "IDLE"
        self.bytes = 0

    def step(self, nxt):
        allowed = {
            "IDLE": {"DISCOVERING"},
            "DISCOVERING": {"PAIRING", "FAILED"},
            "PAIRING": {"TRANSFERRING", "FAILED"},
            "TRANSFERRING": {"COMPLETED", "FAILED"},
            "FAILED": {"DISCOVERING"},
        }
        assert nxt in allowed.get(self.state, set())
        self.state = nxt


class ContinuityHandoffStateMachineTest(unittest.TestCase):
    def test_fsm(self):
        h = Handoff()
        h.step("DISCOVERING")
        h.step("PAIRING")
        h.step("TRANSFERRING")
        h.step("COMPLETED")
        self.assertEqual(h.state, "COMPLETED")


if __name__ == "__main__":
    unittest.main()
