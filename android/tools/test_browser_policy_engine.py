#!/usr/bin/env python3
"""Differential oracle for BrowserPolicyEngine (feature/browser)."""
import unittest


class BrowserPolicyEngine:
    def __init__(self, header: str):
        self.directives = {}
        for part in header.split(";"):
            t = part.strip()
            if not t:
                continue
            toks = t.split()
            name = toks[0].lower()
            if name not in self.directives:
                self.directives[name] = toks[1:]

    def is_allowed(self, directive: str, t_scheme: str, t_host: str, s_scheme: str, s_host: str) -> bool:
        srcs = self.directives.get(directive.lower(), self.directives.get("default-src"))
        if srcs is None:
            return True
        if not srcs or any(s.lower() == "'none'" for s in srcs):
            return False
        ts, th = t_scheme.lower().rstrip(":"), t_host.lower()
        ss, sh = s_scheme.lower().rstrip(":"), s_host.lower()
        for src in srcs:
            s = src.lower()
            if s == "*":
                return True
            if s == "'self'" and ts == ss and th == sh:
                return True
            if s.endswith(":") and ts == s[:-1]:
                return True
            if s.startswith("*."):
                suf = s[1:]
                if th.endswith(suf) and len(th) > len(suf):
                    return True
            else:
                clean = s.split("://")[-1].split("/")[0]
                if th == clean:
                    return True
        return False


class BrowserPolicyEngineTest(unittest.TestCase):
    def test_csp(self):
        pol = BrowserPolicyEngine("default-src 'self'; img-src https: *.cdn.example.com; object-src 'none'")
        self.assertTrue(pol.is_allowed("script-src", "https", "app.local", "https", "app.local"))
        self.assertFalse(pol.is_allowed("script-src", "https", "evil.com", "https", "app.local"))
        self.assertTrue(pol.is_allowed("img-src", "http", "a.cdn.example.com", "https", "app.local"))
        self.assertFalse(pol.is_allowed("object-src", "https", "app.local", "https", "app.local"))


if __name__ == "__main__":
    unittest.main()
