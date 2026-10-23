# R37 primary-source record (fetched this round, with strength labels)

Rule this round: a claim enters a kernel only if the *primary* text was read
here. Search-result prose is recorded separately and never used to pin code.

## Fetched primaries (read, quoted, and made load-bearing)

### 1. RFC 2326 — Real Time Streaming Protocol (rfc-editor.org/rfc/rfc2326.txt)

Read §7.1.1 (Status-Code / Reason-Phrase grammar) and Table 1.

- The §7.1.1 `Status-Code` ABNF lists **44** codes: 100, 200, 201, 250,
  300, 301, 302, 303, 304, 305, 400–408, 410–415, 451–462, 500–505, 551.
  `304 Not Modified` is in the ABNF and **absent from Table 1**.
- The RFC disagrees with itself on spelling. §7.1.1 grammar vs Table 1:
  `250 Low on Storage Space` (both), `408 Request Time-out` / `Request Timeout`,
  `414 Request-URI Too Large` / `Request-URI Too Long`,
  `451 Parameter Not Understood` / `Invalid parameter`,
  `452 Conference Not Found` / `Illegal Conference Identifier`,
  `456 Header Field Not Valid for Resource` / `Header Field Not Valid`,
  `459 Aggregate operation not allowed` / `Aggregate Operation Not Allowed`,
  `504 Gateway Time-out` / `Gateway Timeout`,
  `505 RTSP Version not supported` / `RTSP Version Not Supported`,
  `551 Option not supported` / `Option not support`.
- Consequence (load-bearing): `RtspReason` keeps the **grammar** spellings
  verbatim (the normative ABNF and the only list containing 304) and the KDoc
  says so. The first draft of the table carried "Low on Storage", "Request
  Timeout", "Header Field Not Valid" and omitted 304 — all corrected before
  the first green run, from this fetch.

### 2. The .xz File Format 1.2.1 (github.com/tukaani-project/xz, doc/xz-file-format.txt)

Read §2.1.1.1–2.1.1.3.

- Header magic is exactly `FD 37 7A 58 5A 00`; the first Stream Flags byte
  "is always a null byte".
- Assigned Check IDs: `0x00` None (0 B), `0x01` CRC32 (4 B), `0x04` CRC64
  (8 B), `0x0A` SHA-256 (32 B). IDs `0x02, 0x03, 0x05–0x09, 0x0B–0x0F` are
  **reserved**; bits 4–7 of the second flags byte "MUST be zero".
- The header CRC32 "is calculated from the Stream Flags field" and stored
  little-endian; a mismatch MUST be an error.
- Consequence (load-bearing, corrective): the first draft had `SHA256(7)`.
  ID 7 is reserved for a 16-byte (unnamed) check; SHA-256 is `0x0A`. Fixed to
  `SHA256(10)` before the first green run, and the reserved-ID refusal is now
  pinned by tests (ids 2, 3, 7, 0x0B, and a high-nibble value) plus a
  regression test that the constant is 10.

### 3. RFC 5545 — iCalendar (rfc-editor.org/rfc/rfc5545.txt)

Read §3.8.1.11 (STATUS).

- `statvalue-todo = "NEEDS-ACTION" / "COMPLETED" / "IN-PROCESS" / "CANCELLED"`.
  The token is **IN-PROCESS**, not "IN-PROGRESS".
- Consequence (load-bearing, shipped-code repair): `VTodoParse` accepted
  `IN-PROCESS`'s common misspelling and would have refused every conformant
  VTODO written by an RFC-following client. The parser now accepts the RFC
  token only; `R34Checks` and `check_r34_reference.py` were updated, and R34's
  166 assertions plus its reference were re-run green (repair inherits no
  trust).

## Searches (weaker: aggregator prose, not standards text)

Searched this round, used only for the privacy/anonymity PRD and for
currency checks — never to pin code behavior:

| Topic | Source | Takeaway used |
|---|---|---|
| Tails currency | endoflife.date/tails, es.wikipedia Tails, distrowatch/tails (2026-10-13/14) | Tails **7.15 (2026-10-13)** is current; no 7.16 signal. Debian 13 base, x86-64 only, RAM wipe on shutdown, Persistent Storage encrypted but *detectable*. |
| Hardware keyloggers | en.wikipedia Keystroke_logging, airdrivewifi FAQ, bastille.net O.M.G. cable research, nviso 2026-04, startupdefense 2026-06 | Inline implants are invisible to software; detection is physical inspection + USB topology review; **dual-HID enumerations** are the cheap surviving heuristic; aggressive screen-lock plus USB blocking while locked is cheap and effective; **on-screen input** defeats inline hardware loggers; FIDO2 challenge-response defeats captured passwords. Injection-speed heuristics are explicitly evadable. |
| Android/Tails usage context | (prior R37 batch) Google security blog, gbhackers, Pixel bulletin 2026-10-01 | Advanced Protection's six features and optional Intrusion Logging (E2E-encrypted, 12-month, undeletable) stand; nothing here is claimed as implemented by this app. |

## What these sources did NOT establish

- No source read this round shows a software-only method to detect a
  non-enumerating inline hardware keylogger. The app therefore claims
  *reduction of the most common input-path weakness* (secret entry through the
  on-screen pad, HID topology review), never keylogger detection.
- No source shows a Tails-equivalent guarantee achievable inside an Android
  app; the PRD keeps that boundary explicit.
