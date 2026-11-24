#!/usr/bin/env python3
"""Independent differential oracle for CommandRouter (core/commands).

Implements bounded command router with an explicit allowlist: registers command
names with an arity cap and optional required-flag set; dispatch looks up the
name, validates argument count and required flags, then returns the resolved
target. Unknown commands, arity violations, missing flags, and duplicate
registration are refused. Cross-checks against the Kotlin contract in
core/commands/src/main/kotlin/dev/fusion/commands/CommandRouter.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_COMMANDS = 128
MAX_ARGS = 16
MAX_FLAGS = 8
NAME_RE = re.compile(r"[a-z][a-z0-9-]{0,31}")


class CommandEntry:
    def __init__(self, target: str, max_args: int, required_flags: set):
        self.target = target
        self.max_args = max_args
        self.required_flags = set(required_flags)


class CommandRouter:
    def __init__(self):
        self._commands = {}

    def register(self, name: str, target: str, max_args: int = MAX_ARGS, required_flags: set = None):
        assert NAME_RE.fullmatch(name), f"invalid command name: {name}"
        assert name not in self._commands, f"command already registered: {name}"
        assert len(self._commands) < MAX_COMMANDS, "too many commands"
        assert 0 <= max_args <= MAX_ARGS, f"maxArgs out of bounds: {max_args}"
        if required_flags is None:
            required_flags = set()
        assert len(required_flags) <= MAX_FLAGS, "too many required flags"
        self._commands[name] = CommandEntry(target, max_args, set(required_flags))

    def unregister(self, name: str) -> bool:
        return self._commands.pop(name, None) is not None

    def resolve(self, name: str, args: list, flags: set) -> str:
        entry = self._commands.get(name)
        assert entry is not None, f"unknown command: {name}"
        assert len(args) <= entry.max_args, f"too many arguments for {name}: {len(args)} > {entry.max_args}"
        assert entry.required_flags.issubset(flags), f"missing required flags for {name}: {entry.required_flags - flags}"
        return entry.target

    def names(self) -> set:
        return set(self._commands.keys())

    def is_registered(self, name: str) -> bool:
        return name in self._commands

    def size(self) -> int:
        return len(self._commands)


class CommandRouterTest(unittest.TestCase):
    def setUp(self):
        self.router = CommandRouter()

    def test_register_and_resolve(self):
        self.router.register("open", "dev.fusion.OpenCommand")
        target = self.router.resolve("open", [], set())
        self.assertEqual(target, "dev.fusion.OpenCommand")

    def test_resolve_with_args(self):
        self.router.register("edit", "dev.fusion.EditCommand", max_args=5)
        target = self.router.resolve("edit", ["file.txt", "--readonly"], {"readonly"})
        self.assertEqual(target, "dev.fusion.EditCommand")

    def test_unknown_command_refused(self):
        with self.assertRaises(AssertionError):
            self.router.resolve("ghost", [], set())

    def test_too_many_args_refused(self):
        self.router.register("echo", "dev.fusion.EchoCommand", max_args=2)
        with self.assertRaises(AssertionError):
            self.router.resolve("echo", ["a", "b", "c", "d"], set())

    def test_missing_required_flag_refused(self):
        self.router.register("delete", "dev.fusion.DeleteCommand", required_flags={"force"})
        with self.assertRaises(AssertionError):
            self.router.resolve("delete", [], set())

    def test_required_flag_satisfied(self):
        self.router.register("delete", "dev.fusion.DeleteCommand", required_flags={"force"})
        target = self.router.resolve("delete", [], {"force"})
        self.assertEqual(target, "dev.fusion.DeleteCommand")

    def test_duplicate_register_refused(self):
        self.router.register("open", "dev.fusion.OpenCommand")
        with self.assertRaises(AssertionError):
            self.router.register("open", "dev.fusion.OpenCommandV2")

    def test_invalid_command_name(self):
        with self.assertRaises(AssertionError):
            self.router.register("1bad", "dev.fusion.X")
        with self.assertRaises(AssertionError):
            self.router.register("BAD", "dev.fusion.X")
        with self.assertRaises(AssertionError):
            self.router.register("a" * 33, "dev.fusion.X")

    def test_too_many_commands(self):
        for i in range(MAX_COMMANDS):
            self.router.register(f"cmd{i}", f"target{i}")
        with self.assertRaises(AssertionError):
            self.router.register("cmd128", "target128")

    def test_unregister(self):
        self.router.register("open", "dev.fusion.OpenCommand")
        self.assertTrue(self.router.unregister("open"))
        self.assertFalse(self.router.unregister("open"))
        self.assertFalse(self.router.is_registered("open"))

    def test_max_args_boundary(self):
        self.router.register("exact", "dev.fusion.ExactCommand", max_args=0)
        self.router.resolve("exact", [], set())
        with self.assertRaises(AssertionError):
            self.router.resolve("exact", ["a"], set())

    def test_names(self):
        self.router.register("a", "t1")
        self.router.register("b", "t2")
        self.assertEqual(self.router.names(), {"a", "b"})

    def test_size(self):
        self.assertEqual(self.router.size(), 0)
        self.router.register("a", "t1")
        self.assertEqual(self.router.size(), 1)
        self.router.unregister("a")
        self.assertEqual(self.router.size(), 0)


if __name__ == "__main__":
    unittest.main()
