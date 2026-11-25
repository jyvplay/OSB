#!/usr/bin/env python3
"""Differential oracle for ContactsVcardParser (feature/contacts)."""
import re
import unittest


def parse_vcard(raw: str):
    unfolded = re.sub(r"\n[ \t]", "", raw.replace("\r\n", "\n"))
    fn, emails, phones = "", [], []
    for line in unfolded.splitlines():
        if ":" not in line:
            continue
        prop, val = line.split(":", 1)
        name = prop.split(";")[0].upper()
        if name == "FN":
            fn = val.strip()
        elif name == "EMAIL":
            emails.append(val.strip())
        elif name == "TEL":
            phones.append(val.strip())
    return fn, emails, phones


class ContactsVcardParserTest(unittest.TestCase):
    def test_vcard(self):
        raw = "BEGIN:VCARD\r\nFN:Alice\r\n Smith\r\nTEL;TYPE=CELL:+1555\r\nEMAIL:a@b.com\r\nEND:VCARD"
        self.assertEqual(parse_vcard(raw), ("AliceSmith", ["a@b.com"], ["+1555"]))


if __name__ == "__main__":
    unittest.main()
