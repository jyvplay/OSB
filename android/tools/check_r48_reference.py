#!/usr/bin/env python3
"""Independent Python 3.11 cross-language differential oracle for Round 48 (R48) Kotlin artifacts."""
import base64
import datetime as dt
import hashlib
import hmac
import json
import math
import re
import struct
import subprocess
import sys
import zipfile
import zlib
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ART_DIR = ROOT / "testing/build/r48/artifacts"
OUT_JSON = ROOT / "testing/build/r48/reference-check.json"


def decode_quic_varint(buf: bytes, pos: int) -> tuple[int, int]:
    first = buf[pos]
    prefix = first >> 6
    length = 1 << prefix
    val = first & 0x3F
    for i in range(1, length):
        val = (val << 8) | buf[pos + i]
    return val, pos + length


def tmux_csum(body: str) -> str:
    csum = 0
    for ch in body:
        csum = ((csum >> 1) | ((csum & 1) << 15)) & 0xFFFF
        csum = (csum + (ord(ch) & 0xFF)) & 0xFFFF
    return f"{csum:04x}"


def hkdf_sha256(salt: bytes, ikm: bytes, info: bytes, length: int) -> bytes:
    prk = hmac.new(salt, ikm, hashlib.sha256).digest()
    okm = bytearray()
    prev = b""
    counter = 1
    while len(okm) < length:
        prev = hmac.new(prk, prev + info + bytes([counter]), hashlib.sha256).digest()
        okm.extend(prev)
        counter += 1
    return bytes(okm[:length])


def main() -> None:
    if not (ART_DIR / "sample.pptx").is_file():
        subprocess.run([sys.executable, str(ROOT / "tools/run_r48_contracts.py")], check=True)

    checks: list[dict] = []

    def check(name: str, cond: bool, detail: str = "") -> None:
        if not cond:
            raise AssertionError(f"R48 DIFFERENTIAL CHECK FAILED [{name}]: {detail}")
        checks.append({"id": len(checks) + 1, "name": name, "passed": True, "detail": detail})

    # --- 1..8: .pptx PresentationML OOXML package (zipfile + ElementTree) ---
    pptx_path = ART_DIR / "sample.pptx"
    with zipfile.ZipFile(pptx_path, "r") as zf:
        names = set(zf.namelist())
        check("pptx_required_parts", {"[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml", "ppt/slides/slide1.xml", "ppt/slides/slide2.xml", "ppt/notesSlides/notesSlide1.xml"}.issubset(names), f"entries={len(names)}")
        check("pptx_deterministic_dos_epoch", all(zi.date_time == (1980, 1, 1, 0, 0, 0) for zi in zf.infolist()), "all ZIP entries 1980-01-01T00:00:00")
        pres_root = ET.fromstring(zf.read("ppt/presentation.xml"))
        ns = {"p": "http://schemas.openxmlformats.org/presentationml/2006/main", "a": "http://schemas.openxmlformats.org/drawingml/2006/main"}
        sld_ids = pres_root.findall(".//p:sldIdLst/p:sldId", ns)
        check("pptx_slide_count_2", len(sld_ids) == 2, f"sldIds={len(sld_ids)}")
        sz = pres_root.find(".//p:sldSz", ns)
        check("pptx_widescreen_emu", sz is not None and sz.attrib.get("cx") == "9144000" and sz.attrib.get("cy") == "5143500" and sz.attrib.get("type") == "screen16x9", "16:9 screen16x9 EMU")
        s1_root = ET.fromstring(zf.read("ppt/slides/slide1.xml"))
        s1_texts = [el.text or "" for el in s1_root.findall(".//a:t", ns)]
        check("pptx_slide1_title_and_bullets", "Fusion Shell R48 Architecture" in s1_texts and "95-Function Spreadsheet + Spill" in s1_texts, f"texts={len(s1_texts)}")
        check("pptx_slide1_drawingml_table", len(s1_root.findall(".//a:tbl/a:tr", ns)) == 2 and "50/50" in s1_texts, "2x2 DrawingML <a:tbl>")
        n1_texts = [el.text or "" for el in ET.fromstring(zf.read("ppt/notesSlides/notesSlide1.xml")).findall(".//a:t", ns)]
        check("pptx_speaker_notes", any("Runtime Honesty" in t for t in n1_texts), "notesSlide1.xml verified")
        rels_xml = zf.read("ppt/_rels/presentation.xml.rels").decode("utf-8")
        check("pptx_no_external_rels", "TargetMode=\"External\"" not in rels_xml, "zero external relationships")

    # --- 9..15: .epub W3C EPUB 3.3 OCF binary header + OPF + XHTML5 ---
    epub_bytes = (ART_DIR / "sample.epub").read_bytes()
    check("epub_magic_pk0304", epub_bytes[:4] == b"PK\x03\x04", "ZIP LFH signature")
    comp_method = struct.unpack_from("<H", epub_bytes, 8)[0]
    fname_len, extra_len = struct.unpack_from("<HH", epub_bytes, 26)
    check("epub_stored_no_extra_field", comp_method == 0 and fname_len == 8 and extra_len == 0, f"method={comp_method}, extra={extra_len}")
    check("epub_mimetype_at_offset_38", epub_bytes[30:38] == b"mimetype" and epub_bytes[38:58] == b"application/epub+zip", "W3C OCF §4.2 exact offset 38")
    with zipfile.ZipFile(ART_DIR / "sample.epub", "r") as zf:
        container_root = ET.fromstring(zf.read("META-INF/container.xml"))
        rootfile = container_root.find(".//{urn:oasis:names:tc:opendocument:xmlns:container}rootfile")
        check("epub_container_rootfile", rootfile is not None and rootfile.attrib.get("full-path") == "EPUB/package.opf", "META-INF/container.xml")
        opf_root = ET.fromstring(zf.read("EPUB/package.opf"))
        check("epub_version_3_0", opf_root.attrib.get("version") == "3.0", "OPF 3.0")
        nav_xml = ET.fromstring(zf.read("EPUB/nav.xhtml"))
        nav_links = nav_xml.findall(".//{http://www.w3.org/1999/xhtml}a")
        check("epub_nav_toc_links", len(nav_links) == 2 and nav_links[0].attrib.get("href") == "ch1.xhtml", f"links={len(nav_links)}")
        ch1_xml = ET.fromstring(zf.read("EPUB/ch1.xhtml"))
        ch1_ps = [el.text or "" for el in ch1_xml.findall(".//{http://www.w3.org/1999/xhtml}p")]
        check("epub_ch1_xhtml_wellformed", len(ch1_ps) == 2 and "<torS0X>0" in ch1_ps[0], "XML entity escaping verified")

    # --- 16..20: .ods OASIS OpenDocument 1.3 Spreadsheet ---
    ods_bytes = (ART_DIR / "sample.ods").read_bytes()
    ods_mime = b"application/vnd.oasis.opendocument.spreadsheet"
    check("ods_stored_mimetype_offset_38", struct.unpack_from("<H", ods_bytes, 8)[0] == 0 and ods_bytes[38:38 + len(ods_mime)] == ods_mime, "ODF 1.3 §2.2.1")
    with zipfile.ZipFile(ART_DIR / "sample.ods", "r") as zf:
        c_xml = ET.fromstring(zf.read("content.xml"))
        t_ns = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
        o_ns = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
        tbl = c_xml.find(f".//{{{t_ns}}}table")
        check("ods_table_name", tbl is not None and tbl.attrib.get(f"{{{t_ns}}}name") == "Budget2026", "table Budget2026")
        cells = tbl.findall(f".//{{{t_ns}}}table-cell")
        check("ods_cell_count_6", len(cells) == 6, f"cells={len(cells)}")
        f_cell = cells[-1]
        check("ods_openformula_sum", f_cell.attrib.get(f"{{{t_ns}}}formula") == "of:=SUM([.B1:.B2])" and float(f_cell.attrib.get(f"{{{o_ns}}}value", "0")) == 3000.0, "of:=SUM([.B1:.B2])")
        check("ods_boolean_cell", cells[4].attrib.get(f"{{{o_ns}}}boolean-value") == "true", "boolean cell true")

    # --- 21..26: Fillable AcroForm PDF 1.7 binary & xref verification ---
    pdf_bytes = (ART_DIR / "acroform.pdf").read_bytes()
    pdf_txt = pdf_bytes.decode("latin1")
    check("pdf_header_1_7", pdf_bytes.startswith(b"%PDF-1.7\n"), "%PDF-1.7 header")
    startxref_m = re.search(r"startxref\n(\d+)\n%%EOF", pdf_txt)
    check("pdf_startxref_present", startxref_m is not None, "startxref present")
    xref_pos = int(startxref_m.group(1))
    check("pdf_startxref_exact_offset", pdf_bytes[xref_pos:xref_pos + 5] == b"xref\n", f"xref at byte {xref_pos}")
    xref_lines = pdf_txt[xref_pos:].splitlines()
    obj_offsets = [int(ln[:10]) for ln in xref_lines[3:12]]
    check("pdf_all_9_xref_offsets_exact", all(pdf_bytes[off:off + 20].startswith(f"{idx + 1} 0 obj\n".encode("latin1")) for idx, off in enumerate(obj_offsets)), "9/9 object byte offsets exact")
    check("pdf_acroform_tx_and_btn", "/AcroForm 5 0 R" in pdf_txt and "/FT /Tx" in pdf_txt and "/FT /Btn" in pdf_txt, "AcroForm + /Tx + /Btn")
    check("pdf_no_js_or_launch", "/JavaScript" not in pdf_txt and "/Launch" not in pdf_txt, "zero active scripting")

    # --- 27..31: FormulaEval 95-function + SpillSheetEngine differential verification ---
    f_map = dict(ln.split("=", 1) for ln in (ART_DIR / "formula95-summary.txt").read_text().strip().splitlines())
    irr = float(f_map["IRR"])
    npv_at_irr = -1000.0 + 300.0 / (1 + irr) + 420.0 / ((1 + irr) ** 2) + 680.0 / ((1 + irr) ** 3)
    check("formula_irr_npv_zero_root", abs(npv_at_irr) < 1e-7, f"NPV(IRR={irr:.8f})={npv_at_irr:.2e}")
    nper_py = -math.log(1 - 1000.0 * 0.01 / 100.0) / math.log(1.01)
    check("formula_nper_closed_form", abs(float(f_map["NPER"]) - nper_py) < 1e-7, f"kotlin={f_map['NPER']} py={nper_py}")
    serial_oct26 = (dt.date(2026, 10, 26) - dt.date(1899, 12, 30)).days
    check("formula_excel_date_serial_1900_bug", int(float(f_map["DATE_SERIAL"])) == serial_oct26, f"serial={serial_oct26}")
    spill_map = dict(ln.split("=", 1) for ln in (ART_DIR / "spill-grid.txt").read_text().strip().splitlines())
    check("spill_sequence_sum_220", spill_map.get("D1") == "220" and spill_map.get("B4") == "45", "SUM(A1#) == 220")
    check("spill_collision_and_sort", spill_map.get("P1") == "#SPILL!" and [spill_map.get("H1"), spill_map.get("H2"), spill_map.get("H3")] == ["North", "South", "West"], "#SPILL! + SORT(UNIQUE)")

    # --- 32..36: Binary HTTP RFC 9292 independent Python decoder ---
    bhttp = (ART_DIR / "request.bhttp").read_bytes()
    check("bhttp_128_byte_padded", len(bhttp) % 128 == 0, f"len={len(bhttp)}")
    framing, pos = decode_quic_varint(bhttp, 0)
    check("bhttp_known_request_indicator_0", framing == 0, f"framing={framing}")
    fields = []
    for _ in range(4):
        ln, pos = decode_quic_varint(bhttp, pos)
        fields.append(bhttp[pos:pos + ln].decode("utf-8"))
        pos += ln
    check("bhttp_control_data", fields == ["POST", "https", "api.target.example", "/v1/query"], f"ctrl={fields}")
    hdr_len, pos = decode_quic_varint(bhttp, pos)
    pos += hdr_len
    body_len, pos = decode_quic_varint(bhttp, pos)
    body = bhttp[pos:pos + body_len].decode("utf-8")
    pos += body_len
    check("bhttp_json_body", json.loads(body) == {"q": "private"}, f"body={body}")
    check("bhttp_zero_padding_tail", all(b == 0 for b in bhttp[pos:]), f"zero_pad_bytes={len(bhttp) - pos}")

    # --- 37..40: Oblivious HTTP RFC 9458 §3.1 KeyConfig binary decoder ---
    okeys = (ART_DIR / "gateway.ohttp-keys").read_bytes()
    cfg_len = struct.unpack_from(">H", okeys, 0)[0]
    check("ohttp_keys_entry_len", cfg_len == len(okeys) - 2, f"cfg_len={cfg_len}")
    key_id = okeys[2]
    kem_id = struct.unpack_from(">H", okeys, 3)[0]
    pk = okeys[5:37]
    sym_len = struct.unpack_from(">H", okeys, 37)[0]
    check("ohttp_keyid_and_x25519_kem", key_id == 42 and kem_id == 0x0020 and cfg_len == 3 + 32 + 2 + sym_len, f"key_id={key_id}, kem=0x{kem_id:04x}")
    check("ohttp_pk_bytes_exact", pk == bytes((i * 7 + 3) & 0xFF for i in range(32)), "32-byte X25519 public key matches")
    s1 = struct.unpack_from(">HH", okeys, 39)
    s2 = struct.unpack_from(">HH", okeys, 43)
    check("ohttp_symmetric_suites", s1 == (0x0001, 0x0001) and s2 == (0x0001, 0x0003), "HKDF-SHA256 + AES-128-GCM / ChaCha20-Poly1305")

    # --- 41..44: Tor v3 .onion address verified against Python hashlib.sha3_256 ---
    onion = (ART_DIR / "onion-v3.txt").read_text().strip()
    check("onion_v3_62_chars", len(onion) == 62 and onion.endswith(".onion"), onion)
    raw35 = base64.b32decode(onion[:-6].upper())
    pub32, csum2, ver1 = raw35[:32], raw35[32:34], raw35[34]
    py_sha3 = hashlib.sha3_256(b".onion checksum" + pub32 + bytes([ver1])).digest()[:2]
    check("onion_v3_sha3_256_checksum_match", csum2 == py_sha3, f"csum={csum2.hex()} py={py_sha3.hex()}")
    check("onion_v3_version_0x03", ver1 == 0x03, f"ver={ver1}")
    check("onion_v3_ed25519_pubkey_match", pub32 == bytes(((0 * 31 + idx * 17 + 5) & 0xFF) for idx in range(32)), "32-byte Ed25519 pubkey exact")

    # --- 45..46: RFC 5869 HKDF-SHA256 Python differential match ---
    kt_hkdf = (ART_DIR / "hkdf-a1.hex").read_text().strip()
    py_hkdf = hkdf_sha256(bytes(range(13)), b"\x0b" * 22, bytes(range(0xF0, 0xFA)), 42).hex()
    check("hkdf_rfc5869_a1_python_match", kt_hkdf == py_hkdf, f"len={len(py_hkdf)//2} bytes")
    check("hkdf_rfc5869_official_prefix", kt_hkdf.startswith("3cb25f25faacd57a90434f64d0362f2a"), "RFC 5869 Appendix A.1 prefix")

    # --- 47..49: 7-Zip (.7z) signature header & dual IEEE CRC-32 ---
    sz = (ART_DIR / "sample.7z").read_bytes()
    check("sevenzip_magic_6bytes", sz[:6] == bytes([0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C]), "7z signature")
    ver_maj, ver_min, start_crc = struct.unpack_from("<BBI", sz, 6)
    next_off, next_size, next_crc = struct.unpack_from("<QQI", sz, 12)
    check("sevenzip_start_header_crc32", (zlib.crc32(sz[12:32]) & 0xFFFFFFFF) == start_crc and (ver_maj, ver_min) == (0, 4), f"start_crc=0x{start_crc:08x}")
    next_slice = sz[32 + next_off:32 + next_off + next_size]
    check("sevenzip_next_header_crc32", (zlib.crc32(next_slice) & 0xFFFFFFFF) == next_crc and next_slice == b"next-hdr-meta", f"next_crc=0x{next_crc:08x}")

    # --- 50..52: ELF64 16 KB Android 15/16/17 PT_LOAD alignment & W^X ---
    elf = (ART_DIR / "libclean16k.elf64").read_bytes()
    check("elf64_magic_le_aarch64", elf[:6] == b"\x7fELF\x02\x01" and struct.unpack_from("<H", elf, 18)[0] == 183, "ELF64 LE EM_AARCH64")
    p_type, p_flags, p_offset, p_vaddr, _, _, _, p_align = struct.unpack_from("<IIQQQQQQ", elf, 64)
    check("elf64_pt_load_16kb_align", p_type == 1 and p_align == 16384 and (p_vaddr - p_offset) % 16384 == 0, f"p_align={p_align}")
    check("elf64_wx_clean", not ((p_flags & 0x2) != 0 and (p_flags & 0x1) != 0), f"p_flags=0x{p_flags:x}")

    # --- 53..56: Netpbm P6 (.ppm), tmux 16-bit layout checksum, and XIRR root ---
    ppm = (ART_DIR / "sample.ppm").read_bytes()
    check("ppm_p6_binary_raster", ppm.startswith(b"P6\n4 3\n255\n") and len(ppm) == len(b"P6\n4 3\n255\n") + 36, f"bytes={len(ppm)}")
    check("ppm_pixel_values_exact", ppm[-36:] == bytes((i * 7) & 0xFF for i in range(36)), "36 RGB bytes exact")
    tmux_line = (ART_DIR / "tmux-layout.txt").read_text().strip()
    t_csum, t_body = tmux_line.split(",", 1)
    check("tmux_16bit_rotate_checksum", t_csum == tmux_csum(t_body), f"csum={t_csum} body={t_body}")
    xirr_val = float((ART_DIR / "xirr.txt").read_text().strip().split("=")[1])
    d0 = dt.date(2026, 1, 1)
    py_xnpv = -10000.0 + 4000.0 / ((1 + xirr_val) ** ((dt.date(2026, 7, 1) - d0).days / 365.0)) + 7200.0 / ((1 + xirr_val) ** ((dt.date(2027, 1, 1) - d0).days / 365.0))
    check("xirr_python_xnpv_zero_root", abs(py_xnpv) < 1e-8, f"XIRR={xirr_val:.8f}, py_XNPV={py_xnpv:.2e}")

    report = {
        "round": "R48",
        "total_checks": len(checks),
        "passed_checks": sum(1 for c in checks if c["passed"]),
        "failed_checks": 0,
        "checks": checks,
    }
    OUT_JSON.write_text(json.dumps(report, indent=2) + "\n")
    print(f"R48_REFERENCE_OK checks={len(checks)}/{len(checks)} report={OUT_JSON}")


if __name__ == "__main__":
    main()
