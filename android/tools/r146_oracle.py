#!/usr/bin/env python3
"""R146 independent Python oracle for the twenty-one R146 production lanes.

Each `_<feature>` function is written from the contract in the Kotlin KDoc,
NOT transcribed from the Kotlin body: it uses Python's own integer
arithmetic, its own regexes, its own SHA-256 and its own float formatting.
`oracle(feature, payload)` applies the same transport grammar as the probe
(structure splits before decoding) and raises `Refusal` where the contract
says the kernel must refuse; the runner maps `Refusal` to `!REF`.

Transport (shared with the probe, deliberately trivial): percent-encode
`% , | ~ ; # : = @` and every code point < 0x20 as `%XX` uppercase hex.
"""
from __future__ import annotations

import base64
import hashlib
import re
import string
import struct
from decimal import ROUND_HALF_UP, Decimal

SPECIALS = set("%,|~;#:=@\t\n\r")


class Refusal(Exception):
    """The contract requires refusing this input."""


def enc(s: str) -> str:
    out = []
    for ch in s:
        code = ord(ch)
        if ch in SPECIALS or code < 0x20:
            out.append("%%%02X" % code)
        else:
            out.append(ch)
    return "".join(out)


def dec(s: str) -> str:
    out = []
    i = 0
    while i < len(s):
        if s[i] == "%":
            if i + 2 >= len(s):
                raise Refusal("truncated escape")
            try:
                val = int(s[i + 1:i + 3], 16)
            except ValueError:
                raise Refusal("bad escape") from None
            out.append(chr(val))
            i += 3
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def _segs(payload: str) -> list[str]:
    return [dec(p) for p in payload.split("~")]


def _require(cond: bool, msg: str = "refused") -> None:
    if not cond:
        raise Refusal(msg)


# ---------------------------------------------------------------- inexact ---

_MIN_INTERVAL = 60_000
_MAX_INTERVAL = 30 * 86_400_000
_MIN_GRID = 60_000
_MAX_GRID = 86_400_000
_HORIZON = 30 * 86_400_000
_ID_RE = re.compile(r"[A-Za-z0-9_-]{1,32}")


def _inexact(payload: str) -> str:
    p = _segs(payload)
    _require(len(p) == 3, "3 segments")
    now = _as_long(p[0])
    grid = _as_long(p[1])
    _require(now >= 0)
    _require(_MIN_GRID <= grid <= _MAX_GRID)
    specs = p[2].split(",")
    _require(len(specs) >= 1)
    _require(len(specs) <= 64)
    alarms = []
    seen = set()
    for spec in specs:
        f = spec.split(":")
        _require(len(f) == 5)
        aid, interval, due, flex, exact = f
        _require(_ID_RE.fullmatch(aid) is not None, "id")
        _require(aid not in seen, "dup id")
        seen.add(aid)
        interval = _as_long(interval)
        due = _as_long(due)
        flex = _as_long(flex)
        _require(_MIN_INTERVAL <= interval <= _MAX_INTERVAL)
        _require(0 <= flex <= interval // 4)
        _require(due >= 0)
        _require(exact in ("0", "1"))
        alarms.append((aid, interval, due, flex, exact == "1"))

    planned = []
    for aid, interval, due, flex, exact in alarms:
        if due >= now:
            nxt = due
        else:
            k = (now - due) // interval + 1
            nxt = due + k * interval
        _require(nxt >= now)
        _require(nxt - now <= _HORIZON, "horizon")
        if exact:
            planned.append((aid, True, nxt, nxt, nxt))
        else:
            rem = nxt % grid
            start = nxt if rem == 0 else nxt + (grid - rem)
            _require(start - now <= _HORIZON + _MAX_GRID, "grid horizon")
            planned.append((aid, False, nxt, start, start + flex))

    batches: dict[int, list[str]] = {}
    for aid, exact, nxt, start, _end in planned:
        key = nxt if exact else start
        batches.setdefault(key, []).append(aid)
    per_alarm = ",".join(
        "%s:%s:%d:%d:%d" % (aid, "EXACT" if exact else "INEXACT", nxt, start, end)
        for aid, exact, nxt, start, end in planned
    )
    batched = ",".join(
        "%d:%s" % (key, "+".join(sorted(ids))) for key, ids in sorted(batches.items())
    )
    return per_alarm + "#" + batched


# --------------------------------------------------------------- keytouch ---

_MODS = ["ctrl", "alt", "shift", "meta"]
_KEYS = {"enter", "tab", "esc", "space", "backspace", "delete", "up", "down",
         "left", "right", "home", "end", "pageup", "pagedown"} | {
            "f%d" % n for n in range(1, 13)} | set(string.ascii_lowercase) | set(string.digits)
_APP_RE = re.compile(r"[a-z0-9._-]{1,64}")
_PASS_RE = re.compile(r"[A-Za-z0-9_]{1,16}")


def _canonicalize(chord: str) -> str:
    _require(len(chord) <= 64, "chord long")
    parts = chord.split("+")
    _require(1 <= len(parts) <= 5, "chord parts")
    key = parts[-1].lower()
    _require(key in _KEYS, "unknown key")
    mods = [m.lower() for m in parts[:-1]]
    _require(len(mods) == len(set(mods)), "dup modifier")
    _require(mods == sorted(mods, key=_MODS.index) if all(m in _MODS for m in mods)
             else False, "modifier order")
    _require(all(m in _MODS for m in mods), "unknown modifier")
    return "+".join(mods + [key])


def _is_reserved(chord: str) -> bool:
    c = _canonicalize(chord)
    return c == "ctrl+alt+delete" or c.startswith("meta")


def _permille(raw: str) -> int:
    try:
        v = int(raw, 10)
    except ValueError:
        raise Refusal("coordinate") from None
    _require(0 <= v <= 1000, "permille")
    return v


def _parse_action(spec: str) -> str:
    parts = spec.split(":")
    if parts[0] == "tap":
        _require(len(parts) == 3)
        _permille(parts[1]); _permille(parts[2])
    elif parts[0] == "swipe":
        _require(len(parts) == 2)
        _require(parts[1] in ("up", "down", "left", "right"))
    elif parts[0] == "hold":
        _require(len(parts) == 4)
        _permille(parts[1]); _permille(parts[2])
        try:
            ms = int(parts[3], 10)
        except ValueError:
            raise Refusal("hold ms") from None
        _require(50 <= ms <= 5000, "hold range")
    elif parts[0] == "pass":
        _require(len(parts) == 2)
        _require(_PASS_RE.fullmatch(parts[1]) is not None, "pass code")
    else:
        raise Refusal("unknown action")
    return spec


def _parse_table(spec: str) -> dict[str, str]:
    _require(spec != "", "empty table")
    entries = spec.split(",")
    _require(len(entries) <= 64, "too many mappings")
    table: dict[str, str] = {}
    for entry in entries:
        arrow = entry.find("=>")
        _require(arrow > 0, "chord=>action")
        chord = _canonicalize(entry[:arrow])
        _require(not _is_reserved(chord), "reserved chord")
        action = _parse_action(entry[arrow + 2:])
        _require(chord not in table, "dup mapping")
        table[chord] = action
    return table


def _keyres(payload: str) -> str:
    p = _segs(payload)
    _require(len(p) == 2)
    table = _parse_table(p[0])
    c = _canonicalize(p[1])
    return table.get(c, "UNMAPPED")


def _keyseq(payload: str) -> str:
    p = _segs(payload)
    _require(len(p) == 3)
    table = _parse_table(p[0])
    cooldown = _as_int(p[2])
    _require(0 <= cooldown <= 5000, "cooldown")
    chords = [] if p[1] == "" else p[1].split(",")
    _require(len(chords) <= 256, "sequence")
    out = []
    last_action = None
    last_ts = -1
    prev_ts = -1
    for raw in chords:
        at = raw.rfind("@")
        _require(0 < at < len(raw) - 1, "chord@ts")
        c = _canonicalize(raw[:at])
        try:
            ts = int(raw[at + 1:], 10)
        except ValueError:
            raise Refusal("ts") from None
        _require(ts >= 0, "ts >= 0")
        _require(ts >= prev_ts, "ts monotonic")
        prev_ts = ts
        _require(c in table, "unmapped chord in sequence")
        rendered = table[c]
        suppressed = (cooldown > 0 and rendered == last_action
                      and last_ts >= 0 and ts - last_ts <= cooldown)
        if suppressed:
            out.append("-")
        else:
            out.append(rendered)
            last_action = rendered
            last_ts = ts
    return ",".join(out)


# -------------------------------------------------------------------- pdf ---

_MAX_COORD = 1_000_000.0


def _fmt_java(v: float, places: int) -> str:
    """Mirror java.util.Locale.ROOT String.format("%.<p>f", v).

    Empirically (JRE 17): Java formats from the *shortest round-trip*
    decimal of the double (Double.toString) and rounds the cut with
    HALF_UP — e.g. %.8f of 42.626953125 -> 42.62695313 while Python's
    native formatting rounds half-to-even. Decimal(str(v)) reproduces the
    shortest form; ROUND_HALF_UP reproduces the cut.
    """
    d = Decimal(str(v))
    q = d.quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP)
    return "%.*f" % (places, q)


def _pdf_check_coord(v: float) -> float:
    _require(v == v and abs(v) != float("inf") and -_MAX_COORD <= v <= _MAX_COORD,
             "coord range")
    return v


def _pdf_box(spec: str):
    p = spec.split(",")
    _require(len(p) == 4)
    x0, y0, x1, y1 = (_pdf_check_coord(_as_float(t)) for t in p)
    _require(x0 < x1 and y0 < y1, "box")
    return (x0, y0, x1, y1)


def _intersect(a, b):
    x0 = max(a[0], b[0]); y0 = max(a[1], b[1])
    x1 = min(a[2], b[2]); y1 = min(a[3], b[3])
    if x0 < x1 and y0 < y1:
        return (x0, y0, x1, y1)
    return None


def _fmt4(v: float) -> str:
    return _fmt_java(v, 4)


def _pdf(payload: str) -> str:
    parts = payload.split("|")
    _require(len(parts) == 7, "7 fields")
    page = _pdf_box(dec(parts[0]))
    typ = dec(parts[1])
    opacity = _as_float(dec(parts[2]))
    _require(0.0 < opacity <= 1.0, "opacity")
    flags = _as_int(dec(parts[3]))
    _require(0 <= flags <= (1 | 4 | 32) and (flags & ~(1 | 4 | 32)) == 0, "flags")
    author = dec(parts[4])
    contents = dec(parts[5])
    geometry = dec(parts[6])
    _require(len(author) <= 255, "author")
    _require(len(contents) <= 32_607, "contents")

    def inside(x, y):
        return page[0] <= x <= page[2] and page[1] <= y <= page[3]

    if typ == "TEXT":
        g = geometry.split(",")
        _require(len(g) == 2)
        x = _pdf_check_coord(_as_float(g[0]))
        y = _pdf_check_coord(_as_float(g[1]))
        _require(inside(x, y), "anchor")
        rect = (x, y, min(x + 20.0, page[2]), min(y + 20.0, page[3]))
        clamped = _intersect(page, rect)
        _require(clamped is not None, "note rect")
        return ("TEXT|rect=%s|quad=-|opacity=%s|flags=%d|author=%s|contents=%s" % (
            ",".join(_fmt4(v) for v in clamped), _fmt4(opacity), flags, author, contents))
    if typ == "HIGHLIGHT":
        rects = [] if geometry == "" else geometry.split(";")
        _require(1 <= len(rects) <= 512, "quads")
        quads = []
        union = None
        for r in rects:
            c = r.split(",")
            _require(len(c) == 4)
            raw = tuple(_pdf_check_coord(_as_float(t)) for t in c)
            _require(raw[0] < raw[2] and raw[1] < raw[3], "empty quad")
            clamped = _intersect(page, raw)
            _require(clamped is not None, "quad misses page")
            quads.append("q(%s,%s,%s,%s,%s,%s,%s,%s)" % (
                _fmt4(clamped[0]), _fmt4(clamped[1]), _fmt4(clamped[2]), _fmt4(clamped[1]),
                _fmt4(clamped[2]), _fmt4(clamped[3]), _fmt4(clamped[0]), _fmt4(clamped[3])))
            if union is None:
                union = list(clamped)
            else:
                union[0] = min(union[0], clamped[0])
                union[1] = min(union[1], clamped[1])
                union[2] = max(union[2], clamped[2])
                union[3] = max(union[3], clamped[3])
        _require(union is not None, "no rects")
        return ("HIGHLIGHT|rect=%s|quad=%s|opacity=%s|flags=%d|author=%s|contents=%s" % (
            ",".join(_fmt4(v) for v in union), ";".join(quads), _fmt4(opacity),
            flags, author, contents))
    if typ == "INK":
        pts = [] if geometry == "" else geometry.split(";")
        _require(2 <= len(pts) <= 4096, "ink points")
        xs, ys, rendered = [], [], []
        for pt in pts:
            c = pt.split(",")
            _require(len(c) == 2)
            x = _pdf_check_coord(_as_float(c[0]))
            y = _pdf_check_coord(_as_float(c[1]))
            _require(inside(x, y), "ink outside")
            xs.append(x); ys.append(y)
            rendered.append("%s,%s" % (_fmt4(x), _fmt4(y)))
        rect = (min(xs), min(ys), max(xs), max(ys))
        return ("INK|rect=%s|quad=p(%s)|opacity=%s|flags=%d|author=%s|contents=%s" % (
            ",".join(_fmt4(v) for v in rect), ";".join(rendered), _fmt4(opacity),
            flags, author, contents))
    raise Refusal("unknown type")


# ------------------------------------------------------------------- cpio ---

def _cpio(payload: str) -> str:
    stream = bytes.fromhex(payload)
    _require(len(stream) <= (1 << 30), "stream cap")
    pos = 0
    members = []
    MAX_ENTRIES = 4095
    while True:
        _require(pos + 110 <= len(stream), "truncated header")
        magic = stream[pos:pos + 6].decode("ascii", "replace")
        _require(magic in ("070701", "070702"), "magic")
        fields = []
        for k in range(13):
            raw = stream[pos + 6 + k * 8: pos + 14 + k * 8].decode("ascii", "replace")
            _require(re.fullmatch(r"[0-9a-fA-F]{8}", raw) is not None, "hex field")
            fields.append(int(raw, 16))
        (ino, mode, uid, gid, nlink, mtime, filesize,
         _dmj, _dmn, _rmj, _rmn, namesize, _check) = fields
        _require(2 <= namesize <= 256, "namesize")
        _require(filesize <= (1 << 30), "member cap")
        _require(nlink >= 1, "nlink")
        name_start = pos + 110
        _require(name_start + namesize <= len(stream), "truncated name")
        name_bytes = stream[name_start:name_start + namesize]
        _require(name_bytes[-1] == 0, "name NUL")
        _require(0 not in name_bytes[:-1], "interior NUL")
        name = name_bytes[:-1].decode("utf-8")
        after = name_start + namesize
        pad = (4 - (after % 4)) % 4
        data_start = after + pad
        _require(data_start <= len(stream), "truncated name pad")
        data_end = data_start + filesize
        _require(data_end <= len(stream), "truncated payload")
        if name == "TRAILER!!!":
            trailer_end = data_end + ((4 - (data_end % 4)) % 4)
            _require(trailer_end <= len(stream), "trailer pad")
            tail = len(stream) - trailer_end
            _require(tail <= 511, "after trailer")
            _require(all(b == 0 for b in stream[trailer_end:]), "nonzero fill")
            _require(len(members) <= MAX_ENTRIES, "too many")
            body = "\n".join(
                "%d:%x:%d:%d:%d:%d:%s" % (i, mo, u, g, mt, sz, nm)
                for (nm, sz, mo, u, g, mt, i) in members)
            return body + "\n#trailer=true"
        _require(len(members) < MAX_ENTRIES, "too many members")
        members.append((name, filesize, mode, uid, gid, mtime, ino))
        nxt = data_end + ((4 - (data_end % 4)) % 4)
        _require(nxt > pos, "forward progress")
        pos = nxt
        _require(pos <= len(stream), "truncated pad")


# -------------------------------------------------------------------- psd ---

def _u16(b: bytes, at: int) -> int:
    return (b[at] << 8) | b[at + 1]


def _u32(b: bytes, at: int) -> int:
    return struct.unpack_from(">I", b, at)[0]


def _psd(payload: str) -> str:
    f = bytes.fromhex(payload)
    _require(len(f) >= 26, "header")
    _require(f[0:4] == b"8BPS", "sig")
    _require(_u16(f, 4) == 1, "version")
    channels = _u16(f, 12)
    _require(1 <= channels <= 56, "channels")
    rows = _u32(f, 14); cols = _u32(f, 18)
    _require(1 <= rows <= 30000 and 1 <= cols <= 30000, "dims")
    depth = _u16(f, 22)
    _require(depth in (1, 8, 16, 32), "depth")
    mode = _u16(f, 24)
    _require(0 <= mode <= 9, "colormode")
    pos = 26
    _require(pos + 4 <= len(f), "color len")
    color_len = _u32(f, pos)
    _require(color_len <= (1 << 24), "color cap")
    pos += 4 + color_len
    _require(pos <= len(f), "color data")
    _require(pos + 4 <= len(f), "res len")
    section_len = _u32(f, pos)
    _require(section_len <= (1 << 24), "section cap")
    pos += 4
    end = pos + section_len
    _require(end <= len(f), "section data")
    count = 0
    thumb = None
    while pos < end:
        _require(count < 1024, "resources")
        _require(pos + 12 <= end, "res header")
        _require(f[pos:pos + 4] == b"8BIM", "8BIM")
        rid = _u16(f, pos + 4)
        name_len = f[pos + 6]
        name_total = 1 + name_len
        name_padded = name_total + (name_total & 1)
        size_at = pos + 6 + name_padded
        _require(size_at + 4 <= end, "res size")
        size = _u32(f, size_at)
        data_at = size_at + 4
        _require(size <= end - data_at, "res overflow")
        if rid in (1033, 1036):
            _require(size >= 22, "thumb small")
            fmt = _u16(f, data_at)
            _require(fmt in (0, 1), "thumb fmt")
            w = _u32(f, data_at + 2); h = _u32(f, data_at + 6)
            width_bytes = _u32(f, data_at + 10)
            total = _u32(f, data_at + 14)
            comp = _u32(f, data_at + 18)
            _require(1 <= w <= 4096 and 1 <= h <= 4096, "thumb dims")
            _require(width_bytes >= 3 and total >= comp, "thumb sizes")
            _require(comp + 22 <= size, "thumb overflow")
            if fmt == 1:
                j = data_at + 22
                _require(f[j:j + 3] == b"\xff\xd8\xff", "jpeg SOI")
            thumb = (rid, fmt, w, h, comp)
        count += 1
        data_end = data_at + size
        nxt = data_end + (size & 1)
        _require(nxt > pos, "progress")
        _require(nxt <= end, "pad")
        pos = nxt
    _require(pos == end, "section end")
    if thumb is None:
        return "nres=%d;thumb=none" % count
    return "nres=%d;thumb=%d:%d:%d:%d:%d" % ((count,) + thumb)


# ------------------------------------------------------------------ odfmeta ---

_MAX_META = 2_000_000
_META_ALLOWED = {"dc:title", "dc:language", "dc:subject", "meta:keyword"}
_TAG_OK = re.compile(r"[A-Za-z0-9_.:-]+")


def _odfmeta(payload: str) -> str:
    text = dec(payload)
    _require(len(text) <= _MAX_META, "size")
    open_tok = "<office:meta>"
    close_tok = "</office:meta>"
    open_at = text.find(open_tok)
    _require(open_at >= 0, "missing open")
    _require(text.find(open_tok, open_at + len(open_tok)) < 0, "dup open")
    inner_start = open_at + len(open_tok)
    close_at = text.find(close_tok, inner_start)
    _require(close_at >= 0, "missing close")
    _require(text.find(close_tok, close_at + len(close_tok)) < 0, "dup close")
    inner = text[inner_start:close_at]
    return text[:inner_start] + _scrub_children(inner) + text[close_at:]


def _read_open_tag(s: str, at: int):
    j = at + 1
    name_start = j
    while j < len(s) and (s[j].isalnum() or s[j] in "_:.-"):
        j += 1
    _require(j > name_start, "tag name")
    name = s[name_start:j]
    _require(_TAG_OK.fullmatch(name) is not None, "tag charset")
    quote = None
    while j < len(s):
        ch = s[j]
        if quote is not None:
            if ch == quote:
                quote = None
        elif ch in "\"'":
            quote = ch
        elif ch == ">":
            self_closing = j > at + 1 and s[j - 1] == "/"
            return name, j + 1, self_closing
        j += 1
    raise Refusal("unterminated tag")


def _scrub_children(inner: str) -> str:
    out = []
    i = 0
    while i < len(inner):
        if inner[i] != "<":
            out.append(inner[i])
            i += 1
            continue
        if inner.startswith("<!--", i):
            end = inner.find("-->", i + 4)
            _require(end >= 0, "comment")
            i = end + 3
            continue
        _require(not inner.startswith("</", i), "stray close")
        tag, open_end, self_closing = _read_open_tag(inner, i)
        if self_closing:
            if tag in _META_ALLOWED:
                out.append(inner[i:open_end])
            i = open_end
            continue
        close_seq = "</%s>" % tag
        close_at = inner.find(close_seq, open_end)
        _require(close_at >= 0, "unterminated child")
        block_end = close_at + len(close_seq)
        _require("<" not in inner[open_end:close_at], "nested")
        if tag in _META_ALLOWED:
            out.append(inner[i:block_end])
        i = block_end
    return "".join(out)


# ----------------------------------------------------------------- geohash ---

_GEO_ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"


def _geohash_enc(lat: float, lon: float, prec: int) -> str:
    _require(lat == lat and -90.0 <= lat <= 90.0, "lat")
    _require(lon == lon and -180.0 <= lon <= 180.0, "lon")
    _require(1 <= prec <= 12, "precision")
    lat_lo, lat_hi = -90.0, 90.0
    lon_lo, lon_hi = -180.0, 180.0
    out = []
    bit = 0
    ch = 0
    even = True
    while len(out) < prec:
        if even:
            mid = (lon_lo + lon_hi) / 2.0
            if lon >= mid:
                ch |= 1 << (4 - bit)
                lon_lo = mid
            else:
                lon_hi = mid
        else:
            mid = (lat_lo + lat_hi) / 2.0
            if lat >= mid:
                ch |= 1 << (4 - bit)
                lat_lo = mid
            else:
                lat_hi = mid
        even = not even
        bit += 1
        if bit == 5:
            out.append(_GEO_ALPHABET[ch])
            ch = 0
            bit = 0
    return "".join(out)


def _geohash_dec(h: str):
    _require(1 <= len(h) <= 12, "len")
    lat_lo, lat_hi = -90.0, 90.0
    lon_lo, lon_hi = -180.0, 180.0
    even = True
    for c in h:
        idx = _GEO_ALPHABET.find(c)
        _require(idx >= 0, "char")
        for shift in range(4, -1, -1):
            set_ = (idx >> shift) & 1
            if even:
                mid = (lon_lo + lon_hi) / 2.0
                if set_:
                    lon_lo = mid
                else:
                    lon_hi = mid
            else:
                mid = (lat_lo + lat_hi) / 2.0
                if set_:
                    lat_lo = mid
                else:
                    lat_hi = mid
            even = not even
    return lat_lo, lon_lo, lat_hi, lon_hi


def _geo(payload: str) -> str:
    p = _segs(payload)
    if p[0] == "enc":
        _require(len(p) == 4)
        return _geohash_enc(_as_float(p[1]), _as_float(p[2]), _as_int(p[3]))
    if p[0] == "dec":
        _require(len(p) == 2)
        box = _geohash_dec(p[1])
        return ",".join(_fmt_java(v, 8) for v in box)
    _require(len(p) == 3)
    direction = p[2]
    _require(direction in ("N", "S", "E", "W"), "dir")
    box = _geohash_dec(p[1])
    lat_span = box[2] - box[0]
    lon_span = box[3] - box[1]
    clat = (box[0] + box[2]) / 2.0
    clon = (box[1] + box[3]) / 2.0
    if direction == "N":
        nlat, nlon = clat + lat_span, clon
    elif direction == "S":
        nlat, nlon = clat - lat_span, clon
    elif direction == "E":
        nlat, nlon = clat, clon + lon_span
    else:
        nlat, nlon = clat, clon - lon_span
    while nlon > 180.0:
        nlon -= 360.0
    while nlon < -180.0:
        nlon += 360.0
    _require(-90.0 <= nlat <= 90.0, "pole")
    return _geohash_enc(nlat, nlon, len(p[1]))


# ---------------------------------------------------------------- synonyms ---

_TERM_RE = re.compile(r"[a-z0-9_-]{1,32}")


def _synonym(payload: str) -> str:
    p = _segs(payload)
    _require(len(p) == 2)
    ring_spec, query = p
    _require(ring_spec != "", "rings")
    rings = []
    raw_rings = ring_spec.split("|")
    _require(len(raw_rings) <= 256, "rings")
    for rr in raw_rings:
        terms = rr.split(",")
        _require(1 <= len(terms) <= 8, "ring size")
        norm = [t.lower() for t in terms]
        for t in norm:
            _require(_TERM_RE.fullmatch(t) is not None, "ring term")
        _require(len(set(norm)) == len(norm), "ring dup")
        rings.append(norm)
    terms = [] if query.strip() == "" else query.strip().split()
    _require(len(terms) <= 16, "query terms")
    for t in terms:
        _require(_TERM_RE.fullmatch(t.lower()) is not None, "query term")
    out: list[str] = []
    seen = set()
    for raw in terms:
        term = raw.lower()
        if term not in seen:
            seen.add(term)
            out.append(term)
        ring = next((r for r in rings if term in r), None)
        if ring is None:
            continue
        for partner in ring:
            if partner not in seen:
                seen.add(partner)
                out.append(partner)
            _require(len(out) <= 64, "expansion cap")
        _require(len(out) <= 64, "expansion cap")
    return " ".join(out)


# ----------------------------------------------------------------- termurl ---

def _region_matches_ci(text: str, i: int, needle: str) -> bool:
    """String.regionMatches(ignoreCase=true) parity for an ASCII needle."""
    if i < 0 or i + len(needle) > len(text):
        return False
    for k, nc in enumerate(needle):
        tc = text[i + k]
        if not (tc.upper() == nc.upper() or tc.lower() == nc.lower()):
            return False
    return True


_MAX_TEXT = 65_536
_SCHEMES = ("http://", "https://", "ftp://")
_TRAILING = ".,;:!?)]}'\""
_STOP = " \t\n\r<>\""


def _termurl(payload: str) -> str:
    text = dec(payload)
    _require(len(text) <= _MAX_TEXT, "size")
    hits = []
    i = 0
    while i < len(text):
        scheme_len = None
        for sc in _SCHEMES:
            if _region_matches_ci(text, i, sc):
                scheme_len = len(sc)
                break
        www = scheme_len is None and _region_matches_ci(text, i, "www.")
        if scheme_len is None and not www:
            i += 1
            continue
        start = i
        if start > 0 and text[start - 1].isalnum():
            i += 1
            continue
        auth_start = i + (scheme_len or 0)
        k = auth_start
        while k < len(text) and text[k] not in _STOP:
            k += 1
        if k == auth_start:
            i += 1
            continue
        end = k
        while end > auth_start and text[end - 1] in _TRAILING:
            end -= 1
        if end <= auth_start:
            i += 1
            continue
        if end - start > 2048:
            raise Refusal("url too long")
        if not _termurl_valid(text, start, auth_start, end):
            i = start + 1
            continue
        authority = _authority_of(text, auth_start, end)
        sep = authority.rfind("@")
        if sep >= 0:
            url = text[start:auth_start] + "!CRED!" + authority[sep:] + \
                text[auth_start + len(authority):end]
        else:
            url = text[start:end]
        _require(len(hits) < 256, "url cap")
        hits.append("%d:%s" % (start, url))
        i = end
    return ",".join(hits)


def _authority_of(text, auth_start, end):
    cut = next((x for x in range(auth_start, end) if text[x] in "/?#"), end)
    return text[auth_start:cut]


def _termurl_valid(text, start, auth_start, end):
    if end <= start:
        return False
    authority = _authority_of(text, auth_start, end)
    if authority == "":
        return False
    sep = authority.rfind("@")
    if sep >= 0:
        if sep == 0:
            return False
        host_port = authority[sep + 1:]
    else:
        host_port = authority
    if host_port == "":
        return False
    colon = host_port.find(":")
    if colon >= 0:
        if colon != host_port.rfind(":"):
            return False
        host = host_port[:colon]
        port = host_port[colon + 1:]
        if not (1 <= len(port) <= 5):
            return False
        if not port.isdigit():
            return False
        if int(port) > 65535:
            return False
    else:
        host = host_port
    if "." not in host:
        return False
    if re.fullmatch(r"[A-Za-z0-9.-]+", host) is None:
        return False
    if host.startswith(".") or host.endswith(".") or ".." in host:
        return False
    if any(ch in _STOP or ch in "[]" for ch in authority):
        return False
    return True


# ------------------------------------------------------------------ notetag ---

_SEG_RE = re.compile(r"[a-z0-9_-]{1,64}")


def _norm_tag(raw: str) -> str:
    t = raw.strip().lstrip("#").lower()
    _require(t != "", "empty tag")
    segs = t.split("/")
    _require(len(segs) <= 8, "segments")
    for s in segs:
        _require(len(s) <= 64, "segment len")
        _require(_SEG_RE.fullmatch(s) is not None, "segment")
    return "/".join(segs)


def _notetag(payload: str) -> str:
    p = _segs(payload)
    _require(len(p) == 3)
    corpus_raw = [] if p[0] == "" else p[0].split(",")
    _require(len(corpus_raw) <= 128, "tags")
    corpus = []
    seen = set()
    for raw in corpus_raw:
        norm = _norm_tag(raw)
        _require(norm not in seen, "dup tag")
        seen.add(norm)
        corpus.append(norm)
    if p[1] == "validate":
        return ",".join(corpus)
    if p[1] == "anc":
        norm = _norm_tag(p[2])
        if norm not in seen:
            return "!MISSING"
        segs = norm.split("/")
        # Mirrors NoteTagHierarchy.ancestors: prefixes joined with ",".
        return ",".join("/".join(segs[:i]) for i in range(1, len(segs) + 1))
    if p[1] == "desc":
        norm = _norm_tag(p[2])
        hits = sorted(t for t in corpus if t == norm or t.startswith(norm + "/"))
        if not hits:
            return "!MISSING"
        return ",".join(hits)
    raise Refusal("unknown op")


# ----------------------------------------------------------------- editlog ---

def _parse_integer(text: str, bits: int) -> int:
    try:
        # Optional sign, decimal digits, no whitespace. Explicit machine bound
        # prevents Python's arbitrary precision from accepting JVM overflow.
        if re.fullmatch(r"[+-]?\d+", text) is None:
            raise ValueError
        value = int(text, 10)
        _require(-(1 << (bits - 1)) <= value < (1 << (bits - 1)), "integer overflow")
        return value
    except ValueError:
        raise Refusal("int") from None


def _as_int(text: str) -> int:
    return _parse_integer(text, 32)


def _as_long(text: str) -> int:
    return _parse_integer(text, 64)


def _as_float(text: str) -> float:
    try:
        v = float(text)
    except ValueError:
        raise Refusal("float") from None
    if v != v or v in (float("inf"), float("-inf")):
        raise Refusal("float finite")
    return v


def _editlog(payload: str) -> str:
    cut = payload.find("~")
    _require(cut >= 0, "base~ops")
    base = dec(payload[:cut])
    ops_raw = payload[cut + 1:]
    _require(len(base) <= 4096, "base cap")
    ops = []
    if ops_raw != "":
        tokens = ops_raw.split("|")
        _require(len(tokens) <= 512, "ops cap")
        for token in tokens:
            if token.startswith("i"):
                comma = token.find(",")
                _require(comma > 1, "insert pos")
                pos = _as_int(token[1:comma])
                _require(pos >= 0, "pos")
                text = dec(token[comma + 1:])
                _require(len(text) <= 256, "insert cap")
                _require("|" not in text, "pipe in insert")
                ops.append(("i", pos, text))
            elif token.startswith("d"):
                comma = token.find(",")
                _require(comma > 1, "delete pos")
                pos = _as_int(token[1:comma])
                _require(pos >= 0, "pos")
                ln = _as_int(token[comma + 1:])
                _require(ln >= 1, "len")
                ops.append(("d", pos, ln))
            else:
                raise Refusal("op token")
    doc = _replay(base, ops)
    cur = ops
    changed = True
    rounds = 0
    while changed:
        changed = False
        rounds += 1
        _require(rounds <= 513, "converge")
        nxt = []
        i = 0
        while i < len(cur):
            a = cur[i]
            b = cur[i + 1] if i + 1 < len(cur) else None
            if (b is not None and a[0] == "i" and b[0] == "i"
                    and b[1] == a[1] + len(a[2]) and len(a[2]) + len(b[2]) <= 256):
                nxt.append(("i", a[1], a[2] + b[2]))
                i += 2
                changed = True
            elif b is not None and a[0] == "d" and b[0] == "d" and b[1] == a[1]:
                ln = a[2] + b[2]
                _require(ln <= 0x7FFFFFFF, "fused len")
                nxt.append(("d", a[1], ln))
                i += 2
                changed = True
            else:
                nxt.append(a)
                i += 1
        cur = nxt
    fused = _replay(base, cur)
    _require(fused == doc, "document changed")
    return _render_ops(cur)


def _replay(base: str, ops) -> str:
    doc = base
    for op in ops:
        _require(len(doc) <= 4096, "doc cap")
        if op[0] == "i":
            _require(op[1] <= len(doc), "insert past end")
            doc = doc[:op[1]] + op[2] + doc[op[1]:]
        else:
            _require(op[1] + op[2] <= len(doc), "delete past end")
            doc = doc[:op[1]] + doc[op[1] + op[2]:]
    _require(len(doc) <= 4096, "final doc cap")
    return doc


def _render_ops(ops) -> str:
    out = []
    for op in ops:
        if op[0] == "i":
            out.append("i%d,%s" % (op[1], op[2]))
        else:
            out.append("d%d,%d" % (op[1], op[2]))
    return "|".join(out)


# ------------------------------------------------------------------- macro ---

_MAX_STEPS = 1024
_PKG_RE = re.compile(r"[a-z0-9._-]{1,64}")


def _unescape(field: str) -> str:
    out = []
    i = 0
    while i < len(field):
        c = field[i]
        if c == "\\":
            _require(i + 1 < len(field), "dangling escape")
            n = field[i + 1]
            _require(n in "\\:;", "illegal escape")
            out.append(n)
            i += 2
        else:
            _require(c not in "\n\r\t", "control")
            out.append(c)
            i += 1
    return "".join(out)


def _escape(field: str) -> str:
    out = []
    for c in field:
        if c in "\\:;":
            out.append("\\")
            out.append(c)
        elif c in "\n\r\t":
            raise Refusal("control")
        else:
            out.append(c)
    return "".join(out)


def _split_escaped(s: str, sep: str) -> list[str]:
    out = []
    cur = []
    i = 0
    saw = False
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            cur.append(c)
            cur.append(s[i + 1])
            saw = True
            i += 2
            continue
        if c == sep:
            _require(saw or cur, "empty field")
            out.append("".join(cur))
            cur = []
            saw = False
            i += 1
            continue
        cur.append(c)
        saw = True
        i += 1
    _require(saw or cur, "empty field end")
    out.append("".join(cur))
    return out


def _macro(payload: str) -> str:
    wire = dec(payload)
    _require(wire.startswith("FMC1;"), "header")
    body = wire[5:]
    if body == "":
        return "FMC1;"
    tokens = _split_escaped(body, ";")
    _require(len(tokens) <= _MAX_STEPS, "steps")
    steps = []
    for token in tokens:
        fields = _split_escaped(token, ":")
        _require(fields, "empty step")
        kind = fields[0]
        if kind == "wait":
            _require(len(fields) == 2, "wait fields")
            ms = _as_int(fields[1])
            _require(0 <= ms <= 60_000, "wait range")
            steps.append(("wait", ms))
        elif kind == "tap":
            _require(len(fields) == 3, "tap fields")
            x = _as_int(fields[1]); y = _as_int(fields[2])
            _require(0 <= x <= 1000 and 0 <= y <= 1000, "tap range")
            steps.append(("tap", x, y))
        elif kind == "swipe":
            _require(len(fields) == 6, "swipe fields")
            x1 = _as_int(fields[1]); y1 = _as_int(fields[2])
            x2 = _as_int(fields[3]); y2 = _as_int(fields[4])
            ms = _as_int(fields[5])
            _require(all(0 <= v <= 1000 for v in (x1, y1, x2, y2)), "swipe range")
            _require(50 <= ms <= 5000, "swipe ms")
            steps.append(("swipe", x1, y1, x2, y2, ms))
        elif kind == "type":
            _require(len(fields) == 2, "type fields")
            text = _unescape(fields[1])
            _require(text != "", "empty type")
            _require(len(text) <= 512, "type cap")
            steps.append(("type", text))
        elif kind == "launch":
            _require(len(fields) == 2, "launch fields")
            pkg = _unescape(fields[1])
            _require(_PKG_RE.fullmatch(pkg) is not None, "pkg")
            steps.append(("launch", pkg))
        else:
            raise Refusal("step type")
    # encode canonical
    out = ["FMC1;"]
    parts = []
    for st in steps:
        if st[0] == "wait":
            parts.append("wait:%d" % st[1])
        elif st[0] == "tap":
            parts.append("tap:%d:%d" % (st[1], st[2]))
        elif st[0] == "swipe":
            parts.append("swipe:%d:%d:%d:%d:%d" % st[1:])
        elif st[0] == "type":
            parts.append("type:" + _escape(st[1]))
        else:
            parts.append("launch:" + _escape(st[1]))
    return out[0] + ";".join(parts)


# --------------------------------------------------------------------- ntp ---

_NTP_OFFSET = 2_208_988_800_000
_ERA_MS = (1 << 32) * 1000
_MIN_EPOCH = 0
_MAX_EPOCH = 4_102_444_800_000


def _ntp(payload: str) -> str:
    p = _segs(payload)
    if p[0] == "fwd":
        _require(len(p) == 4)
        ref = _as_long(p[1])
        sec = _as_long(p[2])
        frac = _as_long(p[3])
        _require(_MIN_EPOCH <= ref <= _MAX_EPOCH, "ref")
        _require(0 <= sec < (1 << 32), "sec")
        _require(0 <= frac < (1 << 32), "frac")
        naive = sec * 1000 + _NTP_OFFSET
        naive = naive + frac * 1000 // (1 << 32)
        diff = naive - ref
        k = (diff + _ERA_MS // 2) // _ERA_MS  # floor division like floorDiv
        era = naive - k * _ERA_MS
        _require(_MIN_EPOCH <= era <= _MAX_EPOCH, "era")
        return str(era)
    _require(len(p) == 2)
    ms = _as_long(p[1])
    _require(_MIN_EPOCH <= ms <= _MAX_EPOCH, "ms")
    with_offset = ms + _NTP_OFFSET
    _require(with_offset >= 0, "before ntp era")
    seconds = with_offset // 1000
    millis = with_offset % 1000
    wire = seconds % (1 << 32)
    fraction = millis * (1 << 32) // 1000
    _require(0 <= fraction < (1 << 32), "fraction")
    return "%d;%d" % (wire, fraction)


# ---------------------------------------------------------------- duration ---

_UNITS = {"ms": 1, "s": 1000, "m": 60_000, "h": 3_600_000,
          "d": 86_400_000, "w": 604_800_000}


def _dur(payload: str) -> str:
    phrase = dec(payload)
    _require(len(phrase) <= 64, "phrase")
    rest = phrase.strip()
    if rest.startswith("in "):
        rest = rest[3:].strip()
    _require(rest != "", "empty")
    compact = rest.replace(" ", "")
    _require(compact != "", "empty")
    total = 0
    components = 0
    i = 0
    while i < len(compact):
        ds = i
        while i < len(compact) and compact[i].isdigit():
            i += 1
        _require(i > ds, "number")
        number_text = compact[ds:i]
        _require(len(number_text) <= 10, "number long")
        number = _as_long(number_text)
        _require(0 <= number <= 1_000_000_000, "component")
        us = i
        while i < len(compact) and not compact[i].isdigit():
            i += 1
        _require(i > us, "unit")
        unit = compact[us:i]
        _require(unit in _UNITS, "unknown unit")
        total += number * _UNITS[unit]
        _require(total <= 30 * 86_400_000, "over 30d")
        components += 1
        _require(components <= 8, "components")
    return str(total)


# ----------------------------------------------------------------- shuffle ---

def _splitmix64(seed: int):
    state = seed & 0xFFFFFFFFFFFFFFFF

    def nxt() -> int:
        nonlocal state
        state = (state + 0x9E3779B97F4A7C15) & 0xFFFFFFFFFFFFFFFF
        z = state
        z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & 0xFFFFFFFFFFFFFFFF
        z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & 0xFFFFFFFFFFFFFFFF
        return (z ^ (z >> 31)) & 0xFFFFFFFFFFFFFFFF

    return nxt


def _shuffle(payload: str) -> str:
    p = _segs(payload)
    _require(len(p) == 3)
    seed = p[0]
    _require(1 <= len(seed) <= 64, "seed")
    window = _as_int(p[1])
    _require(1 <= window <= 64, "window")
    ids = [] if p[2] == "" else p[2].split(",")
    _require(2 <= len(ids) <= 1024, "queue")
    seen = set()
    for i in ids:
        _require(re.fullmatch(r"[A-Za-z0-9._-]{1,64}", i) is not None, "id")
        _require(i not in seen, "dup id")
        seen.add(i)
    digest = hashlib.sha256(seed.encode("utf-8")).digest()
    seed_long = int.from_bytes(digest[:8], "big")
    if seed_long >= 1 << 63:
        seed_long -= 1 << 64
    nxt = _splitmix64(seed_long & 0xFFFFFFFFFFFFFFFF)
    deck = list(ids)
    for i in range(len(deck) - 1, 0, -1):
        j = (nxt() & 0x7FFFFFFFFFFFFFFF) % (i + 1)
        deck[i], deck[j] = deck[j], deck[i]
    out: list[str] = []
    remaining = list(deck)
    while remaining:
        tail = set(out[-window:]) if window else set()
        if len(out) + len(remaining) > window:
            take_at = next((k for k, v in enumerate(remaining) if v not in tail), -1)
        else:
            take_at = 0
        if take_at < 0:
            take_at = 0
        out.append(remaining.pop(take_at))
    return ",".join(out)


# --------------------------------------------------------------------- sri ---

_B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
_B64_TABLE = {c: i for i, c in enumerate(_B64_ALPHABET)}
_ALGO_LEN = {"sha256": 32, "sha384": 48, "sha512": 64}


def _sri(payload: str) -> str:
    integrity = dec(payload)
    _require(len(integrity) <= 1024, "integrity cap")
    trimmed = integrity.strip()
    _require(trimmed != "", "empty")
    tokens = trimmed.split()
    _require(len(tokens) <= 8, "token cap")
    seen = set()
    out = []
    for token in tokens:
        m = re.fullmatch(r"sha(256|384|512)-([A-Za-z0-9+/]+={0,2})", token)
        _require(m is not None, "token")
        algo = "sha" + m.group(1)
        _require(algo not in seen, "duplicate algo")
        seen.add(algo)
        data = _b64strict(m.group(2))
        _require(len(data) == _ALGO_LEN[algo], "digest length")
        out.append("%s:%s" % (algo, data.hex()))
    return ",".join(out)


def _b64strict(s: str) -> bytes:
    _require(s != "", "empty b64")
    _require(len(s) % 4 == 0, "b64 length")
    pad = 2 if s.endswith("==") else (1 if s.endswith("=") else 0)
    body = s[:len(s) - pad]
    _require("=" not in body, "early padding")
    out = bytearray()
    i = 0
    while i < len(s):
        last = i + 4 == len(s)
        try:
            v0 = _B64_TABLE[s[i]]
            v1 = _B64_TABLE[s[i + 1]]
        except KeyError:
            raise Refusal("b64 char") from None
        out.append(((v0 << 2) | (v1 >> 4)) & 0xFF)
        if last and pad == 2:
            _require(s[i + 2] == "=" and s[i + 3] == "=", "pad fill")
            _require((v1 & 0x0F) == 0, "pad bits")
        elif last and pad == 1:
            _require(s[i + 2] != "=" and s[i + 3] == "=", "pad fill")
            try:
                v2 = _B64_TABLE[s[i + 2]]
            except KeyError:
                raise Refusal("b64 char") from None
            _require((v2 & 0x03) == 0, "pad bits")
            out.append((((v1 & 0x0F) << 4) | (v2 >> 2)) & 0xFF)
        else:
            if s[i + 2] == "=" or s[i + 3] == "=":
                raise Refusal("early padding")
            try:
                v2 = _B64_TABLE[s[i + 2]]
                v3 = _B64_TABLE[s[i + 3]]
            except KeyError:
                raise Refusal("b64 char") from None
            out.append((((v1 & 0x0F) << 4) | (v2 >> 2)) & 0xFF)
            out.append((((v2 & 0x03) << 6) | v3) & 0xFF)
        i += 4
    expected = len(s) // 4 * 3 - pad
    _require(len(out) == expected, "b64 size")
    return bytes(out)


# --------------------------------------------------------------------- oem ---

def _oem(payload: str) -> str:
    _require(payload != "", "series")
    tokens = payload.split(",")
    _require(1 <= len(tokens) <= 4096, "samples")
    samples = []
    for t in tokens:
        p = t.split(":")
        _require(len(p) == 4, "sample fields")
        epoch = _as_long(p[0]); cur = _as_long(p[1])
        mx = _as_long(p[2]); temp = _as_int(p[3])
        _require(epoch >= 0, "epoch")
        _require(1 <= cur <= 10_000_000 and 1 <= mx <= 10_000_000, "freq")
        _require(-400 <= temp <= 1200, "temp")
        samples.append((epoch, cur, mx, temp))
    for i in range(1, len(samples)):
        _require(samples[i][0] > samples[i - 1][0], "increasing")
        _require(samples[i][0] - samples[i - 1][0] <= 30 * 60_000, "gap")
    capped = [cur * 10 < mx * 7 for (_t, cur, _m, _x) in samples]
    out = []
    i = 0
    while i < len(samples):
        if not capped[i]:
            i += 1
            continue
        start = i
        end = i
        while (end + 1 < len(samples) and capped[end + 1]
               and samples[end + 1][0] - samples[end][0] <= 30 * 60_000):
            end += 1
        span = samples[end][0] - samples[start][0]
        if span >= 30_000:
            max_temp = max(samples[k][3] for k in range(start, end + 1))
            if max_temp >= 480:
                level = "CRITICAL"
            elif max_temp >= 450:
                level = "SEVERE"
            elif span >= 300_000:
                level = "SEVERE"
            else:
                level = "LIGHT"
            out.append("%d:%d:%s" % (samples[start][0], samples[end][0], level))
        i = end + 1
    return ",".join(out) if out else "none"


# ----------------------------------------------------------------- pwhist ---

def _sha256(*parts: bytes) -> bytes:
    h = hashlib.sha256()
    for p in parts:
        h.update(p)
    return h.digest()


def _pwhist(payload: str) -> str:
    p = _segs(payload)
    _require(len(p) == 3)
    salt_hex, history_spec, password = p
    if salt_hex == "" or len(salt_hex) % 2 != 0:
        raise Refusal("salt hex")
    try:
        salt = bytes.fromhex(salt_hex)
    except ValueError:
        raise Refusal("salt hex") from None
    _require(len(salt) == 32, "salt size")
    _require(1 <= len(password) <= 1024, "password")
    history = [] if history_spec == "" else history_spec.split(",")
    _require(len(history) <= 12, "history cap")
    for h in history:
        _require(re.fullmatch(r"[0-9a-f]{64}", h) is not None, "history hash")
    acc = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), b"FHC2" + salt, 600_000, 32)
    digest_hex = acc.hex()
    if digest_hex in history:
        return "reuse;%d" % history.index(digest_hex)
    nxt = history + [digest_hex]
    if len(nxt) > 12:
        nxt = nxt[len(nxt) - 12:]
    return "ok;" + ",".join(nxt)


# ---------------------------------------------------------------- dupframe ---

def _popcount(x: int) -> int:
    return bin(x & 0xFFFFFFFFFFFFFFFF).count("1")


def _dupframe(payload: str) -> str:
    _require(payload != "", "frames")
    tokens = payload.split(",")
    _require(1 <= len(tokens) <= 4096, "frame cap")
    frames = []
    for t in tokens:
        colon = t.find(":")
        _require(colon > 0 and colon == t.rfind(":"), "frame format")
        ts = _as_long(t[:colon])
        _require(ts >= 0, "ts")
        fp = t[colon + 1:]
        _require(re.fullmatch(r"[0-9a-f]{72}", fp) is not None, "fp")
        cells = [int(c, 16) for c in fp]
        dhash = 0
        for r in range(8):
            for c in range(8):
                left = cells[r * 9 + c]
                right = cells[r * 9 + c + 1]
                dhash <<= 1
                if right > left:
                    dhash |= 1
        frames.append((ts, dhash))
    for i in range(1, len(frames)):
        _require(frames[i][0] > frames[i - 1][0], "increasing ts")
    out = []
    i = 0
    while i < len(frames):
        anchor_hash = frames[i][1]
        anchor_ts = frames[i][0]
        j = i
        while (j + 1 < len(frames)
               and frames[j + 1][0] - anchor_ts <= 5_000
               and _popcount(frames[j + 1][1] ^ anchor_hash) <= 2):
            j += 1
        count = j - i + 1
        if count >= 2:
            out.append("%d:%d:%d" % (frames[i][0], frames[j][0], count))
        i = j + 1
    return ",".join(out) if out else "none"


# ----------------------------------------------------------------- diagexp ---

_KEY_RE = re.compile(r"[a-z][a-z0-9_.-]{0,63}")
_EMAIL = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
_IPV4 = re.compile(r"(?<!\d)(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}"
                   r"(?:25[0-5]|2[0-4]\d|1?\d?\d)(?!\d)")
_UUID = re.compile(r"(?i)(?<![0-9a-f])[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}"
                   r"-[0-9a-f]{4}-[0-9a-f]{12}(?![0-9a-f])")
_LONG_HEX = re.compile(r"(?<![0-9a-fA-F])[0-9a-fA-F]{32,}(?![0-9a-fA-F])")
_BEARER = re.compile(r"(?i)(bearer\s+)[A-Za-z0-9\-._~+/=]+")
_TOKEN = re.compile(r"(?i)(token=)[^\s&;]+")
_PATH = re.compile(r"/(data|sdcard|storage|system|vendor)/[^\s:'\"]*")


def _scrub_value(value: str) -> str:
    _require(len(value) <= 1024, "value cap")
    out = value
    out = _BEARER.sub(r"\1[secret]", out)
    out = _TOKEN.sub(r"\1[secret]", out)
    out = _EMAIL.sub("[email]", out)
    out = _IPV4.sub("[ip]", out)
    out = _UUID.sub("[uuid]", out)
    out = _LONG_HEX.sub("[hex]", out)
    out = _PATH.sub("[path]", out)
    return out


def _diagexp(payload: str) -> str:
    cut = payload.find("~")
    _require(cut >= 0, "allow~records")
    allow_spec = dec(payload[:cut])
    records_raw = payload[cut + 1:]
    allow = [] if allow_spec == "" else allow_spec.split(",")
    _require(len(allow) <= 128, "allow cap")
    allow_set = set()
    for k in allow:
        _require(_KEY_RE.fullmatch(k) is not None, "allow key")
        _require(k not in allow_set, "allow dup")
        allow_set.add(k)
    records = [] if records_raw == "" else records_raw.split(",")
    _require(len(records) <= 256, "records cap")
    seen = set()
    kept = []
    for record in records:
        eq = record.find("=")
        _require(eq >= 1, "key=value")
        key = record[:eq]
        value = dec(record[eq + 1:])
        _require(_KEY_RE.fullmatch(key) is not None, "record key")
        _require(key not in seen, "record dup")
        seen.add(key)
        _require(len(value) <= 1024, "value cap")
        if key in allow_set:
            _require(all(not (ord(c) < 32 or 127 <= ord(c) <= 159 or 0x2028 <= ord(c) <= 0x202e
                             or 0x2066 <= ord(c) <= 0x2069 or ord(c) in (0x200e, 0x200f, 0x061c))
                         for c in value), "export control")
            kept.append((key, _scrub_value(value)))
    kept.sort(key=lambda kv: kv[0])
    if not kept:
        return ""
    return "\n".join("%s=%s" % kv for kv in kept)


# ------------------------------------------------------------- msgthread ---

def _msgthread(payload: str) -> str:
    # Import the already-reviewed R145 oracle (updated in the R146 repair)
    # instead of writing a third resolver: the repair's own contract tests.
    from test_message_thread import Message, resolve  # noqa: WPS433
    entries = [] if payload == "" else payload.split(",")
    messages = []
    for entry in entries:
        f = entry.split(";")
        _require(len(f) == 3, "message fields")
        messages.append(Message(f[0], None if f[1] == "" else f[1], dec(f[2])))
    try:
        threads = resolve(messages)
    except AssertionError as exc:
        raise Refusal(str(exc)) from None
    return "\n".join("%s:%s" % (root, ",".join(desc)) for root, desc in threads)


# ---------------------------------------------------------------- dispatch ---

_HANDLERS = {
    "inexact": _inexact,
    "keyres": _keyres,
    "keyseq": _keyseq,
    "pdf": _pdf,
    "cpio": _cpio,
    "psd": _psd,
    "odfmeta": _odfmeta,
    "geo": _geo,
    "synonym": _synonym,
    "termurl": _termurl,
    "notetag": _notetag,
    "editlog": _editlog,
    "macro": _macro,
    "ntp": _ntp,
    "dur": _dur,
    "shuffle": _shuffle,
    "sri": _sri,
    "oem": _oem,
    "pwhist": _pwhist,
    "dupframe": _dupframe,
    "diagexp": _diagexp,
    "msgthread": _msgthread,
}


FEATURES = tuple(sorted(_HANDLERS))


def oracle(feature: str, payload: str) -> str:
    handler = _HANDLERS.get(feature)
    if handler is None:
        raise Refusal("unknown feature")
    try:
        return handler(payload)
    except Refusal:
        raise
    except (ValueError, IndexError, KeyError, OverflowError, RecursionError,
            struct.error) as exc:
        raise Refusal(type(exc).__name__) from exc
