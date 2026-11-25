#!/usr/bin/env python3
"""Differential oracle for WalCompactionEngine (core/storage)."""
import unittest
import zlib


def compute_crc32(seq: int, op: str, key: str, val: str | None) -> int:
    payload = f"{seq}|{op}|{key}|{val or ''}".encode("utf-8")
    return zlib.crc32(payload) & 0xFFFFFFFF


class WalCompactionEngine:
    def __init__(self):
        self.log = []
        self.seq = 1

    def put(self, k: str, v: str):
        s = self.seq
        self.seq += 1
        self.log.append((s, "PUT", k, v, compute_crc32(s, "PUT", k, v)))

    def delete(self, k: str):
        s = self.seq
        self.seq += 1
        self.log.append((s, "DELETE", k, None, compute_crc32(s, "DELETE", k, None)))

    def replay(self):
        st = {}
        for s, op, k, v, crc in self.log:
            assert crc == compute_crc32(s, op, k, v)
            if op == "PUT":
                st[k] = v
            elif op == "DELETE":
                st.pop(k, None)
        return st

    def compact(self):
        live = self.replay()
        self.log.clear()
        for k, v in live.items():
            self.put(k, v)
        return len(self.log)


class WalCompactionEngineTest(unittest.TestCase):
    def test_wal_and_compact(self):
        w = WalCompactionEngine()
        w.put("a", "1")
        w.put("b", "2")
        w.put("a", "3")
        w.delete("b")
        self.assertEqual(w.replay(), {"a": "3"})
        self.assertEqual(w.compact(), 1)
        self.assertEqual(w.replay(), {"a": "3"})


if __name__ == "__main__":
    unittest.main()
