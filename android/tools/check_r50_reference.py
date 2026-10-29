#!/usr/bin/env python3
"""Independent Python 3.11 cross-language differential oracle for R50 artifacts.

Verifies all 26 real binary & structured artifacts emitted by `dev.fusion.testing.R50Checks`
using Python's standard library (`zipfile`, `wave`, `struct`, `xml.etree.ElementTree`,
`hashlib`, `math`, `json`) plus `openpyxl` for ECMA-376 `.xlsx` verification — sharing zero
Kotlin implementation code with the kernels under test.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import struct
import sys
import wave
import xml.etree.ElementTree as ET
import zipfile
from itertools import combinations
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
py_ext = Path.home() / ".cache/fusion-host-r23/python"
if py_ext.is_dir():
    sys.path.insert(0, str(py_ext))

EXPECTED_ARTIFACTS = [
    "r50-art-baseline-prof.txt",
    "r50-conditional-databar.xml",
    "r50-doh-rfc8484.bin",
    "r50-formula-168.json",
    "r50-fsr1-upscaled.json",
    "r50-hardware-vectors-v11-v15.json",
    "r50-master-deck.pptx",
    "r50-mat2-scrubbed.docx",
    "r50-megolm-session.json",
    "r50-mermaid-dag.svg",
    "r50-multisheet-3d.xlsx",
    "r50-nlms-echo.json",
    "r50-opentype-kern.bin",
    "r50-pivot-cache.xml",
    "r50-pmtiles-hilbert.json",
    "r50-redacted.pdf",
    "r50-rfb-copyrect.bin",
    "r50-shamir-gf256.json",
    "r50-sparkline.svg",
    "r50-ssh2-packet.bin",
    "r50-summary.json",
    "r50-tails-transports.json",
    "r50-vcard4-projected.vcf",
    "r50-voice-memo.wav",
    "r50-wasm2-f64-table.wasm",
    "r50-writer-trackchanges.xml",
]


def gf256_mul(a: int, b: int) -> int:
    p = 0
    for _ in range(8):
        if b & 1:
            p ^= a
        hi = a & 0x80
        a = (a << 1) & 0xFF
        if hi:
            a ^= 0x1B
        b >>= 1
    return p


def gf256_inv(a: int) -> int:
    if a == 0:
        raise ValueError("zero has no inverse")
    # a^254 in GF(2^8)
    res = 1
    base = a
    exp = 254
    while exp > 0:
        if exp & 1:
            res = gf256_mul(res, base)
        base = gf256_mul(base, base)
        exp >>= 1
    return res


def py_hilbert_zxy_to_tile_id(z: int, x: int, y: int) -> int:
    if z == 0:
        return 0
    acc = ((1 << (2 * z)) - 1) // 3
    n = 1 << z
    rx = x
    ry = y
    d = 0
    s = n >> 1
    while s > 0:
        bx = 1 if (rx & s) != 0 else 0
        by = 1 if (ry & s) != 0 else 0
        d += s * s * ((3 * bx) ^ by)
        if by == 0:
            if bx == 1:
                rx = s - 1 - rx
                ry = s - 1 - ry
            rx, ry = ry, rx
        s >>= 1
    return acc + d


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--artifacts", type=Path, default=ROOT / "testing/build/r50/artifacts")
    args = ap.parse_args([] if argv is None and __name__ != "__main__" else argv)
    art = args.artifacts.resolve()

    checks = 0

    def ok(cond: bool, msg: str) -> None:
        nonlocal checks
        if not cond:
            raise AssertionError(f"R50 oracle check #{checks + 1} failed: {msg}")
        checks += 1

    # 0. Presence & non-empty verification across all 26 artifacts (26 checks)
    ok(len(EXPECTED_ARTIFACTS) == 26, "expected 26 R50 artifacts")
    for name in EXPECTED_ARTIFACTS:
        p = art / name
        ok(p.is_file() and p.stat().st_size > 0, f"missing or empty artifact {name}")

    # 1. r50-multisheet-3d.xlsx via openpyxl + zipfile
    import openpyxl
    xlsx_path = art / "r50-multisheet-3d.xlsx"
    wb_val = openpyxl.load_workbook(xlsx_path, data_only=True)
    wb_frm = openpyxl.load_workbook(xlsx_path, data_only=False)
    ok(wb_val.sheetnames == ["Q1", "Q2", "Q3", "Summary"], f"unexpected sheets {wb_val.sheetnames}")
    for idx_q, sh_name in enumerate(("Q1", "Q2", "Q3"), start=1):
        ok(abs(float(wb_val[sh_name]["B2"].value) - 100.0 * idx_q) < 1e-9, f"{sh_name}!B2 value")
        ok(abs(float(wb_val[sh_name]["B3"].value) - 25.0 * idx_q) < 1e-9, f"{sh_name}!B3 value")
    ok(abs(float(wb_val["Summary"]["A1"].value) - 750.0) < 1e-9, "Summary!A1 cached 3D SUM")
    ok(abs(float(wb_val["Summary"]["A2"].value) - 200.0) < 1e-9, "Summary!A2 cached 3D AVERAGE")
    ok(str(wb_frm["Summary"]["A1"].value).startswith("="), "Summary!A1 formula preserved")
    ok(str(wb_frm["Summary"]["A2"].value).startswith("="), "Summary!A2 formula preserved")
    with zipfile.ZipFile(xlsx_path, "r") as zf:
        ok(all("vbaProject" not in n for n in zf.namelist()), "no VBA macro payload allowed")
        for zi in zf.infolist():
            ok(zi.CRC != 0 or zi.file_size == 0, f"CRC populated for {zi.filename}")

    # 2. r50-formula-168.json independent Python math cross-check
    f168 = json.loads((art / "r50-formula-168.json").read_text())
    ok(f168["total_functions"] == 168 and f168["spill_ops"] == 29, "168 fns + 29 spill ops")
    ok(abs(f168["mdeterm_2x2"] - (4.0 * 6.0 - 7.0 * 2.0)) < 1e-9, "MDETERM 2x2")
    ok(abs(f168["mmult_minverse_trace"] - 2.0) < 1e-9, "MMULT * MINVERSE trace")
    ok(abs(f168["gammaln5_exp"] - math.gamma(5.0)) < 1e-4, "EXP(GAMMALN(5)) == 24")
    ok(abs(f168["seriestest"] - (1 * 2**1 + 2 * 2**2 + 3 * 2**3)) < 1e-9, "SERIESSUM")
    ok(abs(f168["multinomial"] - (math.factorial(6) / (math.factorial(2) * math.factorial(3)))) < 1e-9, "MULTINOMIAL")
    ok(abs(f168["trend_x5"] - 11.0) < 1e-8 and f168["sum_3d"] == 750.0, "TREND & 3D SUM")

    # 3. XML & SVG artifacts (pivot, conditional databar, sparkline, trackchanges, mermaid)
    for xml_name in (
        "r50-pivot-cache.xml",
        "r50-conditional-databar.xml",
        "r50-sparkline.svg",
        "r50-writer-trackchanges.xml",
        "r50-mermaid-dag.svg",
    ):
        raw_txt = (art / xml_name).read_text()
        ok("<!DOCTYPE" not in raw_txt and "<!ENTITY" not in raw_txt, f"XXE guard on {xml_name}")
        root = ET.fromstring(raw_txt)
        ok(root is not None, f"well-formed XML {xml_name}")

    ok("pivotTableDefinition" in (art / "r50-pivot-cache.xml").read_text(), "pivotTableDefinition tag")
    ok("dataBar" in (art / "r50-conditional-databar.xml").read_text(), "dataBar tag")
    ok("<polyline" in (art / "r50-sparkline.svg").read_text(), "sparkline polyline")
    wml_txt = (art / "r50-writer-trackchanges.xml").read_text()
    ok("ins" in wml_txt and "del" in wml_txt, "WordprocessingML ins/del")
    ok((art / "r50-mermaid-dag.svg").read_text().count("<rect") >= 3, "Mermaid SVG nodes")

    # 4. r50-opentype-kern.bin binary Format 0 header & sorted glyph pairs
    kern = (art / "r50-opentype-kern.bin").read_bytes()
    ver, n_tables, sub_ver, sub_len, cov, n_pairs, s_range, e_sel, r_shift = struct.unpack(">HHHHHHHHH", kern[:18])
    ok(ver == 0 and n_tables == 1 and n_pairs == 3, "OpenType kern header")
    ok(sub_len == len(kern) - 4 and s_range == 12 and r_shift == 6, "OpenType binary search header")
    pairs = [struct.unpack(">HHh", kern[18 + 6 * i : 24 + 6 * i]) for i in range(n_pairs)]
    keys = [(l << 16) | r for l, r, _ in pairs]
    ok(keys == sorted(keys), "OpenType kern pairs strictly sorted")
    ok((ord("A"), ord("V"), -90) in pairs, "A-V -90 FUnits kern pair")

    # 5. r50-master-deck.pptx & r50-mat2-scrubbed.docx
    with zipfile.ZipFile(art / "r50-master-deck.pptx", "r") as zf:
        names = set(zf.namelist())
        ok({"ppt/slideMasters/slideMaster1.xml", "ppt/slides/slide1.xml", "ppt/slides/slide2.xml", "ppt/notesSlides/notesSlide1.xml"} <= names, "PPTX parts")
        ok(b"<p:sldMaster" in zf.read("ppt/slideMasters/slideMaster1.xml"), "PPTX master XML")
        ok(b"<p:transition" in zf.read("ppt/slides/slide1.xml"), "PPTX slide transition XML")

    with zipfile.ZipFile(art / "r50-mat2-scrubbed.docx", "r") as zf:
        core_xml = zf.read("docProps/core.xml")
        ok(b"Jane Doe" not in core_xml and b"JaneLaptop" not in core_xml and len(list(ET.fromstring(core_xml))) == 0, "mat2 zeroed docProps/core.xml")
        jpg = zf.read("word/media/evidence.jpg")
        ok(jpg[:2] == b"\xff\xd8" and jpg[-2:] == b"\xff\xd9", "nested JPEG SOI/EOI intact")
        ok(b"GPSLatitude" not in jpg and b"SecretSource" not in jpg and b"\xff\xe1" not in jpg, "nested JPEG APP1 EXIF scrubbed")

    # 6. r50-redacted.pdf physical stream redaction
    pdf_bytes = (art / "r50-redacted.pdf").read_bytes()
    ok(pdf_bytes.startswith(b"%PDF-1."), "PDF header")
    ok(b"xref" in pdf_bytes and pdf_bytes.rstrip().endswith(b"%%EOF"), "PDF xref & EOF")
    ok(b"999-42-8811" not in pdf_bytes and b"[REDACTED]" in pdf_bytes, "PDF physical stream redaction")

    # 7. r50-wasm2-f64-table.wasm binary specification check (full section walk)
    wasm = (art / "r50-wasm2-f64-table.wasm").read_bytes()
    ok(wasm[:8] == b"\x00asm\x01\x00\x00\x00", "Wasm 2.0 magic + version 1")
    ok(b"quad_eval" in wasm and b"table_ops_demo" in wasm and b"f64_mem_sat" in wasm, "Wasm exports")
    ok(0x7C in wasm and 0xFC in wasm, "Wasm f64 (0x7C) and bulk/sat prefix (0xFC) present")
    sec_ids = []
    pos = 8
    while pos < len(wasm):
        sid = wasm[pos]; pos += 1
        slen = 0; shift = 0
        while True:
            b = wasm[pos]; pos += 1
            slen |= (b & 0x7F) << shift
            if (b & 0x80) == 0:
                break
            shift += 7
        sec_ids.append(sid)
        ok(pos + slen <= len(wasm), f"Wasm section {sid} bounded")
        pos += slen
    ok(pos == len(wasm) and sec_ids == [1, 3, 4, 5, 7, 9, 10], f"Wasm canonical section order {sec_ids}")

    # 8. r50-doh-rfc8484.bin RFC 8484 + RFC 8467 128-byte padded DNS wire query
    doh = (art / "r50-doh-rfc8484.bin").read_bytes()
    ok(len(doh) == 128 and len(doh) % 128 == 0, "RFC 8467 128-byte block padding")
    qid, flags, qdcount, ancount, nscount, arcount = struct.unpack(">HHHHHH", doh[:12])
    ok(qid == 0 and flags == 0x0100 and qdcount == 1 and arcount == 1, "RFC 8484 ID=0 RD=1 QD=1 AR=1")
    ok(doh[12:23] == b"\x05tails\x03net\x00", "DNS QNAME tails.net")
    qtype, qclass = struct.unpack(">HH", doh[23:27])
    ok(qtype == 65 and qclass == 1, "HTTPS/SVCB QTYPE=65 IN=1")

    # 9. r50-shamir-gf256.json independent Python GF(2^8) Lagrange interpolation across all 10 3-of-5 subsets!
    sh = json.loads((art / "r50-shamir-gf256.json").read_text())
    expected_secret = bytes.fromhex(sh["secret_hex"])
    ok(expected_secret == b"FusionVaultMasterKey-R50-32Bytes", "Shamir target secret")
    parsed_shares = [(item["x"], bytes.fromhex(item["y_hex"])) for item in sh["shares"]]
    ok(len(parsed_shares) == 5, "5 Shamir shares")
    for subset in combinations(parsed_shares, 3):
        rec = bytearray(len(expected_secret))
        for b_idx in range(len(expected_secret)):
            acc = 0
            for i, (xi, yi_bytes) in enumerate(subset):
                num = 1
                den = 1
                for j, (xj, _) in enumerate(subset):
                    if i != j:
                        num = gf256_mul(num, xj)
                        den = gf256_mul(den, xi ^ xj)
                basis = gf256_mul(num, gf256_inv(den))
                acc ^= gf256_mul(yi_bytes[b_idx], basis)
            rec[b_idx] = acc
        ok(bytes(rec) == expected_secret, f"Python GF(256) Lagrange subset {[x for x, _ in subset]}")

    # Also prove under-threshold 2-of-5 Lagrange interpolation at x=0 does NOT leak the secret!
    for pair in combinations(parsed_shares, 2):
        rec2 = bytearray(len(expected_secret))
        for b_idx in range(len(expected_secret)):
            acc = 0
            for i, (xi, yi_bytes) in enumerate(pair):
                xj = pair[1 - i][0]
                basis = gf256_mul(xj, gf256_inv(xi ^ xj))
                acc ^= gf256_mul(yi_bytes[b_idx], basis)
            rec2[b_idx] = acc
        ok(bytes(rec2) != expected_secret, f"2-of-5 under-threshold pair {[x for x, _ in pair]} must not recover secret")

    # 10. r50-pmtiles-hilbert.json independent Python Hilbert curve verification (4 zoom levels)
    pm = json.loads((art / "r50-pmtiles-hilbert.json").read_text())
    ok(len(pm["samples"]) == 4, "4 PMTiles Hilbert samples")
    for s in pm["samples"]:
        py_tid = py_hilbert_zxy_to_tile_id(s["z"], s["x"], s["y"])
        ok(py_tid == s["tile_id"], f"PMTiles Hilbert mismatch at {s}: py={py_tid}")

    # 11. r50-voice-memo.wav via Python stdlib `wave`
    with wave.open(str(art / "r50-voice-memo.wav"), "rb") as wf:
        ok(wf.getnchannels() == 1 and wf.getsampwidth() == 2, "PCM16 mono WAV")
        ok(wf.getframerate() == 16_000 and wf.getnframes() == 320, "16kHz 320 frames WAV")
        raw_pcm = wf.readframes(320)
        samples = struct.unpack("<320h", raw_pcm)
        ok(samples[0] == 0 and samples[8] == 18_000 and samples[24] == -18_000, "exact sine PCM16 samples")

    # 12. Remaining binary & JSON/VCF/ART/Hardware/Tails/Megolm/FSR1/NLMS artifacts
    ssh = (art / "r50-ssh2-packet.bin").read_bytes()
    pkt_len = struct.unpack(">I", ssh[:4])[0]
    pad_len = ssh[4]
    ok(pkt_len + 4 == len(ssh) and pad_len >= 4 and len(ssh) % 8 == 0, "RFC 4253 SSH binary packet framing")
    ok(ssh[5] == 20, "SSH_MSG_KEXINIT payload byte")

    rfb = (art / "r50-rfb-copyrect.bin").read_bytes()
    msg_t, pad_r, n_rects, rx, ry, rw, rh, enc, sx, sy = struct.unpack(">BBHHHHHiHH", rfb)
    ok(msg_t == 0 and n_rects == 1 and (rx, ry, rw, rh, enc) == (16, 32, 640, 480, 1), "RFC 6143 CopyRect frame")

    vcf = (art / "r50-vcard4-projected.vcf").read_text()
    ok("VERSION:4.0" in vcf and "EMAIL:ada@analytical.org" in vcf, "vCard 4.0 projected email")
    ok("TEL:" not in vcf and "NOTE:" not in vcf and "ORG:" not in vcf, "vCard 4.0 least-privilege redaction")

    tails = json.loads((art / "r50-tails-transports.json").read_text())
    ok(tails["transport"] == "WEBTUNNEL" and tails["confluxPrimary"] == "legA" and tails["strippedCount"] == 3, "Tails WebTunnel + Conflux + ClearURLs")

    hw = json.loads((art / "r50-hardware-vectors-v11-v15.json").read_text())
    ok(hw["v11_bluffs_7byte_blocked"] and hw["v12_wifi_wildcard_only"], "V11 BLUFFS & V12 Wi-Fi")
    ok(hw["v13_nfc_max_rtt_ms"] == 12.0 and 1 <= hw["v14_tempest_max_lsb"] <= 2, "V13 NFC <=12ms & V14 Tempest <=2 LSB")
    ok(hw["v15_badusb_min_iki_ms"] == 8.0 and hw["posture_score"] == 100, "V15 BadUSB >=8ms & posture 100")

    meg = json.loads((art / "r50-megolm-session.json").read_text())
    ok(meg["indices"] == [0, 65, 4100] and len(meg["mac4100_hex"]) == 32 and meg["omemo_sid"] == 1042, "Megolm 4-tier + OMEMO")

    fsr = json.loads((art / "r50-fsr1-upscaled.json").read_text())
    ok(fsr["dst_w"] == 16 and fsr["dst_h"] == 16, "FSR 1.0 16x16 upscale")
    ok(fsr["easu_min"] >= 0.15 - 1e-9 and fsr["easu_max"] <= 0.85 + 1e-9, "FSR 1.0 EASU zero-ringing clamp")
    ok(all(0.0 <= v <= 1.0 for v in fsr["first32"]), "FSR 1.0 RCAS [0, 1] bounds")

    nlms = json.loads((art / "r50-nlms-echo.json").read_text())
    ok(nlms["erle_db"] > 20.0 and math.isfinite(nlms["tap2"]) and math.isfinite(nlms["tap3"]), "NLMS ERLE > 20 dB convergence")

    art_prof = (art / "r50-art-baseline-prof.txt").read_text()
    ok("HSPLdev/fusion/ooxml/FormulaEval;->evaluateSingle" in art_prof, "ART baseline profile rule")

    summ = json.loads((art / "r50-summary.json").read_text())
    ok(summ["round"] == "R50" and summ["total_assertions"] >= 8500, "R50 summary >= 8500 assertions")
    ok(summ["modules_covered"] == 50 and summ["new_pure_jvm_kernels"] == 80, "50/50 modules + 80 new kernels")
    ok(summ["touched_pure_jvm_engines"] == 4 and summ["touched_compose_ui_screens"] == 10, "14 TOUCHED files")
    ok(len(summ["transcript_sha256"]) == 64, "transcript SHA-256")

    out_report = {
        "round": "R50",
        "oracle": "Python 3.11 + openpyxl + wave + zipfile + struct + xml.etree + GF(256) + Hilbert",
        "artifacts_verified": len(EXPECTED_ARTIFACTS),
        "checks_passed": checks,
        "exit_code": 0,
    }
    (art.parent / "oracle-result.json").write_text(json.dumps(out_report, indent=2) + "\n")
    print(f"R50_ORACLE_OK checks={checks} artifacts={len(EXPECTED_ARTIFACTS)}")


if __name__ == "__main__":
    main()
