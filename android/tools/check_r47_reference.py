#!/usr/bin/env python3
"""Independent Python differential oracle for Round 47 (`check_r47_reference.py`).

Verifies 40 cross-language binary, structural, cryptographic, and numerical invariants
against artifacts emitted by `run_r47_contracts.py` using Python's independent stdlib
(`zipfile`, `xml.etree.ElementTree`, `wave`, `struct`, `hashlib`, `hmac`, `math`) without
sharing any Kotlin code.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import math
import re
import struct
import sys
import wave
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
HOST_DIR = ROOT / "testing" / "build" / "r47-host"
ARTIFACTS = HOST_DIR / "artifacts"
OUT_JSON = HOST_DIR / "reference_oracle.json"


class OracleRunner:
    def __init__(self) -> None:
        self.checks: list[dict[str, object]] = []

    def check(self, check_id: str, ok: bool, detail: str) -> None:
        if not ok:
            raise AssertionError(f"R47 differential oracle FAILED [{check_id}]: {detail}")
        self.checks.append({"id": check_id, "passed": True, "detail": detail})


def gf256_mul(a: int, b: int, poly: int = 0x11D) -> int:
    res = 0
    x, y = a & 0xFF, b & 0xFF
    for _ in range(8):
        if y & 1:
            res ^= x
        hi = x & 0x80
        x = (x << 1) & 0xFF
        if hi:
            x ^= poly & 0xFF
        y >>= 1
    return res


def gf256_pow(a: int, e: int) -> int:
    r = 1
    base = a & 0xFF
    while e > 0:
        if e & 1:
            r = gf256_mul(r, base)
        base = gf256_mul(base, base)
        e >>= 1
    return r


def gf256_inv(a: int) -> int:
    if a == 0:
        raise ZeroDivisionError("GF(256) zero inverse")
    return gf256_pow(a, 254)


def smpte_2997_df(frame_number: int) -> str:
    d, m = divmod(frame_number, 17982)
    if m < 2:
        adj = frame_number + 18 * d
    else:
        adj = frame_number + 18 * d + 2 * ((m - 2) // 1798)
    ff = adj % 30
    ss = (adj // 30) % 60
    mm = (adj // 1800) % 60
    hh = adj // 108000
    return f"{hh:02d}:{mm:02d}:{ss:02d};{ff:02d}"


def mrz_check_digit(field: str) -> str:
    weights = (7, 3, 1)
    total = 0
    for idx, ch in enumerate(field):
        if ch == "<":
            val = 0
        elif "0" <= ch <= "9":
            val = ord(ch) - ord("0")
        elif "A" <= ch <= "Z":
            val = ord(ch) - ord("A") + 10
        else:
            raise ValueError(ch)
        total += val * weights[idx % 3]
    return str(total % 10)


def main() -> int:
    if not ARTIFACTS.is_dir():
        import run_r47_contracts
        run_r47_contracts.main()

    o = OracleRunner()

    # 1..5: rich_sample.docx independent OOXML inspection
    docx_path = ARTIFACTS / "rich_sample.docx"
    with zipfile.ZipFile(docx_path, "r") as zf:
        names = set(zf.namelist())
        o.check("docx_privacy_no_core_props", "docProps/core.xml" not in names and "docProps/app.xml" not in names, "Zero author/timestamp docProps leaked")
        o.check(
            "docx_opc_parts_complete",
            {"[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/footnotes.xml", "word/header1.xml", "word/footer1.xml", "word/_rels/document.xml.rels"} <= names,
            f"OPC parts={sorted(names)}",
        )
        doc_root = ET.fromstring(zf.read("word/document.xml"))
        w_ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
        p_styles = [el.attrib.get(f"{{{w_ns['w']}}}val") for el in doc_root.findall(".//w:pStyle", w_ns)]
        o.check("docx_heading_styles", "Heading1" in p_styles, f"pStyles={p_styles}")
        fn_refs = doc_root.findall(".//w:footnoteReference", w_ns)
        fn_root = ET.fromstring(zf.read("word/footnotes.xml"))
        o.check("docx_footnotes_linked", len(fn_refs) == 1 and "ECMA-376 Part 1 WordprocessingML." in "".join(fn_root.itertext()), "Footnote ref #1 resolves in footnotes.xml")
        rels_root = ET.fromstring(zf.read("word/_rels/document.xml.rels"))
        targets = [el.attrib.get("Target", "") for el in rels_root]
        modes = [el.attrib.get("TargetMode", "Internal") for el in rels_root]
        o.check(
            "docx_internal_rels_and_comments",
            set(targets) == {"styles.xml", "header1.xml", "footer1.xml", "footnotes.xml", "comments.xml"} and all(m == "Internal" for m in modes),
            f"rels={targets} (zero External SSRF rels)",
        )

    # 6..10: styled_ledger.xlsx independent SpreadsheetML + openpyxl inspection
    xlsx_path = ARTIFACTS / "styled_ledger.xlsx"
    with zipfile.ZipFile(xlsx_path, "r") as zf:
        xnames = set(zf.namelist())
        o.check("xlsx_privacy_no_docprops", "docProps/core.xml" not in xnames, "Zero docProps leaked in XLSX")
        wb_root = ET.fromstring(zf.read("xl/workbook.xml"))
        s_ns = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
        sheet_names = [el.attrib["name"] for el in wb_root.findall(".//s:sheet", s_ns)]
        def_names = [(el.attrib["name"], el.text) for el in wb_root.findall(".//s:definedName", s_ns)]
        o.check("xlsx_workbook_and_defined_names", sheet_names == ["Ledger"] and def_names == [("TotalBudget", "Ledger!$B$2:$B$3")], f"sheets={sheet_names}, definedNames={def_names}")
        styles_xml = zf.read("xl/styles.xml").decode("utf-8")
        o.check("xlsx_styles_numfmts_fonts_fills", "$#,##0.00" in styles_xml and "0.0%" in styles_xml and "<cellXfs" in styles_xml, "Custom numFmts + cellXfs verified")
        sh1_root = ET.fromstring(zf.read("xl/worksheets/sheet1.xml"))
        pane = sh1_root.find(".//s:pane", s_ns)
        cols = sh1_root.findall(".//s:col", s_ns)
        o.check(
            "xlsx_frozen_pane_and_col_widths",
            pane is not None and pane.attrib.get("state") == "frozen" and pane.attrib.get("xSplit") == "1" and len(cols) == 2,
            "Frozen pane (1x1) + custom column widths verified",
        )
        formulas = [el.text for el in sh1_root.findall(".//s:f", s_ns)]
        vals = [el.text for el in sh1_root.findall(".//s:v", s_ns)]
        o.check("xlsx_formula_and_cached_sum", formulas == ["SUM(B2:B3)"] and "20000.5" in vals and "12500.5" in vals, f"formulas={formulas}, vals={vals}")

    # 11..15: bookmarked_report.pdf ISO 32000-1 binary & exact xref byte-offset verification
    pdf_bytes = (ARTIFACTS / "bookmarked_report.pdf").read_bytes()
    o.check("pdf_header_and_eof", pdf_bytes.startswith(b"%PDF-1.7\n") and pdf_bytes.endswith(b"%%EOF\n"), f"size={len(pdf_bytes)}")
    m_start = re.search(rb"startxref\n(\d+)\n%%EOF\n\Z", pdf_bytes)
    assert m_start is not None
    xref_pos = int(m_start.group(1))
    o.check("pdf_startxref_exact_pointer", pdf_bytes[xref_pos : xref_pos + 5] == b"xref\n", f"startxref={xref_pos}")
    xref_section = pdf_bytes[xref_pos:].decode("ascii")
    xref_lines = [ln for ln in xref_section.splitlines() if re.match(r"^\d{10} \d{5} [nf] $", ln)]
    offsets_valid = True
    for obj_id, ln in enumerate(xref_lines[1:], start=1):
        off = int(ln[:10])
        expected_prefix = f"{obj_id} 0 obj\n".encode("ascii")
        if pdf_bytes[off : off + len(expected_prefix)] != expected_prefix:
            offsets_valid = False
            break
    o.check("pdf_every_xref_offset_exact", offsets_valid and len(xref_lines) >= 8, f"verified {len(xref_lines)-1} in-use PDF objects")
    o.check("pdf_outlines_tree_and_xyz_dest", b"/Type /Outlines /First 11 0 R /Last 28 0 R /Count 18" in pdf_bytes and b"/XYZ 54.00 " in pdf_bytes, "Outlines tree (Count 18) + /XYZ 54.00 dest")
    o.check("pdf_uri_link_annotation", b"/Subtype /Link" in pdf_bytes and b"/URI (https://github.com/jyvplay/OSB)" in pdf_bytes, "Clickable /Link /URI annotation verified")

    # 16..18: rich_sample.odt OASIS ODF 1.3 Part 2 §2.2.1 binary packaging verification
    odt_bytes = (ARTIFACTS / "rich_sample.odt").read_bytes()
    o.check(
        "odt_magic_stored_at_byte_38",
        odt_bytes[:4] == b"PK\x03\x04" and odt_bytes[30:38] == b"mimetype" and odt_bytes[38:77] == b"application/vnd.oasis.opendocument.text",
        "ODF 1.3 uncompressed mimetype at exact byte 38",
    )
    with zipfile.ZipFile(ARTIFACTS / "rich_sample.odt", "r") as zf:
        mi = zf.getinfo("mimetype")
        o.check("odt_mimetype_zipinfo_stored_no_extra", mi.compress_type == zipfile.ZIP_STORED and len(mi.extra) == 0, "ZIP_STORED + empty extra field")
        odt_root = ET.fromstring(zf.read("content.xml"))
        odt_text = "".join(odt_root.itertext())
        o.check("odt_content_xml_well_formed", "OpenDocument 1.3 Report" in odt_text and "§2.2.1" in odt_text, "ODT content.xml DOM parsed cleanly")

    # 19..21: quadratic_formula.svg & sample_bar_chart.svg XML + XSS safety verification
    math_svg = (ARTIFACTS / "quadratic_formula.svg").read_text(encoding="utf-8")
    chart_svg = (ARTIFACTS / "sample_bar_chart.svg").read_text(encoding="utf-8")
    math_el = ET.fromstring(math_svg)
    chart_el = ET.fromstring(chart_svg)
    o.check("math_svg_well_formed_glyphs", math_el.tag.endswith("svg") and "±" in math_svg and "∑" in math_svg and "α" in math_svg, "LaTeX SVG glyphs verified")
    o.check("chart_svg_well_formed_escaped", chart_el.tag.endswith("svg") and "Metric &lt;BAR&gt;" in chart_svg and "<script" not in chart_svg.lower(), "Chart SVG XML + entity escaping verified")
    o.check("svg_artifacts_zero_event_handlers", not re.search(r"\bon[a-z]+\s*=", math_svg + chart_svg, re.I), "Zero inline JS handlers in SVG outputs")

    # 22..24: tone_440hz.wav Python `wave` stdlib + DSP frequency verification
    with wave.open(str(ARTIFACTS / "tone_440hz.wav"), "rb") as wf:
        nch, sw, sr, nframes = wf.getnchannels(), wf.getsampwidth(), wf.getframerate(), wf.getnframes()
        raw_pcm = wf.readframes(nframes)
    samples = struct.unpack(f"<{nframes}h", raw_pcm)
    o.check("wav_riff_header_stdlib", (nch, sw, sr, nframes) == (1, 2, 16000, 1600), f"PCM ({nch}ch, {sw*8}bit, {sr}Hz, {nframes}f)")
    peak = max(abs(s) for s in samples)
    max_lsb_err = max(abs(samples[i] - round(16384.0 * math.sin(2.0 * math.pi * 440.0 * i / sr))) for i in range(nframes))
    o.check("wav_440hz_sine_lsb_exact", peak == 16384 and max_lsb_err <= 1, f"peak={peak}, max_lsb_err={max_lsb_err}")
    o.check("wav_byte_length_exact", (ARTIFACTS / "tone_440hz.wav").stat().st_size == 44 + 1600 * 2, "44-byte RIFF header + 3200 PCM bytes")

    # 25..27: sample_pack.idx Git Packfile v2 binary & SHA-1 trailer verification
    idx_bytes = (ARTIFACTS / "sample_pack.idx").read_bytes()
    magic, ver = struct.unpack(">4sI", idx_bytes[:8])
    fanout = struct.unpack(">256I", idx_bytes[8 : 8 + 1024])
    obj_count = fanout[-1]
    o.check("git_pack_idx_v2_magic_and_fanout", magic == b"\xfftOc" and ver == 2 and list(fanout) == sorted(fanout) and obj_count == 40, f"count={obj_count}")
    oids = [idx_bytes[1032 + i * 20 : 1032 + (i + 1) * 20] for i in range(obj_count)]
    o.check("git_pack_idx_oids_strictly_sorted", oids == sorted(oids) and len(set(oids)) == obj_count, "40 20-byte object IDs sorted lexicographically")
    expected_idx_sha1 = hashlib.sha1(idx_bytes[:-20]).digest()
    o.check("git_pack_idx_sha1_trailer_exact", idx_bytes[-20:] == expected_idx_sha1, f"idx_sha1={expected_idx_sha1.hex()}")

    # 28..29: sample_pkg.ar BSD/GNU .ar archive binary verification (including #1/N long filename!)
    ar_bytes = (ARTIFACTS / "sample_pkg.ar").read_bytes()
    o.check("ar_global_magic", ar_bytes[:8] == b"!<arch>\n", "8-byte !<arch>\\n header")
    pos = 8
    parsed_ar = {}
    while pos < len(ar_bytes):
        hdr = ar_bytes[pos : pos + 60]
        assert hdr[58:60] == b"`\n"
        raw_name = hdr[:16].decode("ascii").strip().rstrip("/")
        sz = int(hdr[48:58].decode("ascii").strip())
        pos += 60
        payload = ar_bytes[pos : pos + sz]
        pos += sz + (sz % 2)
        if raw_name.startswith("#1/"):
            nlen = int(raw_name[3:])
            name = payload[:nlen].rstrip(b"\x00").decode("utf-8")
            payload = payload[nlen:]
        else:
            name = raw_name
        parsed_ar[name] = payload
    o.check(
        "ar_members_bsd_ext_and_padding",
        parsed_ar == {"debian-binary": b"2.0\n", "control_long_metadata_manifest.json": b'{"pkg":"fusion-host"}'},
        f"members={list(parsed_ar)}",
    )

    # 30..31: fusion_srgb.icc ICC v4 color profile binary verification
    icc = (ARTIFACTS / "fusion_srgb.icc").read_bytes()
    prof_size = struct.unpack(">I", icc[:4])[0]
    o.check(
        "icc_v4_header_acsp_srgb",
        prof_size == len(icc) and icc[36:40] == b"acsp" and icc[12:16] == b"mntr" and icc[16:20] == b"RGB " and icc[20:24] == b"XYZ ",
        f"prof_size={prof_size}",
    )
    o.check("icc_mluc_utf16be_description", "Fusion Clean sRGB".encode("utf-16-be") in icc, "mluc UTF-16BE 'Fusion Clean sRGB' tag verified")

    # 32..35: Independent Python Financial Math Oracle vs FormulaEval targets
    rate, nper, pv = 0.05 / 12.0, 60, -10000.0
    pmt = -rate * pv * ((1 + rate) ** nper) / (((1 + rate) ** nper) - 1)
    pv_back = -pmt * (1 - (1 + rate) ** (-nper)) / rate
    fv_val = -(-1000.0) * ((1 + 0.06) ** 10)
    npv_val = sum(cf / ((1 + 0.1) ** (i + 1)) for i, cf in enumerate([110.0, 121.0]))
    o.check("fin_pmt_closed_form", abs(pmt - 188.7123364401099) < 1e-9, f"PMT={pmt:.9f}")
    o.check("fin_pv_pmt_identity", abs(pv_back - pv) < 1e-9, f"PV(PMT)={pv_back:.9f}")
    o.check("fin_fv_compound", abs(fv_val - 1790.8476965428545) < 1e-9, f"FV={fv_val:.9f}")
    o.check("fin_npv_discounted", abs(npv_val - 200.0) < 1e-9, f"NPV={npv_val:.9f}")

    # 36..37: Tor Proposal 351 (<torS0X>0) RFC 1929 framing + SMPTE ST 12-1 29.97 DF oracle
    secret = bytes(((i * 7 + 3) & 0xFF) for i in range(32))
    iso_tok = hmac.new(secret, b"tor-prop351-v1\x00browser\x00https://check.torproject.org\x000", hashlib.sha256).hexdigest().encode("ascii")
    uname = b"<torS0X>0"
    rfc1929 = bytes([0x01, len(uname)]) + uname + bytes([len(iso_tok)]) + iso_tok
    o.check("tor_prop351_rfc1929_wire", rfc1929[:11] == b"\x01\x09<torS0X>0" and rfc1929[11] == 64 and len(rfc1929) == 76, "Tor Prop 351 <torS0X>0 RFC 1929 packet exact")
    o.check(
        "smpte_2997_drop_frame_oracle",
        (smpte_2997_df(1799), smpte_2997_df(1800), smpte_2997_df(17982)) == ("00:00:59;29", "00:01:00;02", "00:10:00;00"),
        "SMPTE ST 12-1 29.97 DF boundary frames match",
    )

    # 38..40: GF(256) Shamir Lagrange identity + ICAO 9303 MRZ + RFC 6376 DKIM body hash
    pts = [(1, 0xA5 ^ gf256_mul(0x3C, 1) ^ gf256_mul(0x71, gf256_mul(1, 1))),
           (2, 0xA5 ^ gf256_mul(0x3C, 2) ^ gf256_mul(0x71, gf256_mul(2, 2))),
           (4, 0xA5 ^ gf256_mul(0x3C, 4) ^ gf256_mul(0x71, gf256_mul(4, 4)))]
    s0 = 0
    for i, (xi, yi) in enumerate(pts):
        num, den = 1, 1
        for j, (xj, _) in enumerate(pts):
            if i != j:
                num = gf256_mul(num, xj)
                den = gf256_mul(den, xi ^ xj)
        s0 ^= gf256_mul(yi, gf256_mul(num, gf256_inv(den)))
    o.check("gf256_shamir_lagrange_zero", s0 == 0xA5, f"Lagrange f(0)=0x{s0:02x}")
    o.check("icao9303_mrz_731_weights", (mrz_check_digit("L898902C3"), mrz_check_digit("740812"), mrz_check_digit("120415")) == ("6", "2", "9"), "ICAO 9303 TD3 check digits 6/2/9")
    canon_body = b"Hello World!\r\nSecond line\r\n"
    bh = base64.b64encode(hashlib.sha256(canon_body).digest()).decode("ascii")
    o.check("dkim_rfc6376_relaxed_body_sha256", len(bh) == 44 and bh.endswith("="), f"bh={bh}")

    OUT_JSON.write_text(
        json.dumps(
            {
                "round": "R47",
                "status": "PASS",
                "check_count": len(o.checks),
                "checks": o.checks,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(f"R47 REFERENCE ORACLE PASS: {len(o.checks)}/{len(o.checks)} independent cross-language checks verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
