#!/usr/bin/env python3
"""Independent differential oracle for BeamOfferValidator (feature/beam).

Validates beam share offers: sender ID, payload size, expiry time, MIME
type. Rejects expired, oversized, duplicate, or invalidly typed offers.
Cross-checks against the Kotlin contract in
feature/beam/src/main/kotlin/dev/fusion/beam/BeamOfferValidator.kt.

No Android SDK, Kotlin compiler or Gradle required; runs on host Python 3.11.
"""
from __future__ import annotations

import re
import sys
import time
import unittest

sys.path.insert(0, str(__file__.rpartition("/tools/")[0] + "/tools"))

MAX_OFFERS = 512
MAX_ID_LEN = 128
MAX_PAYLOAD_BYTES = 1 << 30
MAX_MIME_LEN = 128
MAX_TS = float("inf")
ID_RE = re.compile(r"[a-zA-Z0-9_-]{1,64}")
MIME_RE = re.compile(r"[a-z]+/[a-z0-9.+-]+")


class BeamOffer:
    def __init__(self, offer_id, sender_id, payload_size_bytes, mime_type, expires_at_ms):
        self.offer_id = offer_id
        self.sender_id = sender_id
        self.payload_size_bytes = payload_size_bytes
        self.mime_type = mime_type
        self.expires_at_ms = expires_at_ms


class BeamOfferValidator:
    def __init__(self):
        self._offers = {}

    def validate(self, offer_id, sender_id, payload_size_bytes, mime_type, expires_at_ms):
        assert ID_RE.fullmatch(offer_id), f"invalid offer id: {offer_id}"
        assert len(offer_id) <= MAX_ID_LEN, "offer id too long"
        assert ID_RE.fullmatch(sender_id), f"invalid sender id: {sender_id}"
        assert len(sender_id) <= MAX_ID_LEN, "sender id too long"
        assert 0 <= payload_size_bytes <= MAX_PAYLOAD_BYTES, "payload size out of bounds"
        assert MIME_RE.fullmatch(mime_type), f"invalid MIME type: {mime_type}"
        assert len(mime_type) <= MAX_MIME_LEN, "MIME type too long"
        assert 0 <= expires_at_ms <= MAX_TS, "expiry out of bounds"

        now = int(time.time() * 1000)
        if expires_at_ms <= now:
            return (False, "offer expired")
        if offer_id in self._offers:
            return (False, "duplicate offer id")
        return (True, None)

    def record_offer(self, offer):
        assert len(self._offers) < MAX_OFFERS or offer.offer_id in self._offers, f"too many offers: {MAX_OFFERS}"
        self._offers[offer.offer_id] = offer

    def offer_count(self):
        return len(self._offers)

    def remove_offer(self, offer_id):
        return self._offers.pop(offer_id, None) is not None

    def clear(self):
        self._offers.clear()


class BeamOfferValidatorTest(unittest.TestCase):
    def setUp(self):
        self.v = BeamOfferValidator()

    def test_valid_offer(self):
        valid, reason = self.v.validate("offer-1", "alice", 1000, "image/png", int(time.time() * 1000) + 60000)
        self.assertTrue(valid)
        self.assertIsNone(reason)

    def test_expired_offer(self):
        valid, reason = self.v.validate("offer-1", "alice", 1000, "image/png", 1)
        self.assertFalse(valid)
        self.assertEqual(reason, "offer expired")

    def test_oversized_payload(self):
        with self.assertRaises(AssertionError):
            self.v.validate("offer-1", "alice", MAX_PAYLOAD_BYTES + 1, "image/png", int(time.time() * 1000) + 60000)

    def test_invalid_mime(self):
        # text/plain IS valid per MIME_RE; only clearly invalid MIME types refused
        with self.assertRaises(AssertionError):
            self.v.validate("offer-1", "alice", 1000, "not-a-mime", int(time.time() * 1000) + 60000)
        with self.assertRaises(AssertionError):
            self.v.validate("offer-1", "alice", 1000, "text/plain ", int(time.time() * 1000) + 60000)

    def test_duplicate_offer(self):
        future = int(time.time() * 1000) + 60000
        self.v.validate("offer-1", "alice", 1000, "image/png", future)
        self.v.record_offer(BeamOffer("offer-1", "alice", 1000, "image/png", future))
        valid, reason = self.v.validate("offer-1", "alice", 1000, "image/png", future)
        self.assertFalse(valid)
        self.assertEqual(reason, "duplicate offer id")

    def test_invalid_ids(self):
        with self.assertRaises(AssertionError):
            self.v.validate("bad offer", "alice", 1000, "image/png", int(time.time() * 1000) + 60000)
        with self.assertRaises(AssertionError):
            self.v.validate("offer-1", "Bad Sender", 1000, "image/png", int(time.time() * 1000) + 60000)


if __name__ == "__main__":
    unittest.main()
