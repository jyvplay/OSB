#!/usr/bin/env python3
"""R146 differential corpus: 22 lanes x valid, boundary and refusal vectors.

Every case is (feature, payload, note). `payload` follows the probe transport
grammar (see r146_oracle): free fields are `enc`-ed, structure stays raw.
The expected verdict comes from r146_oracle.oracle (independent Python
implementation); the runner executes the same payload on a real JVM and
diffs. Deterministic: fixed seeds only, no wall clock, no urandom.
"""
from __future__ import annotations

import hashlib
import random
import struct

from r146_oracle import enc

Case = tuple[str, str, str]


def _segs(*segments: str) -> str:
    return "~".join(enc(s) for s in segments)


def _cpio_entry(name: str, payload: bytes, mode: int = 0o100644, ino: int = 1,
                uid: int = 0, gid: int = 0, mtime: int = 1_700_000_000,
                nlink: int = 1, magic: str = "070701",
                namesize_override: int | None = None,
                size_override: int | None = None) -> bytes:
    name_bytes = name.encode() + b"\x00"
    namesize = len(name_bytes) if namesize_override is None else namesize_override
    size = len(payload) if size_override is None else size_override
    fields = [ino, mode, uid, gid, nlink, mtime, size, 0, 0, 0, 0, namesize, 0]
    header = magic.encode() + "".join("%08x" % (f & 0xFFFFFFFF) for f in fields).encode()
    assert len(header) == 110
    block = header + name_bytes
    block += b"\x00" * ((4 - (len(block) % 4)) % 4)
    # padding is computed from absolute offsets by the caller loop below
    return block, payload


def build_cpio(members: list[tuple[str, bytes]], trailer: bool = True,
               mutate=None) -> bytes:
    out = bytearray()
    for name, payload in members:
        block, data = _cpio_entry(name, payload)
        # header+name padding from absolute offset (out already 4-aligned)
        after = len(out) + len(block)
        block += b"\x00" * ((4 - (after % 4)) % 4)
        out += block
        data_at = len(out)
        out += data
        out += b"\x00" * ((4 - (len(out) % 4)) % 4)
    if trailer:
        block, data = _cpio_entry("TRAILER!!!", b"")
        after = len(out) + len(block)
        block += b"\x00" * ((4 - (after % 4)) % 4)
        out += block
    if mutate is not None:
        out = bytearray(mutate(bytes(out)))
    return bytes(out)


def _psd_resource(rid: int, name: bytes, data: bytes) -> bytes:
    rec = b"8BIM" + struct.pack(">H", rid)
    rec += bytes([len(name)]) + name
    if (1 + len(name)) & 1:
        rec += b"\x00"
    rec += struct.pack(">I", len(data)) + data
    if len(data) & 1:
        rec += b"\x00"
    return rec


def build_psd(resources: list[bytes], *, channels: int = 3, rows: int = 8,
              cols: int = 8, depth: int = 8, mode: int = 3,
              version: int = 1, sig: bytes = b"8BPS",
              color_data: bytes = b"", truncate=None) -> bytes:
    header = sig + struct.pack(">H", version) + b"\x00" * 6 + \
        struct.pack(">H", channels) + struct.pack(">II", rows, cols) + \
        struct.pack(">HH", depth, mode)
    assert len(header) == 26
    body = struct.pack(">I", len(color_data)) + color_data
    section = b"".join(resources)
    body += struct.pack(">I", len(section)) + section
    body += struct.pack(">I", 0)  # layer/mask info length
    body += struct.pack(">H", 0)  # compression: raw placeholder
    out = header + body
    if truncate is not None:
        out = out[:truncate]
    return out


def _thumb_record(rid: int, fmt: int, w: int, h: int, wb: int, total: int,
                  comp: int, jpeg: bytes) -> bytes:
    data = struct.pack(">HIIIII", fmt, w, h, wb, total, comp) + jpeg
    return _psd_resource(rid, b"", data)


def build_psd_valid_jpeg_thumb() -> bytes:
    # Minimal SOI..EOI stand-in; only the SOI marker is validated.
    jpeg = b"\xff\xd8\xff\xe0" + b"\x00\x10JFIF\x00" + b"\xff\xd9"
    return build_psd([
        _psd_resource(1000, b"", b"\x00" * 4),
        _thumb_record(1033, 1, 16, 8, 48, len(jpeg), len(jpeg), jpeg),
    ])


def _fingerprint(seed: int, jitter: int = 0) -> str:
    """72-hex 9x8 nibble grid; jitter flips `jitter` cells (4-bit deltas)."""
    rnd = random.Random(seed)
    cells = [rnd.randrange(16) for _ in range(72)]
    for _ in range(jitter):
        idx = rnd.randrange(72)
        cells[idx] = (cells[idx] + 1 + rnd.randrange(3)) % 16
    return "".join("%x" % c for c in cells)


def build_corpus() -> list[Case]:
    cases: list[Case] = []

    # ---------------------------------------------------------- inexact ----
    a1 = "a:60000:60000:0:0"
    a2 = "b:3600000:123456:300000:1"
    a3 = "c:86400000:999999999:60000:0"
    cases.append(("inexact", _segs("1000", "900000", a1), "grid-ceiling basic"))
    cases.append(("inexact", _segs("1000000", "3600000",
                                   ",".join([a1, a2, a3])), "mixed exact/inexact"))
    cases.append(("inexact", _segs("0", "60000", "z:60000:0:0:0"),
                  "firstDue==now boundary"))
    cases.append(("inexact", _segs("60000", "60000", "z:60000:0:0:0"),
                  "due just past -> next interval"))
    cases.append(("inexact", _segs("0", "60000", "x:60000:3024000000:0:0"),
                  "horizon boundary at 35d refused"))
    cases.append(("inexact", _segs("0", "60000", "x:60000:2591999000:0:0"),
                  "30d-1000ms horizon accepted"))
    cases.append(("inexact", _segs("0", "59999", a1), "grid under min refused"))
    cases.append(("inexact", _segs("0", "86400001", a1), "grid over max refused"))
    cases.append(("inexact", _segs("0", "60000", "a:60000:0:0:0,a:60000:0:0:0"),
                  "duplicate ids refused"))
    cases.append(("inexact", _segs("0", "60000", "a:59999:0:0:0"),
                  "interval under min refused"))
    cases.append(("inexact", _segs("0", "60000", "a:2592000001:0:0:0"),
                  "interval over 30d refused"))
    cases.append(("inexact", _segs("0", "60000", "a:60000:0:15001:0"),
                  "flex over interval/4 refused"))
    cases.append(("inexact", _segs("0", "60000", "a:60000:0:15000:0"),
                  "flex at interval/4 accepted"))
    cases.append(("inexact", _segs("0", "60000", "bad id:60000:0:0:0"),
                  "illegal id refused"))
    cases.append(("inexact", _segs("0", "60000", "a:60000:0:0:2"),
                  "exact flag 2 refused"))
    cases.append(("inexact", _segs("-1", "60000", a1), "negative now refused"))
    batch = ",".join("s%d:3600000:1700000000000:1000:0" % i for i in range(5))
    cases.append(("inexact", _segs("1699999999000", "900000", batch),
                  "five alarms share a maintenance slot"))

    # ---------------------------------------------------------- keytouch ----
    table = "ctrl+alt+t=>tap:500:500,shift+up=>swipe:up,f1=>tap:10:20,a=>hold:10:20:200"
    cases.append(("keyres", _segs(table, "ctrl+alt+t"), "mapped tap resolves"))
    cases.append(("keyres", _segs(table, "Alt+Ctrl+T"), "canonicalizes case+order"))
    cases.append(("keyres", _segs(table, "ctrl+alt+t+x"), "unknown key refused"))
    cases.append(("keyres", _segs(table, "ctrl+ctrl+a"), "dup modifier refused"))
    cases.append(("keyres", _segs(table, "alt+ctrl+t"), "out-of-order mods refused"))
    cases.append(("keyres", _segs(table, "b"), "unmapped -> UNMAPPED (not REF)"))
    reserved = "meta+l=>pass:x"
    cases.append(("keyres", _segs(reserved, "meta+l"),
                  "reserved meta chord refused at table parse"))
    cases.append(("keyres", _segs("ctrl+alt+delete=>tap:1:1", "ctrl+alt+delete"),
                  "reserved ctrl+alt+delete refused"))
    cases.append(("keyres", _segs(table, "ctrl+alt+"), "modifier-only chord refused"))
    bad_action = "ctrl+alt+t=>tap:1001:0"
    cases.append(("keyres", _segs(bad_action, "ctrl+alt+t"), "permille over 1000"))
    bad_hold = "ctrl+alt+t=>hold:1:1:49"
    cases.append(("keyres", _segs(bad_hold, "ctrl+alt+t"), "hold 49ms refused"))
    seq = "ctrl+alt+t@1000,ctrl+alt+t@1500,ctrl+alt+t@4000,b@10000"
    cases.append(("keyseq", _segs(table, seq, "1000"), "cooldown suppression"))
    cases.append(("keyseq", _segs(table, seq, "0"), "cooldown 0 never suppresses"))
    cases.append(("keyseq", _segs(table, "a@0,a@100", "-1"), "negative cooldown"))
    cases.append(("keyseq", _segs(table, "a@100,a@99", "1000"),
                  "decreasing timestamps refused"))
    cases.append(("keyseq", _segs(table, "b@1", "1000"), "unmapped chord in seq refused"))
    cases.append(("keyseq", _segs(table, "", "1000"), "empty sequence -> empty"))
    cases.append(("keyseq", _segs(table, "a@0", "5001"), "cooldown over 5000 refused"))

    # --------------------------------------------------------------- pdf ----
    page = "0,0,612,792"
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "TEXT", "0.75", "4", "Al;ce|1", "note,#1",
                                   "100,700"]),
                  "sticky note with hostile author/contents"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "HIGHLIGHT", "1", "0", "a", "",
                                   "10,700,120,720;130,700,200,715"]),
                  "two quads + union rect"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "INK", "0.5", "4", "x", "",
                                   "10,10;20,30;40,15"]),
                  "ink polyline"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "TEXT", "1", "0", "a", "", "612,792"]),
                  "anchor on page corner -> degenerate rect refused"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "HIGHLIGHT", "0", "0", "a", "",
                                   "0,0,10,10"]),
                  "opacity 0 refused"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "HIGHLIGHT", "1.01", "0", "a", "",
                                   "0,0,10,10"]),
                  "opacity >1 refused"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "HIGHLIGHT", "1", "2", "a", "",
                                   "0,0,10,10"]),
                  "unknown flag bit refused"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "HIGHLIGHT", "1", "4", "a", "",
                                   "10,700,120,720;500,0,900,50"]),
                  "partially visible quad accepted"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "INK", "1", "4", "a", "",
                                   "5,5;1000,1000"]),
                  "ink point outside refused"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  ["0,0,0,100", "TEXT", "1", "0", "a", "", "1,1"]),
                  "inverted box refused"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "SVG", "1", "0", "a", "", "1,1"]),
                  "unknown type refused"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  ["0,0,612,792", "TEXT", "1", "0", "a", "",
                                   "1000001,0"]),
                  "coordinate over 1e6 refused"))
    cases.append(("pdf", "|".join(enc(f) for f in
                                  [page, "HIGHLIGHT", "1", "4", "a", "",
                                   "0,0,10,10;10,0,20,10;20,0,30,10"]),
                  "512-bound prologue kept small here"))

    # -------------------------------------------------------------- cpio ----
    good = build_cpio([("readme.txt", b"hello cpio"), ("dir/", b"")])
    cases.append(("cpio", good.hex(), "two members + trailer"))
    only_trailer = build_cpio([])
    cases.append(("cpio", only_trailer.hex(), "trailer-only archive"))
    padded = build_cpio([("a", b"x")]) + b"\x00" * 500
    cases.append(("cpio", padded.hex(), "500 zero bytes after trailer"))
    too_pad = build_cpio([("a", b"x")]) + b"\x00" * 512
    cases.append(("cpio", too_pad.hex(), "512 fill after trailer refused"))
    nonzero = bytearray(build_cpio([("a", b"x")]))
    nonzero += b"\x00" * 8
    nonzero[-1] = 1
    cases.append(("cpio", bytes(nonzero).hex(), "nonzero fill refused"))
    no_trailer = build_cpio([("a", b"x")], trailer=False)
    cases.append(("cpio", no_trailer.hex(), "missing trailer refused"))
    bad_magic = bytearray(build_cpio([("a", b"x")]))
    bad_magic[0:6] = b"070707"
    cases.append(("cpio", bytes(bad_magic).hex(), "odc magic refused"))
    bad_hex = bytearray(build_cpio([("a", b"x")]))
    bad_hex[6] = ord("G")
    cases.append(("cpio", bytes(bad_hex).hex(), "non-hex count refused"))
    interior_nul = build_cpio([("a\x00b", b"x")])
    cases.append(("cpio", interior_nul.hex(), "interior NUL refused"))
    claimed_big = bytearray(build_cpio([("a", b"x")], trailer=False))
    # forge the filesize field of the first entry to 1 GiB + 1
    claimed_big[54:62] = b"40000001"
    cases.append(("cpio", bytes(claimed_big).hex(),
                  "member size 1GiB+1 refused before read"))
    truncated = build_cpio([("hello.txt", b"payload")])[:80]
    cases.append(("cpio", truncated.hex(), "truncated stream refused"))
    long_name = build_cpio([("n" * 255, b"x")])
    cases.append(("cpio", long_name.hex(), "255-char name accepted"))
    long_name2 = build_cpio([("n" * 256, b"x")])
    cases.append(("cpio", long_name2.hex(), "256-char name refused"))
    crc = build_cpio([("a", b"x")])
    crc = bytearray(crc)
    crc[0:6] = b"070702"
    cases.append(("cpio", bytes(crc).hex(), "crc magic accepted"))
    utf8 = build_cpio([("café.txt", b"x")])
    cases.append(("cpio", utf8.hex(), "utf-8 member name"))

    # --------------------------------------------------------------- psd ----
    cases.append(("psd", build_psd_valid_jpeg_thumb().hex(), "8BIM + JPEG thumb"))
    raw_thumb = build_psd([
        _thumb_record(1036, 0, 4, 4, 12, 48, 48, bytes(range(48))),
    ])
    cases.append(("psd", raw_thumb.hex(), "raw thumbnail id1036"))
    no_thumb = build_psd([_psd_resource(1000, b"", b"\x01\x02")])
    cases.append(("psd", no_thumb.hex(), "no thumbnail"))
    odd_name = build_psd([_psd_resource(1001, b"nm", b"\x00" * 3)])
    cases.append(("psd", odd_name.hex(), "odd pascal name padded"))
    cases.append(("psd", build_psd([], version=2).hex(), "PSB version2 refused"))
    cases.append(("psd", build_psd([], channels=0).hex(), "zero channels refused"))
    cases.append(("psd", build_psd([], depth=7).hex(), "depth7 refused"))
    cases.append(("psd", build_psd([], mode=10).hex(), "color mode 10 refused"))
    cases.append(("psd", build_psd([], sig=b"8PBS").hex(), "bad signature refused"))
    cases.append(("psd", build_psd([], truncate=20).hex(), "truncated header refused"))
    big_dim = build_psd([_psd_resource(1000, b"", b"\x00" * 4)], rows=30001)
    cases.append(("psd", big_dim.hex(), "rows 30001 refused"))
    bad_so = build_psd([
        _thumb_record(1033, 1, 8, 8, 24, 64, 64, b"\x00" * 64),
    ])
    cases.append(("psd", bad_so.hex(), "jpeg thumb without SOI refused"))
    big_w = build_psd([
        _thumb_record(1033, 0, 4097, 8, 3, 4097 * 3, 10, b"\x00" * 10),
    ])
    cases.append(("psd", big_w.hex(), "thumb width 4097 refused"))
    res_over = build_psd([_psd_resource(1000, b"", b"\x00" * 4)])
    res_over = bytearray(res_over)
    # claim a larger resource section than exists
    sect_len_at = 26 + 4
    struct.pack_into(">I", res_over, sect_len_at, 1 << 20)
    cases.append(("psd", bytes(res_over).hex(), "section length overrun refused"))

    # ------------------------------------------------------------ odfmeta ----
    doc = ("<?xml version='1.0'?><office:document>"
           "<office:meta><dc:title>Hi</dc:title><dc:creator>Alice</dc:creator>"
           "<meta:generator>X 9</meta:generator><dc:language>en</dc:language>"
           "</office:meta><body>keep me</body></office:document>")
    cases.append(("odfmeta", enc(doc), "drop creator+generator keep title+language"))
    doc2 = ("<?xml version='1.0'?><office:document><office:meta>"
            "<!-- wipe -->"
            "<meta:user-defined meta:name='k' office:string-type='x'>secret</meta:user-defined>"
            "<dc:title>t</dc:title></office:meta><body/></office:document>")
    cases.append(("odfmeta", enc(doc2), "comment dropped, user-defined dropped"))
    doc3 = ("<office:meta><dc:title>ok &gt; ok</dc:title>"
            "<meta:editing-cycles>3</meta:editing-cycles></office:meta>")
    cases.append(("odfmeta", enc(doc3), "escaped content kept verbatim"))
    doc4 = "<office:meta><dc:creator><nested/></dc:creator></office:meta>"
    cases.append(("odfmeta", enc(doc4), "nested child refused"))
    doc5 = "<office:meta><dc:creator>open</office:meta>"
    cases.append(("odfmeta", enc(doc5), "unterminated child refused"))
    cases.append(("odfmeta", enc("<body>no meta</body>"), "missing meta refused"))
    cases.append(("odfmeta", enc("<office:meta></office:meta><office:meta></office:meta>"),
                  "duplicate meta refused"))
    doc6 = ("<office:meta><meta:user-defined meta:name='a>b'>v</meta:user-defined>"
            "<dc:title>x</dc:title></office:meta>")
    cases.append(("odfmeta", enc(doc6), "quote-held '>' attribute parsed"))
    doc7 = "<office:meta><dc:title/>" + "<x/>" * 3 + "</office:meta>"
    cases.append(("odfmeta", enc(doc7), "self-closing allow/drop mix"))
    doc8 = "<office:meta><!-- unterminated</office:meta>"
    cases.append(("odfmeta", enc(doc8), "unterminated comment refused"))
    doc9 = "<office:meta><dc:title>" + ("t" * 10) + "</dc:title></office:meta>"
    cases.append(("odfmeta", enc(doc9 + doc9[len(doc9) // 2:]),
                  "second close refused"))

    # ------------------------------------------------------------- geohash ----
    cases.append(("geo", _segs("enc", "42.605", "-5.603", "5"), "encode precision 5"))
    cases.append(("geo", _segs("dec", "ezs42"), "decode standard example"))
    cases.append(("geo", _segs("dec", "ezs42"), "decode idempotent check"))
    cases.append(("geo", _segs("enc", "-90", "180", "12"), "south-east corner"))
    cases.append(("geo", _segs("enc", "90", "-180", "1"), "north-west corner"))
    cases.append(("geo", _segs("adj", "ezs42", "N"), "neighbour north"))
    cases.append(("geo", _segs("adj", "ezs42", "S"), "neighbour south"))
    cases.append(("geo", _segs("adj", "ezs42", "E"), "neighbour east"))
    cases.append(("geo", _segs("adj", "ezs42", "W"), "neighbour west"))
    pole = _segs("enc", "89.999999", "0", "8")
    cases.append(("geo", pole, "encode near pole"))
    # antimeridian cell: east neighbour wraps to negative longitude
    wrap_src = _segs("enc", "0", "179.9", "6")
    cases.append(("geo", wrap_src, "encode at antimeridian"))
    cases.append(("geo", _segs("enc", "91", "0", "5"), "latitude 91 refused"))
    cases.append(("geo", _segs("enc", "0", "181", "5"), "longitude 181 refused"))
    cases.append(("geo", _segs("enc", "0", "0", "13"), "precision 13 refused"))
    cases.append(("geo", _segs("enc", "0", "0", "0"), "precision 0 refused"))
    cases.append(("geo", _segs("dec", "ezs4!"), "invalid char refused"))
    cases.append(("geo", _segs("dec", ""), "empty hash refused"))
    cases.append(("geo", _segs("adj", "ezs42", "X"), "bad direction refused"))
    rnd = random.Random(1461)
    for k in range(6):
        lat = rnd.uniform(-90, 90)
        lon = rnd.uniform(-180, 180)
        prec = rnd.randint(1, 12)
        cases.append(("geo", _segs("enc", "%.9f" % lat, "%.9f" % lon, str(prec)),
                      "seeded encode #%d" % k))
    for k in range(4):
        h = "".join("0123456789bcdefghjkmnpqrstuvwxyz"[rnd.randrange(32)]
                    for _ in range(rnd.randint(1, 12)))
        d = "NSEW"[rnd.randrange(4)]
        cases.append(("geo", _segs("adj", h, d), "seeded neighbour #%d" % k))

    # ------------------------------------------------------------ synonym ----
    rings = "color,colour,shade|big,huge,large|color,big,primary"
    cases.append(("synonym", _segs(rings, "the color of the sky"),
                  "first ring wins, dedupe"))
    cases.append(("synonym", _segs(rings, "unknown"), "no-ring term passes"))
    cases.append(("synonym", _segs(rings, ""), "empty query -> empty"))
    cases.append(("synonym", _segs("", "x"), "empty rings refused"))
    cases.append(("synonym", _segs(rings, "a " * 17), "17 query terms refused"))
    big_ring = ",".join("t%02d" % i for i in range(9))
    cases.append(("synonym", _segs(big_ring, "t00"), "ring of 9 refused"))
    dup_ring = "a,a"
    cases.append(("synonym", _segs(dup_ring, "a"), "duplicate in ring refused"))
    case_fold = _segs("Alpha,BETA", "COLORS")
    cases.append(("synonym", case_fold, "uppercase query folded"))
    many = ",".join("r%d,rr%d" % (i, i) for i in range(257)).replace(",", "|", 0)
    rings257 = "|".join("r%d,rr%d" % (i, i) for i in range(257))
    cases.append(("synonym", _segs(rings257, "r0"), "257 rings refused"))
    over_rings = "|".join(
        "q%d,%s" % (i, ",".join("p%d_%d" % (i, j) for j in range(7)))
        for i in range(16))
    over_query = " ".join("q%d" % i for i in range(16))
    cases.append(("synonym", _segs(over_rings, over_query),
                  "expansion over 64 refused"))

    # ------------------------------------------------------------ termurl ----
    text = "open https://example.com/a?b=1#c now, or www.x.org. Next."
    cases.append(("termurl", enc(text), "two urls + punctuation trim"))
    text2 = "see http://user:pass@host.example/path and http://ok.example/"
    cases.append(("termurl", enc(text2), "credentials redacted"))
    text3 = "xhttp://bad.example www. http://a.example"
    cases.append(("termurl", enc(text3), "word-boundary + bare www skipped"))
    text4 = "visit http://localhost:8080/x and ftp://files.example.org/pub"
    cases.append(("termurl", enc(text4), "port + ftp scheme"))
    cases.append(("termurl", enc("http://a.example:99999/x"),
                  "port 99999 invalid candidate skipped"))
    cases.append(("termurl", enc("http://nodot/x"),
                  "host without dot skipped"))
    cases.append(("termurl", enc("https://a.example/" + "p" * 3000),
                  "url over 2048 refused"))
    text5 = "one www.alpha.example two https://beta.example three"
    cases.append(("termurl", enc(text5), "multiple matches with offsets"))
    cases.append(("termurl", enc("mailto:someone@example.com skip"),
                  "unsupported scheme not matched"))
    text6 = "http://a.example, http://b.example;http://c.example"
    cases.append(("termurl", enc(text6), "comma/semicolon delimiters trimmed"))
    text7 = "http://[::1]/x"
    cases.append(("termurl", enc(text7), "ipv6 literal skipped"))
    text8 = "http://a..b.example/"
    cases.append(("termurl", enc(text8), "double-dot host skipped"))
    long_chain = " ".join("https://s%03d.example/" % i for i in range(260))
    cases.append(("termurl", enc(long_chain), "260 urls refused at cap"))

    # ------------------------------------------------------------ notetag ----
    corpus = "Project,Project/Alpha,project/beta,Inbox,Project/Alpha/task1"
    cases.append(("notetag", _segs(corpus, "validate", "-"), "canonical corpus"))
    cases.append(("notetag", _segs(corpus, "anc", "Project/Alpha/task1"),
                  "ancestor chain"))
    cases.append(("notetag", _segs(corpus, "anc", "Inbox"), "single segment"))
    cases.append(("notetag", _segs(corpus, "anc", "Missing"), "!MISSING ancestor"))
    cases.append(("notetag", _segs(corpus, "desc", "Project"),
                  "descendants sorted"))
    cases.append(("notetag", _segs(corpus, "desc", "Project/Alpha"),
                  "prefix subtree"))
    cases.append(("notetag", _segs(corpus, "desc", "Nothing"), "!MISSING desc"))
    cases.append(("notetag", _segs("#Weird/Tag,#Weird/Tag", "validate", "-"),
                  "duplicate after normalize refused"))
    cases.append(("notetag", _segs("bad tag", "validate", "-"),
                  "bad segment refused"))
    cases.append(("notetag", _segs(corpus, "anc", "a/" * 9 + "x"),
                  "9 segments refused"))
    cases.append(("notetag", _segs("", "validate", "-"), "empty corpus"))

    # ------------------------------------------------------------ editlog ----
    cases.append(("editlog", _segs("hello world") + "~" + "i0,XX|d5,1",
                  "insert+delete then fuse nothing"))
    cases.append(("editlog", _segs("ab") + "~" + "i1,c|d2,1",
                  "insert then delete"))
    cases.append(("editlog", _segs("abc") + "~" + "i0,x|d3,1",
                  "positions validated against live doc"))
    cases.append(("editlog", _segs("abc") + "~" + "d5,1",
                  "delete past end refused"))
    cases.append(("editlog", _segs("abc") + "~" + "i9,x",
                  "insert past end refused"))
    cases.append(("editlog", _segs("abc") + "~" + "d0,0",
                  "zero-length delete refused"))
    cases.append(("editlog", _segs("") + "~" + "",
                  "empty script"))
    cases.append(("editlog", _segs("abcdefghij") + "~" + "i0,123|d0,0",
                  "invalid op token refused") if False else
                  ("editlog", _segs("abcdefghij") + "~" + "d0,2|d0,3",
                   "consecutive same-pos deletes fuse"))
    cases.append(("editlog", _segs("abcdefghij") + "~" + "d0,1|d1,1",
                  "adjacent deletes do not fuse (documented rule)"))
    cases.append(("editlog", _segs("abcdefghij") + "~" + "i2,xy|d4,1",
                  "mixed script replayed"))
    # fusable consecutive inserts: after i0,x doc grows; next pos = 0+1
    cases.append(("editlog", _segs("base") + "~" + "i0,x|d1,1",
                  "insert then delete at shifted pos"))
    cases.append(("editlog", _segs("z" * 4000) + "~" + "i0," + enc("q" * 96),
                  "insert at doc cap refused after growth" if False else
                  "insert keeps doc at bound"))
    rnd = random.Random(1462)
    for k in range(6):
        base = "".join(rnd.choice("abcdefg ") for _ in range(60))
        tokens = []
        doc_len = len(base)
        for _ in range(rnd.randint(1, 6)):
            if rnd.random() < 0.6 and doc_len > 0:
                pos = rnd.randrange(doc_len + 1)
                text = "".join(rnd.choice("XYZ") for _ in range(rnd.randint(1, 3)))
                tokens.append("i%d,%s" % (pos, enc(text)))
                doc_len += len(text)
            elif doc_len > 0:
                pos = rnd.randrange(doc_len)
                ln = rnd.randint(1, min(4, doc_len - pos))
                tokens.append("d%d,%d" % (pos, ln))
                doc_len -= ln
        if tokens:
            cases.append(("editlog", _segs(base) + "~" + "|".join(tokens),
                          "seeded script #%d" % k))

    # -------------------------------------------------------------- macro ----
    wire = "FMC1;wait:500;tap:100:200;type:hi\\;there;launch:org.example.app"
    cases.append(("macro", enc(wire), "canonical round trip"))
    wire2 = "FMC1;type:a\\:b\\\\c"
    cases.append(("macro", enc(wire2), "escape round trip"))
    cases.append(("macro", enc("FMC1;"), "header only"))
    cases.append(("macro", enc("FMC0;wait:1"), "bad version refused"))
    cases.append(("macro", enc("FMC1;jump:1"), "unknown step refused"))
    cases.append(("macro", enc("FMC1;wait:60001"), "wait over 60s refused"))
    cases.append(("macro", enc("FMC1;tap:1001:0"), "tap permille refused"))
    cases.append(("macro", enc("FMC1;swipe:0:0:1000:1000:49"),
                  "swipe 49ms refused"))
    cases.append(("macro", enc("FMC1;type:x\\q"), "illegal escape refused"))
    cases.append(("macro", enc("FMC1;type:x\\"), "dangling escape refused"))
    cases.append(("macro", enc("FMC1;launch:UPPER.pkg"), "bad package refused"))
    cases.append(("macro", enc("FMC1;type:" + "t" * 513),
                  "type text over 512 refused"))
    cases.append(("macro", enc("FMC1;;wait:1"), "empty step refused"))
    cases.append(("macro", enc("FMC1;type:"), "empty type refused"))
    wire3 = "FMC1;swipe:10:20:30:40:500;type:" + "word " * 100
    cases.append(("macro", enc(wire3), "long canonical text"))

    # ---------------------------------------------------------------- ntp ----
    cases.append(("ntp", _segs("rev", "0"), "epoch 0 -> NTP offset"))
    cases.append(("ntp", _segs("rev", "1700000000000"), "modern epoch"))
    cases.append(("ntp", _segs("fwd", "1700000000000", "3874688000", "0"),
                  "era0 timestamp near now"))
    cases.append(("ntp", _segs("fwd", "0", "0", "0"), "wire zero resolves to 1900"))
    cases.append(("ntp", _segs("fwd", "1700000000000", "0", "2147483648"),
                  "half-second fraction floors"))
    cases.append(("ntp", _segs("fwd", "1700000000000", "4294967295", "4294967295"),
                  "uint32 max resolves inside window"))
    cases.append(("ntp", _segs("fwd", "0", "4294967295", "0"),
                  "era outside window refused"))
    cases.append(("ntp", _segs("fwd", "0", "4294967296", "0"),
                  "seconds 2^32 refused"))
    cases.append(("ntp", _segs("rev", "4102444800001"),
                  "epoch past 2100 refused"))
    cases.append(("ntp", _segs("rev", "-1"), "negative epoch refused"))
    # round trip vectors
    for k, ms in enumerate([1, 999, 1000, 2 ** 31, 4102444799999]):
        cases.append(("ntp", _segs("rev", str(ms)), "rev bound #%d" % k))
    cases.append(("ntp", _segs("fwd", "x", "1", "1"), "non-numeric ref refused"))

    # ----------------------------------------------------------- duration ----
    cases.append(("dur", enc("90s"), "plain seconds"))
    cases.append(("dur", enc("in 1h30m"), "in-prefix compound"))
    cases.append(("dur", enc("2 d 4 h"), "spaced compound"))
    cases.append(("dur", enc("in 0ms"), "zero -> 0"))
    cases.append(("dur", enc("1w"), "one week"))
    cases.append(("dur", enc(""), "empty refused"))
    cases.append(("dur", enc("in "), "bare prefix refused"))
    cases.append(("dur", enc("5x"), "unknown unit refused"))
    cases.append(("dur", enc("5"), "missing unit refused"))
    cases.append(("dur", enc("abc"), "leading text refused"))
    cases.append(("dur", enc("-5s"), "negative refused"))
    cases.append(("dur", enc("1.5h"), "fraction refused"))
    cases.append(("dur", enc("30d1s"), "over 30 days refused"))
    cases.append(("dur", enc("30d"), "exactly 30 days accepted"))
    cases.append(("dur", enc("1000000000h"), "component over 1e9 refused"))
    cases.append(("dur", enc("1h " * 9), "9 components refused"))
    cases.append(("dur", enc("0000000001s"), "10-digit leading zeros accepted"))

    # ------------------------------------------------------------ shuffle ----
    ids = ",".join("track%02d" % i for i in range(20))
    cases.append(("shuffle", _segs("seed-a", "3", ids), "seeded windowed deal"))
    cases.append(("shuffle", _segs("seed-a", "3", ids),
                  "same seed deterministic again"))
    cases.append(("shuffle", _segs("seed-b", "3", ids), "different seed"))
    cases.append(("shuffle", _segs("s", "1", "a,b"), "min queue window 1"))
    cases.append(("shuffle", _segs("s", "65", ids), "window 65 refused"))
    cases.append(("shuffle", _segs("", "2", ids), "empty seed refused"))
    cases.append(("shuffle", _segs("s", "2", "a,a"), "duplicate ids refused"))
    cases.append(("shuffle", _segs("s", "2", "a"), "single item refused"))
    cases.append(("shuffle", _segs("s", "0", ids), "window 0 refused"))
    ids2 = ",".join("i%d" % i for i in range(300))
    cases.append(("shuffle", _segs("x", "7", ids2), "300-item queue"))
    ids_unicode = ",".join(["ok"] + ["é%d" % i for i in range(4)])
    cases.append(("shuffle", _segs("u", "2", ids_unicode),
                  "unicode id refused by grammar"))
    cases.append(("shuffle", _segs("s", "2", ",".join("a.b_c-d" + str(i) for i in range(5))),
                  "charset-valid ids"))
    cases.append(("shuffle", _segs("seed " * 20, "2", ids), "60-char seed"))
    cases.append(("shuffle", _segs("s" * 65, "2", ids), "65-char seed refused"))

    # ---------------------------------------------------------------- sri ----
    d256 = hashlib.sha256(b"payload").digest()
    d384 = hashlib.sha384(b"payload").digest()
    d512 = hashlib.sha512(b"payload").digest()
    cases.append(("sri", enc("sha256-" + __import__("base64").b64encode(d256).decode()),
                  "sha256 valid"))
    cases.append(("sri", enc("sha384-" + __import__("base64").b64encode(d384).decode()),
                  "sha384 valid"))
    cases.append(("sri", enc("sha512-" + __import__("base64").b64encode(d512).decode()),
                  "sha512 valid"))
    cases.append(("sri", enc("sha256-AAAA sha512-" +
                             __import__("base64").b64encode(d512).decode()),
                  "ordered multi-token"))
    cases.append(("sri", enc("sha1-" + __import__("base64").b64encode(d256).decode()),
                  "sha1 refused"))
    cases.append(("sri", enc("sha256-" + __import__("base64").b64encode(d384).decode()),
                  "wrong digest length refused"))
    cases.append(("sri", enc("sha256-AAA"), "length not multiple of 4 refused"))
    cases.append(("sri", enc("sha256-" + __import__("base64").b64encode(d256).decode() +
                             " sha256-AAAA"), "duplicate algo refused"))
    b64 = __import__("base64").b64encode(d256).decode()
    # flip low pad bits of the final quantum (sha256: 32 -> 44 chars, pad=1)
    last_vals = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    group = b64[-4:]
    v2 = last_vals.index(group[2])
    if (v2 & 3) == 0:
        group2 = group[:2] + last_vals[v2 | 1] + "="
        cases.append(("sri", enc("sha256-" + b64[:-4] + group2),
                      "non-zero pad bits refused"))
    cases.append(("sri", enc("  sha256-" + b64 + "  "), "surrounding whitespace"))
    cases.append(("sri", enc(""), "empty refused"))
    cases.append(("sri", enc("sha256-" + "A" * 4), "all-A wrong length refused"))
    cases.append(("sri", enc("sha256-" + b64[:-1] + "$="),
                  "bad base64 char refused"))
    many = " ".join("sha256-" + b64 for _ in range(9))
    cases.append(("sri", enc(many), "9 tokens refused"))

    # ---------------------------------------------------------------- oem ----
    def s(t, cur, mx, temp):
        return "%d:%d:%d:%d" % (t, cur, mx, temp)
    light = ",".join([s(1000, 1500, 2000, 390), s(60000, 1500, 2000, 395),
                      s(120000, 1500, 2000, 400)])
    cases.append(("oem", light, "60s LIGHT episode"))
    cases.append(("oem", ",".join([s(0, 2000, 2000, 300), s(60000, 2000, 2000, 300)]),
                  "no cap -> none"))
    cases.append(("oem", s(0, 1000, 2000, 300), "single sample -> none"))
    cases.append(("oem", ",".join([s(0, 1400, 2000, 300), s(15000, 1400, 2000, 300)]),
                  "span under 30s dropped"))
    severe = ",".join([s(0, 1000, 2000, 300), s(310000, 1000, 2000, 300)])
    cases.append(("oem", severe, "310s SEVERE by duration"))
    crit = ",".join([s(0, 1000, 2000, 479), s(40000, 1000, 2000, 480)])
    cases.append(("oem", crit, "480 tenths -> CRITICAL"))
    hot = ",".join([s(0, 1000, 2000, 455), s(40000, 1000, 2000, 456)])
    cases.append(("oem", hot, "455 tenths -> SEVERE"))
    cases.append(("oem", ",".join([s(0, 1000, 2000, 300), s(31 * 60_000, 1000, 2000, 300)]),
                  "31-minute gap refused"))
    cases.append(("oem", ",".join([s(60000, 1000, 2000, 300), s(0, 1000, 2000, 300)]),
                  "unsorted timestamps refused"))
    cases.append(("oem", "0:0:2000:300", "zero frequency refused"))
    cases.append(("oem", "0:1000:2000:1201", "temperature over bound refused"))
    two_ep = ",".join([s(0, 1000, 2000, 300), s(40000, 1000, 2000, 300),
                       s(90000, 2000, 2000, 300),
                       s(200000, 1000, 2000, 300), s(240000, 1000, 2000, 300)])
    cases.append(("oem", two_ep, "second episode after recovery"))
    cases.append(("oem", "", "empty series refused"))

    # ------------------------------------------------------------- pwhist ----
    salt = "00" * 32
    cases.append(("pwhist", _segs(salt, "", "hunter22"), "first password stored"))
    pw_hash = hashlib.pbkdf2_hmac("sha256", b"hunter22", b"FHC2" + bytes(32), 600_000, 32)
    cases.append(("pwhist", _segs(salt, pw_hash.hex(), "hunter22"),
                  "reuse detected at index 0"))
    other = hashlib.pbkdf2_hmac("sha256", b"otherpw99", b"FHC2" + bytes(32), 600_000, 32)
    cases.append(("pwhist", _segs(salt, other.hex() + "," + pw_hash.hex(), "newpw1234"),
                  "new password appended"))
    cases.append(("pwhist", _segs(salt, "AB" * 32, "x"), "uppercase history refused"))
    cases.append(("pwhist", _segs("00" * 31, "", "x"), "31-byte salt refused"))
    cases.append(("pwhist", _segs("", "", "x"), "empty salt refused"))
    cases.append(("pwhist", _segs(salt, "", ""), "empty password refused"))
    full = ",".join(other.hex() for _ in range(12))
    cases.append(("pwhist", _segs(salt, full, "freshpw01"),
                  "12-entry history prunes oldest"))
    cases.append(("pwhist", _segs(salt, ",".join(["0" * 64] * 13), "x"),
                  "13-entry history refused"))
    cases.append(("pwhist", _segs(salt, "0" * 63, "x"), "short hash refused"))
    pw_emoji = "päss😀word1"
    cases.append(("pwhist", _segs(salt, "", pw_emoji), "utf-8 password"))
    cases.append(("pwhist", _segs(salt, "", "p" * 1025), "1025-char password refused"))

    # ----------------------------------------------------------- dupframe ----
    f1 = _fingerprint(1)
    f2 = _fingerprint(2)
    f3 = _fingerprint(3)
    near = _fingerprint(1, jitter=2)
    cases.append(("dupframe", "0:%s,500:%s,1000:%s" % (f1, f1, f1),
                  "three identical frames"))
    cases.append(("dupframe", "0:%s,500:%s,10000:%s" % (f1, f1, f2),
                  "gap over 5s splits run"))
    cases.append(("dupframe", "0:%s,500:%s" % (f1, near),
                  "2-bit near-duplicate joins"))
    cases.append(("dupframe", "0:%s,500:%s" % (f1, f2),
                  "distinct frames -> none"))
    cases.append(("dupframe", "0:%s" % f1, "single frame -> none"))
    cases.append(("dupframe", "500:%s,0:%s" % (f1, f2), "unsorted refused"))
    cases.append(("dupframe", "0:%s" % ("z" * 72), "non-hex fp refused"))
    cases.append(("dupframe", "0:%s" % ("a" * 71), "71-char fp refused"))
    cases.append(("dupframe", "", "empty refused"))
    chain = ",".join("%d:%s" % (i * 900, f1 if i < 5 else f2) for i in range(6))
    cases.append(("dupframe", chain, "5-frame run then new anchor"))
    many_frames = ",".join(
        "%d:%s" % (i, _fingerprint(9)) for i in range(4096))
    cases.append(("dupframe", many_frames, "4096-frame cap accepted"))

    # ------------------------------------------------------------ diagexp ----
    # Transport: allow segment is enc-ed whole; record structure (`,` and the
    # first `=`) stays raw while each VALUE is field-encoded, matching the
    # probe's split-before-decode lane.
    def _diag(allow: str, pairs) -> str:
        recs = ",".join(k + "=" + enc(v) for k, v in pairs)
        return enc(allow) + "~" + recs

    rich_pairs = [
        ("app", "com.example"),
        ("ip", "10.0.0.1"),
        ("uuid", "ab12cd34-1111-2222-3333-444455556666"),
        ("hash", "de" * 20),
        ("msg", "bearer abcdef123456"),
        ("path", "/data/user/0/com.example/files/x"),
        ("mail", "dev@example.org"),
        ("drop", "secret,value;=x"),
    ]
    rich = ",".join(k for k, _v in rich_pairs)
    cases.append(("diagexp", _diag(rich, rich_pairs), "all keys, all shapes"))
    cases.append(("diagexp", _diag("app,ip", rich_pairs), "drop non-allowlisted"))
    cases.append(("diagexp", _diag("app", [("app", "com.x"), ("app2", "com.y")]),
                  "dropped key is not an error"))
    cases.append(("diagexp", enc("") + "~" + "", "empty export"))
    cases.append(("diagexp", _diag("BadKey", [("app", "x")]), "bad allow key refused"))
    cases.append(("diagexp", _diag("a,a", [("x", "1")]), "dup allow refused"))
    cases.append(("diagexp", _diag("app", [("app", "x"), ("app", "y")]),
                  "dup record refused"))
    cases.append(("diagexp", _diag("app", []), "records field with no pairs"))
    cases.append(("diagexp", enc("app") + "~" + "noequals",
                  "missing equals refused"))
    cases.append(("diagexp", _diag("app", [("App", "x")]), "uppercase key refused"))
    cases.append(("diagexp", _diag("note", [("note", "ends token=abc rest")]),
                  "token= mid-value scrubbed"))
    cases.append(("diagexp", _diag("phone", [("phone", "call 5551234567 now")]),
                  "phone-shaped digit run"))
    many_pairs = [("k%02d" % i, "v%d" % i) for i in range(256)]
    cases.append(("diagexp", _diag("k00", many_pairs), "256-record cap"))
    many_pairs2 = [("k%02d" % i, "v%d" % i) for i in range(257)]
    cases.append(("diagexp", _diag("k00", many_pairs2), "257 records refused"))
    cases.append(("diagexp", _diag("app", [("app", "v" * 1025)]),
                  "value over 1024"))
    cases.append(("diagexp", _diag("msg", [("msg", "bearer tok1"), ("ip", "1.2.3.4")]),
                  "scrub order bearer then others"))

    # ----------------------------------------------------------- msgthread ----
    def msg(mid, parent, text):
        return "%s;%s;%s" % (mid, parent or "", enc(text))

    cases.append(("msgthread", ",".join([msg("root", None, "hi"),
                                         msg("kid", "root", "yo")]),
                  "simple thread"))
    cases.append(("msgthread", ",".join([msg("kid", "root", "yo"),
                                         msg("root", None, "hi")]),
                  "forward reference parent"))
    cases.append(("msgthread", ",".join([msg("a", "b", "x"),
                                         msg("b", "a", "y")]),
                  "forward two-cycle refused"))
    cases.append(("msgthread", ",".join([msg("root", None, "ok"),
                                         msg("loop", "loop", "self")]),
                  "self-parent unreachable refused"))
    cases.append(("msgthread", ",".join([msg("a", None, "a"),
                                         msg("b", "a", "b"),
                                         msg("c", "d", "c"),
                                         msg("d", "c", "d")]),
                  "detached cycle refused"))
    cases.append(("msgthread", ",".join([msg("root", None, "r"),
                                         msg("c1", "root", "1"),
                                         msg("c2", "root", "2")]),
                  "ordered siblings"))
    cases.append(("msgthread", "", "empty corpus"))
    cases.append(("msgthread", msg("bad id", None, "x"),
                  "invalid id refused"))
    cases.append(("msgthread", msg("a", None, "t" * 1025), "text over cap"))
    deep = [msg("m0", None, "root")]
    deep += [msg("m%d" % i, "m%d" % (i - 1), "x") for i in range(1, 80)]
    cases.append(("msgthread", ",".join(deep), "80-deep chain, depth-bounded"))
    orphan = ",".join([msg("x", "missingparent", "t")])
    cases.append(("msgthread", orphan, "missing parent -> root (documented)"))

    # Repair-adversary vectors: typed-API limits, output re-import and
    # line-injection attempts after transport decoding.
    cases.extend([
        ("editlog", enc("a" * 4096) + "~i4096,x", "final document bound refused"),
        ("editlog", "~" + "|".join("i0," for _ in range(513)), "structured op-count bound refused"),
        ("editlog", "a~d2147483647,1", "delete range overflow refused"),
        ("editlog", "~i0," + "x" * 256 + "|i256," + "y" * 256, "fused output remains importable"),
        ("diagexp", "msg~msg=" + enc("safe\nforged=secret"), "decoded line injection refused"),
        ("diagexp", "msg~msg=" + enc("safe\x1b[31m"), "terminal escape refused"),
        ("diagexp", "msg~msg=" + enc("100%,;= literal"), "literal punctuation retained"),
    ])

    return cases
