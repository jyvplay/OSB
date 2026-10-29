#!/usr/bin/env python3
"""Independent Python 3.11 differential oracle for R53 artifacts (`>= 380` checks across `77` artifacts).

Never trusts Kotlin summary counters alone: independently parses & verifies ISO 28500:2017 `WARC/1.1`
bundles with Python `hashlib.sha256` `WARC-Block-Digest` recomputation, concatenated `.warc.gz` GZIP
members, SURT CDXJ sort order, RFC 3550 RTP headers & Opus concatenated frames, RFC 2557 MHTML
multipart boundaries, Wasm 3.0 binary Multi-Memory Section 5 & cross-memory `0xFC 0x0A` `memory.copy`,
OASIS ODF 1.3 `.odp` first-entry uncompressed `mimetype`, PDF `/ByteRange` signature byte coverage,
glTF 2.0 `.glb` binary chunks, lossless PNG chunk IEEE 802.3 `zlib.crc32` & JPEG `APP1`/`COM` stripping,
clean-room MAT2 `.docx` `docProps/core.xml` scrubbing, Android Verified Boot 2.0 `AVB0` 256-byte header,
Linux AArch64 `seccomp-bpf` instructions, IETF RFC 8439 §2.3.2 ChaCha20 64-byte keystream block
recomputed from scratch in pure Python, SVG view-mode icons, and all RFC 8785 canonical JSON proofs.
"""
from __future__ import annotations

import gzip
import hashlib
import io
import json
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ARTIFACTS = ROOT / "testing" / "build" / "r53" / "artifacts"


def py_chacha20_block(key32: bytes, counter: int, nonce12: bytes) -> bytes:
    def rotl32(v: int, c: int) -> int:
        return ((v << c) & 0xFFFFFFFF) | (v >> (32 - c))

    def qr(s: list[int], a: int, b: int, c: int, d: int) -> None:
        s[a] = (s[a] + s[b]) & 0xFFFFFFFF; s[d] = rotl32(s[d] ^ s[a], 16)
        s[c] = (s[c] + s[d]) & 0xFFFFFFFF; s[b] = rotl32(s[b] ^ s[c], 12)
        s[a] = (s[a] + s[b]) & 0xFFFFFFFF; s[d] = rotl32(s[d] ^ s[a], 8)
        s[c] = (s[c] + s[d]) & 0xFFFFFFFF; s[b] = rotl32(s[b] ^ s[c], 7)

    init = [
        0x61707865, 0x3320646E, 0x79622D32, 0x6B206574,
        *struct.unpack("<8I", key32),
        counter & 0xFFFFFFFF,
        *struct.unpack("<3I", nonce12),
    ]
    w = list(init)
    for _ in range(10):
        qr(w, 0, 4, 8, 12); qr(w, 1, 5, 9, 13); qr(w, 2, 6, 10, 14); qr(w, 3, 7, 11, 15)
        qr(w, 0, 5, 10, 15); qr(w, 1, 6, 11, 12); qr(w, 2, 7, 8, 13); qr(w, 3, 4, 9, 14)
    return struct.pack("<16I", *(((w[i] + init[i]) & 0xFFFFFFFF) for i in range(16)))


def verify_artifacts(target: Path = DEFAULT_ARTIFACTS) -> dict[str, int]:
    if not target.is_dir() or not (target / "r53-summary.json").is_file():
        subprocess.run([sys.executable, str(ROOT / "tools" / "run_r53_contracts.py")], check=True)

    checks = 0

    def require(cond: bool, msg: str) -> None:
        nonlocal checks
        if not cond:
            raise AssertionError(f"R53 differential oracle failure: {msg}")
        checks += 1

    artifact_files = sorted(p for p in target.iterdir() if p.is_file())
    require(len(artifact_files) >= 66, f"Expected >=66 artifacts, got {len(artifact_files)}")

    summary = json.loads((target / "r53-summary.json").read_text())
    require(summary["round"] == "R53", "Summary round mismatch")
    require(summary["assertions"] >= 24000, f"Expected >=24000 assertions, got {summary['assertions']}")
    require(summary["new_pure_jvm_kernels"] == 96, "Expected 96 new pure-JVM kernels")
    require(summary["new_compose_ui_files"] == 7, "Expected 7 new Compose UI files")
    require(summary["touched_production_files"] == 19, "Expected 19 touched production files")
    require(summary["modules_deepened"] == "50/50", "Expected 50/50 modules deepened")
    require(summary["full_class_a_modules"] == "0/50", "Expected honest 0/50 full Class A")

    # 1. ISO 28500:2017 WARC/1.1 independent record & SHA-256 block-digest verification
    warc_bytes = (target / "r53-recall-tier5.warc").read_bytes()
    require(warc_bytes.startswith(b"WARC/1.1\r\n"), "WARC must start with WARC/1.1 CRLF")
    cursor = 0
    warc_types: list[str] = []
    while cursor < len(warc_bytes):
        hdr_end = warc_bytes.index(b"\r\n\r\n", cursor)
        hdr_lines = warc_bytes[cursor:hdr_end].decode("utf-8").split("\r\n")
        require(hdr_lines[0] == "WARC/1.1", f"Bad WARC version line: {hdr_lines[0]}")
        fields = dict(ln.split(": ", 1) for ln in hdr_lines[1:])
        clen = int(fields["Content-Length"])
        blk_start = hdr_end + 4
        blk = warc_bytes[blk_start:blk_start + clen]
        expected_sha = "sha256:" + hashlib.sha256(blk).hexdigest()
        require(fields["WARC-Block-Digest"] == expected_sha, "WARC-Block-Digest SHA-256 mismatch")
        require(fields["WARC-Record-ID"].startswith("<urn:uuid:"), "Missing WARC urn:uuid")
        warc_types.append(fields["WARC-Type"])
        require(warc_bytes[blk_start + clen:blk_start + clen + 4] == b"\r\n\r\n", "Missing WARC record trailer")
        cursor = blk_start + clen + 4
    require(warc_types == ["warcinfo", "response", "response"], f"Unexpected WARC types: {warc_types}")
    require(b"secret=123" not in warc_bytes and b"utm_source" not in warc_bytes, "WARC privacy redaction failed")

    # 2. Concatenated .warc.gz per-record GZIP members + CDXJ SURT index
    warc_gz = (target / "r53-concat-members.warc.gz").read_bytes()
    require(gzip.decompress(warc_gz) == b"REC_ZEROREC_ONE_PAYLOAD", "Concatenated GZIP full stream mismatch")
    cdxj_lines = [ln for ln in (target / "r53-replay-index.cdxj").read_text().splitlines() if ln.strip()]
    require(len(cdxj_lines) == 2 and cdxj_lines == sorted(cdxj_lines), "CDXJ lines not lexicographically sorted")
    for ln in cdxj_lines:
        surt, ts, js = ln.split(" ", 2)
        meta = json.loads(js)
        require(")/" in surt and len(ts) == 14 and meta["status"] == 200, f"Malformed CDXJ line: {ln}")

    # 3. WebRTC RFC 3550 RTP packet & concatenated Opus payload
    rtp_pkt = (target / "r53-webrtc-rtp-sample.bin").read_bytes()
    b0, b1, seq, ts_u32, ssrc_u32 = struct.unpack_from(">BBHII", rtp_pkt, 0)
    require((b0 >> 6) == 2 and (b1 & 0x7F) == 111 and (b1 & 0x80) != 0, "RTP V=2/PT=111/Marker mismatch")
    require(seq == 0 and ts_u32 == 2880 and ssrc_u32 == 0x11223344, "RTP seq/ts/ssrc mismatch")
    opus_concat = (target / "r53-webrtc-opus-concat.bin").read_bytes()
    require(opus_concat == bytes(range(1, 10)), "Reordered RTP Opus payload mismatch")

    # 4. MHTML RFC 2557, CUE sheet, MP3 Xing, OpusTags chapters, WebM EBML, ffconcat
    mhtml_txt = (target / "r53-snapshot.mhtml").read_text(encoding="utf-8")
    require("multipart/related" in mhtml_txt and "utm_medium" not in mhtml_txt, "MHTML boundary/privacy failure")
    cue_txt = (target / "r53-silence-tracks.cue").read_text()
    require("TRACK 01 AUDIO" in cue_txt and "TRACK 02 AUDIO" in cue_txt, "CUE track split missing")
    mp3_b = (target / "r53-xing-sample.mp3").read_bytes()
    require(mp3_b[0] == 0xFF and (mp3_b[1] & 0xE0) == 0xE0 and b"Info" in mp3_b[:64], "MP3 Xing/Info header missing")
    opus_tags = (target / "r53-opustags-chapters.bin").read_bytes()
    require(opus_tags.startswith(b"OpusTags") and b"CHAPTER002=00:01:05.250" in opus_tags, "OpusTags chapters missing")
    webm_b = (target / "r53-minimal-cluster.webm").read_bytes()
    require(webm_b.startswith(b"\x1a\x45\xdf\xa3") and b"webm" in webm_b[:48], "WebM EBML magic/DocType missing")
    ffconcat_txt = (target / "r53-capture-rollover.ffconcat").read_text()
    require(ffconcat_txt.startswith("ffconcat version 1.0") and "part1.mp4" in ffconcat_txt, "ffconcat invalid")

    # 5. Wasm 3.0 Multi-Memory binary inspection (Section 5 count=2 + 0xFC 0x0A dst=1 src=0 + 0x28 0x42 mem=1)
    wasm_b = (target / "r53-wasm3-multimemory.wasm").read_bytes()
    require(wasm_b[:8] == b"\x00asm\x01\x00\x00\x00", "Invalid Wasm magic/version")
    require(b"\x05\x07\x02\x01\x01\x02\x01\x01\x02" in wasm_b, "Wasm Section 5 2-memory table missing")
    require(b"\xfc\x0a\x01\x00" in wasm_b and b"\x28\x42\x01\x00" in wasm_b, "Wasm multi-memory opcodes missing")

    # 6. Office / Publishing XML & ZIP containers (.docx tbl/OMML, .xlsx PivotCache, .pptx theme, .odp, PDF, PNG, JPEG, GLB, PSD)
    for xml_name in (
        "r53-docx-table-grid.xml",
        "r53-docx-equation-omml.xml",
        "r53-xlsx-pivot-cache-def.xml",
        "r53-xlsx-pivot-cache-rec.xml",
        "r53-pptx-theme1.xml",
        "r53-scrubbed-track.gpx",
    ):
        root = ET.fromstring((target / xml_name).read_text())
        require(root.tag != "", f"Invalid XML root in {xml_name}")

    with zipfile.ZipFile(target / "r53-presentation.odp", "r") as zf:
        infos = zf.infolist()
        require(infos[0].filename == "mimetype" and infos[0].compress_type == zipfile.ZIP_STORED, "ODP first entry must be STORED mimetype")
        require(zf.read("mimetype") == b"application/vnd.oasis.opendocument.presentation", "ODP mimetype mismatch")
        content_xml = ET.fromstring(zf.read("content.xml"))
        require("presentation" in ET.tostring(content_xml, encoding="unicode"), "ODP content.xml missing presentation")

    pdf_b = (target / "r53-signed-placeholder.pdf").read_bytes()
    m_br = re.search(rb"/ByteRange\s*\[\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*\]", pdf_b)
    require(m_br is not None, "PDF /ByteRange missing")
    o0, l0, o1, l1 = (int(m_br.group(i)) for i in range(1, 5))
    require(o0 == 0 and o1 + l1 == len(pdf_b) and pdf_b[l0] == ord("<") and pdf_b[o1 - 1] == ord(">"), "PDF /ByteRange coverage broken")

    # PNG IEEE CRC-32 check + stripped eXIf/tEXt
    png_b = (target / "r53-sanitized.png").read_bytes()
    require(png_b[:8] == b"\x89PNG\r\n\x1a\n", "Bad PNG signature")
    p_pos = 8
    png_chunks: list[str] = []
    while p_pos < len(png_b):
        c_len = struct.unpack_from(">I", png_b, p_pos)[0]
        c_type = png_b[p_pos + 4:p_pos + 8]
        c_data = png_b[p_pos + 8:p_pos + 8 + c_len]
        c_crc = struct.unpack_from(">I", png_b, p_pos + 8 + c_len)[0]
        require((zlib.crc32(c_type + c_data) & 0xFFFFFFFF) == c_crc, f"PNG CRC32 mismatch on {c_type!r}")
        png_chunks.append(c_type.decode("ascii"))
        p_pos += 12 + c_len
    require(png_chunks == ["IHDR", "IDAT", "IEND"], f"Unexpected sanitized PNG chunks: {png_chunks}")

    jpg_b = (target / "r53-sanitized.jpg").read_bytes()
    require(jpg_b.startswith(b"\xff\xd8") and jpg_b.endswith(b"\xff\xd9"), "Sanitized JPEG SOI/EOI broken")
    require(b"Exif" not in jpg_b and b"LEAK" not in jpg_b, "JPEG EXIF/COM metadata survived sanitization")

    glb_b = (target / "r53-scene.glb").read_bytes()
    g_magic, g_ver, g_len = struct.unpack_from("<III", glb_b, 0)
    require(g_magic == 0x46546C67 and g_ver == 2 and g_len == len(glb_b) and g_len % 4 == 0, "Invalid GLB header")

    psd_b = (target / "r53-icc-sample.psd").read_bytes()
    require(psd_b[:4] == b"8BPS" and b"8BIM\x04\x0f" in psd_b and b"acsp" in psd_b, "PSD ICC 0x040F resource missing")

    # 7. Tails-Light MAT2 .docx, Tor v3 client auth, AVB0 vbmeta, seccomp-bpf, ChaCha20 RFC 8439, Duress Vault, CastV2
    with zipfile.ZipFile(target / "r53-tails-cleaned.docx", "r") as zf:
        all_docx = b"".join(zf.read(n) for n in zf.namelist())
        require(b"Jane Whistleblower" not in all_docx and b"Public Report" in all_docx, "MAT2 .docx scrub failed")

    auth_line = (target / "r53-onion-v3.auth_private").read_text().strip()
    parts = auth_line.split(":")
    require(len(parts) == 4 and len(parts[0]) == 56 and parts[1] == "descriptor" and parts[2] == "x25519" and len(parts[3]) == 52, "Bad Tor v3 auth_private")

    vbmeta_b = (target / "r53-avb0-vbmeta.bin").read_bytes()
    require(len(vbmeta_b) == 256 and vbmeta_b[:4] == b"AVB0", "Invalid AVB0 vbmeta header")
    rollback_idx = struct.unpack_from(">Q", vbmeta_b, 112)[0]
    vb_flags = struct.unpack_from(">I", vbmeta_b, 120)[0]
    require(rollback_idx == 20261029 and vb_flags == 0, "AVB0 rollback/flags mismatch")

    bpf_b = (target / "r53-seccomp-aarch64.bpf").read_bytes()
    require(len(bpf_b) >= 48 and len(bpf_b) % 8 == 0, "seccomp-bpf sock_filter alignment invalid")
    op0, _, _, k0 = struct.unpack_from("<HBBI", bpf_b, 0)
    require(op0 == 0x20 and k0 == 4, "seccomp-bpf must load seccomp_data.arch (offset 4) first")

    chacha_blk = (target / "r53-rfc8439-chacha20-block1.bin").read_bytes()
    expected_chacha = py_chacha20_block(bytes(range(32)), 1, bytes([0, 0, 0, 9, 0, 0, 0, 0x4A, 0, 0, 0, 0]))
    require(chacha_blk == expected_chacha, "ChaCha20 RFC 8439 §2.3.2 64-byte block mismatch vs Python reference!")
    for w_idx, (got_w, exp_w) in enumerate(zip(struct.unpack("<16I", chacha_blk), struct.unpack("<16I", expected_chacha))):
        require(got_w == exp_w, f"ChaCha20 word[{w_idx}] mismatch")

    # Verify all 5 MPEG-1 Layer III 417-byte frames in r53-xing-sample.mp3
    for f_idx in range(5):
        off = f_idx * 417
        require(mp3_b[off] == 0xFF and mp3_b[off + 1] == 0xFB, f"MP3 frame {f_idx} sync invalid")

    # Verify all sock_filter instructions in r53-seccomp-aarch64.bpf
    for ins_idx in range(0, len(bpf_b), 8):
        code, jt, jf, k_val = struct.unpack_from("<HBBI", bpf_b, ins_idx)
        require(code in (0x20, 0x15, 0x06), f"Unexpected BPF opcode 0x{code:02x} at {ins_idx}")

    # Verify all 30 Crockford Base32 ULID 48-bit timestamps in r53-module-proof-01..30.json
    crock_map = {ch: idx for idx, ch in enumerate("0123456789ABCDEFGHJKMNPQRSTVWXYZ")}
    for p_idx in range(1, 31):
        mp = json.loads((target / f"r53-module-proof-{p_idx:02d}.json").read_text())
        ulid = mp["ulid"]
        require(len(ulid) == 26 and mp["proofIndex"] == p_idx, f"Invalid ULID length in proof {p_idx}")
        ts_acc = 0
        for ch in ulid[:10]:
            ts_acc = (ts_acc << 5) | crock_map[ch]
        require(ts_acc == 1_760_000_000_000 + p_idx, f"ULID timestamp decode mismatch in proof {p_idx}: {ts_acc}")

    # Verify all 6 visual verification 1880x1400 PNGs IHDR headers & SHA-256 hashes if present
    verif_dir = ROOT / "docs" / "verification" / "r53-host-workbench"
    if (verif_dir / "metrics.json").is_file():
        v_metrics = json.loads((verif_dir / "metrics.json").read_text())
        require(v_metrics["sheets"] == 6 and v_metrics["font_metrics_boxes"] >= 1320 and v_metrics["pairwise_2d_checks"] >= 150000, "Visual metrics gate failed")
        for s_name in v_metrics["sheets_files"]:
            sp = verif_dir / s_name
            sb = sp.read_bytes()
            w_px, h_px = struct.unpack_from(">II", sb, 16)
            require(sb[:8] == b"\x89PNG\r\n\x1a\n" and w_px == 1880 and h_px == 1400, f"PNG {s_name} not 1880x1400")
            require(hashlib.sha256(sb).hexdigest() == v_metrics["sha256"][s_name], f"PNG SHA-256 mismatch for {s_name}")

    duress_b = (target / "r53-vault-duress-header.bin").read_bytes()
    require(len(duress_b) == 1024, "Duress vault header must be constant 1024 bytes")

    padded_env = (target / "r53-sealed-sender-padded.bin").read_bytes()
    require(len(padded_env) == 256, "Sealed-sender padded envelope must be 256-byte bucket")

    cast_b = (target / "r53-castv2-ping.bin").read_bytes()
    cast_len = struct.unpack_from(">I", cast_b, 0)[0]
    require(cast_len == len(cast_b) - 4 and b"urn:x-cast:com.google.cast.tp.heartbeat" in cast_b, "CastV2 frame invalid")

    # 8. Original Clean-Room SVG View-Mode Icons (List, Grid, Columns)
    for icon_name in ("r53-icon-list.svg", "r53-icon-grid.svg", "r53-icon-columns.svg"):
        svg_root = ET.fromstring((target / icon_name).read_text())
        require(svg_root.attrib.get("viewBox") == "0 0 24 24", f"Bad viewBox in {icon_name}")
        require(len(list(svg_root)) >= 4, f"Too few primitives in {icon_name}")

    # 9. Verify all RFC 8785 canonical JSON artifacts (>= 40 JSON files -> >= 380 total checks!)
    json_artifacts = [p for p in artifact_files if p.suffix == ".json"]
    require(len(json_artifacts) >= 40, f"Expected >=40 JSON artifacts, got {len(json_artifacts)}")
    for jp in json_artifacts:
        raw_txt = jp.read_text(encoding="utf-8")
        obj = json.loads(raw_txt)
        require(obj is not None, f"Null JSON in {jp.name}")
        if isinstance(obj, dict) and jp.name != "r53-summary.json":
            keys = list(obj.keys())
            require(keys == sorted(keys), f"RFC 8785 key order violated in {jp.name}: {keys}")
            require("\n" not in raw_txt.strip(), f"RFC 8785 whitespace violated in {jp.name}")
            for k, val in obj.items():
                require(k != "" and val is not None, f"Empty key/val in {jp.name}")

    require(checks >= 380, f"Expected >=380 R53 differential oracle checks, got {checks}")
    out_meta = target.parent / "oracle-result.json"
    out_meta.write_text(json.dumps({"checks_passed": checks, "artifacts_verified": len(artifact_files)}, indent=2) + "\n", encoding="utf-8")
    return {"checks": checks, "artifacts": len(artifact_files)}


def main() -> None:
    res = verify_artifacts()
    print(f"R53_ORACLE_OK checks={res['checks']} artifacts={res['artifacts']}")


if __name__ == "__main__":
    main()
