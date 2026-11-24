#!/usr/bin/env python3
"""Independent differential oracle for DlnaDevice (cast/dlna).

Extracts device type, friendly name, manufacturer, model name, and
presentation URL from a minimal DLNA/UPnP device description subset.
Cross-checks against the Kotlin contract in
cast/dlna/src/main/kotlin/dev/fusion/dlna/DlnaDevice.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_INPUT = 1 << 14
MAX_FIELD_LEN = 1024
TAG_RE = re.compile(r"<([a-zA-Z][a-zA-Z0-9]*)>([^<]*)</\1>")


def parse(xml: str):
    assert len(xml) <= MAX_INPUT, f"XML exceeds {MAX_INPUT} chars"
    fields = {}
    for m in TAG_RE.finditer(xml):
        tag = m.group(1)
        value = m.group(2)
        if len(value) <= MAX_FIELD_LEN:
            fields[tag] = value.strip()
    return {
        "device_type": fields.get("deviceType"),
        "friendly_name": fields.get("friendlyName"),
        "manufacturer": fields.get("manufacturer"),
        "model_name": fields.get("modelName"),
        "presentation_url": fields.get("presentationURL"),
    }


def is_media_server(xml: str) -> bool:
    dt = parse(xml)["device_type"]
    return dt is not None and "MediaServer" in dt


def is_media_renderer(xml: str) -> bool:
    dt = parse(xml)["device_type"]
    return dt is not None and "MediaRenderer" in dt


class DlnaDeviceTest(unittest.TestCase):
    def test_full_device(self):
        xml = (
            "<root>"
            "<deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>"
            "<friendlyName>My Media Server</friendlyName>"
            "<manufacturer>Acme Corp</manufacturer>"
            "<modelName>MediaBox 2000</modelName>"
            "<presentationURL>http://192.168.1.10/</presentationURL>"
            "</root>"
        )
        dev = parse(xml)
        self.assertEqual(dev["device_type"], "urn:schemas-upnp-org:device:MediaServer:1")
        self.assertEqual(dev["friendly_name"], "My Media Server")
        self.assertEqual(dev["manufacturer"], "Acme Corp")
        self.assertEqual(dev["model_name"], "MediaBox 2000")
        self.assertEqual(dev["presentation_url"], "http://192.168.1.10/")

    def test_is_media_server(self):
        xml = "<deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>"
        self.assertTrue(is_media_server(xml))
        xml2 = "<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>"
        self.assertFalse(is_media_server(xml2))

    def test_is_media_renderer(self):
        xml = "<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>"
        self.assertTrue(is_media_renderer(xml))
        xml2 = "<deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>"
        self.assertFalse(is_media_renderer(xml2))

    def test_empty(self):
        dev = parse("")
        self.assertIsNone(dev["device_type"])
        self.assertIsNone(dev["friendly_name"])


if __name__ == "__main__":
    unittest.main()
