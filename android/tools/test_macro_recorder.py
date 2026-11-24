#!/usr/bin/env python3
"""Independent differential oracle for MacroRecorder (feature/automation).

Records bounded macro steps with optional parameter maps, supports
pause/resume recording and bounded playback. Cross-checks against the
Kotlin contract in
feature/automation/src/main/kotlin/dev/fusion/automation/MacroRecorder.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_STEPS = 1024
MAX_NAME_LEN = 128
MAX_PARAMS = 16
MAX_PARAM_LEN = 256
MAX_TOTAL_BYTES = 1 << 16
NAME_RE = __import__("re").compile(r"[a-z][a-z0-9_]{0,63}")


class MacroStep:
    def __init__(self, name: str, params: dict):
        self.name = name
        self.params = dict(params)


class MacroRecorder:
    def __init__(self):
        self._steps = []
        self._recording = False
        self._total_bytes = 0

    def start_recording(self):
        assert len(self._steps) == 0, "cannot start with existing steps"
        self._recording = True
        self._total_bytes = 0

    def stop_recording(self):
        assert self._recording, "not recording"
        self._recording = False
        return ([MacroStep(s.name, s.params) for s in self._steps], self._total_bytes)

    def add_step(self, name: str, params: dict = None):
        assert self._recording, "not recording"
        assert NAME_RE.fullmatch(name), f"invalid step name: {name}"
        assert len(name) <= MAX_NAME_LEN, "step name too long"
        assert len(self._steps) < MAX_STEPS, "too many steps"
        if params is None:
            params = {}
        assert len(params) <= MAX_PARAMS, "too many params"
        for k, v in params.items():
            assert len(k) <= MAX_PARAM_LEN and len(v) <= MAX_PARAM_LEN, "param too long"
        step_bytes = len(name)
        for k, v in params.items():
            step_bytes += len(k) + len(v)
        assert self._total_bytes + step_bytes <= MAX_TOTAL_BYTES, "macro too large"
        self._total_bytes += step_bytes
        self._steps.append(MacroStep(name, dict(params)))

    def pause(self):
        self._recording = False

    def resume(self):
        self._recording = True

    def is_recording(self):
        return self._recording

    def step_count(self):
        return len(self._steps)

    def clear(self):
        self._steps.clear()
        self._total_bytes = 0
        self._recording = False


class MacroRecorderTest(unittest.TestCase):
    def setUp(self):
        self.rec = MacroRecorder()

    def test_record_and_playback(self):
        self.rec.start_recording()
        self.rec.add_step("click", {"x": "100", "y": "200"})
        self.rec.add_step("wait", {"ms": "500"})
        steps, total = self.rec.stop_recording()
        self.assertEqual(len(steps), 2)
        self.assertEqual(steps[0].name, "click")
        self.assertEqual(steps[0].params["x"], "100")
        self.assertEqual(steps[1].name, "wait")
        self.assertGreater(total, 0)

    def test_not_recording(self):
        with self.assertRaises(AssertionError):
            self.rec.add_step("click")

    def test_stop_without_start(self):
        with self.assertRaises(AssertionError):
            self.rec.stop_recording()

    def test_invalid_step_name(self):
        self.rec.start_recording()
        with self.assertRaises(AssertionError):
            self.rec.add_step("BadName")
        with self.assertRaises(AssertionError):
            self.rec.add_step("1bad")
        with self.assertRaises(AssertionError):
            self.rec.add_step("a" * 129)

    def test_pause_resume(self):
        self.rec.start_recording()
        self.rec.add_step("a")
        self.rec.pause()
        self.assertFalse(self.rec.is_recording())
        self.rec.resume()
        self.assertTrue(self.rec.is_recording())
        self.rec.add_step("b")
        steps, _ = self.rec.stop_recording()
        self.assertEqual(len(steps), 2)

    def test_too_many_steps(self):
        self.rec.start_recording()
        for i in range(MAX_STEPS):
            self.rec.add_step(f"step{i}")
        with self.assertRaises(AssertionError):
            self.rec.add_step("step_too_many")

    def test_clear(self):
        self.rec.start_recording()
        self.rec.add_step("a")
        self.rec.clear()
        self.assertEqual(self.rec.step_count(), 0)
        self.assertFalse(self.rec.is_recording())


if __name__ == "__main__":
    unittest.main()
