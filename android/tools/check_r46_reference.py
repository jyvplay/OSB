#!/usr/bin/env python3
"""Independent Python + openpyxl + tarfile + quopri + PDF-xref differential oracle for R46.

Executes compiled Kotlin R46 production kernels on the host JVM and verifies byte/numeric
agreement against independent Python implementations and held-out standard/third-party parsers:
  1. `openpyxl` bidirectional multi-sheet `.xlsx` (`data_only=False` + `data_only=True` + foreign read)
  2. Independent ISO 32000-1 PDF 1.7 `xref` byte-offset & `/Pages` validator (`PdfPageWriter`)
  3. Python `tarfile` bidirectional POSIX.1-2001 `pax` extended-header TAR (`TarPax`)
  4. Python `quopri` & `email.header` RFC 2045 / RFC 2047 MIME codec (`QuotedPrintable`)
  5. Python `hmac`/`hashlib` (`MacRandomizer`), `zlib.crc32` (`PngMetadataStrip`), `SheetPivot`, & `WasmSandbox`
"""
from __future__ import annotations

import argparse
import email.header
import hashlib
import hmac
import io
import json
import math
import os
from pathlib import Path
import quopri
import re
import struct
import subprocess
import sys
import tarfile
import tempfile
import zlib

_HOST_PY = Path(os.environ.get("FUSION_HOST_CACHE", str(Path.home() / ".cache/fusion-host-r23"))) / "python"
if _HOST_PY.is_dir() and str(_HOST_PY) not in sys.path:
    sys.path.insert(0, str(_HOST_PY))

import openpyxl  # installed by bootstrap_host_tools.py in ~/.cache/fusion-host-r23/python

import run_r46_contracts

ROOT = Path(__file__).resolve().parents[1]
BRIDGE_DIR = ROOT / "testing/build/r46-ref-bridge"

KOTLIN_PROBE_SRC = r"""
package dev.fusion.testing

import dev.fusion.archive.TarPax
import dev.fusion.capabilities.WasmSandbox
import dev.fusion.format.image.PngMetadataStrip
import dev.fusion.format.pdf.PdfPageWriter
import dev.fusion.messages.QuotedPrintable
import dev.fusion.ooxml.FormulaEval
import dev.fusion.ooxml.MultiSheetBook
import dev.fusion.ooxml.SheetPivot
import dev.fusion.security.MacRandomizer
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

object R46RefProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val workDir = Paths.get(args[0])
        val foreignXlsx = Paths.get(args[1])
        val foreignTar = Paths.get(args[2])

        // 1. Multi-sheet .xlsx -> openpyxl
        val bookBytes = MultiSheetBook.write(
            listOf(
                MultiSheetBook.SheetDef(
                    "Q1 Sales",
                    linkedMapOf(
                        "A1" to FormulaEval.CellInput.TextLiteral("North"),
                        "B1" to FormulaEval.CellInput.NumberLiteral(1200.50),
                        "A2" to FormulaEval.CellInput.TextLiteral("South"),
                        "B2" to FormulaEval.CellInput.NumberLiteral(799.50),
                        "A3" to FormulaEval.CellInput.TextLiteral("North"),
                        "B3" to FormulaEval.CellInput.NumberLiteral(500.00),
                        "B4" to FormulaEval.CellInput.Formula("=SUM(B1:B3)"),
                        "B5" to FormulaEval.CellInput.Formula("=SUMIF(A1:A3, \"North\", B1:B3)"),
                        "B6" to FormulaEval.CellInput.Formula("=MEDIAN(B1:B3)"),
                    ),
                ),
                MultiSheetBook.SheetDef(
                    "Rates",
                    linkedMapOf(
                        "A1" to FormulaEval.CellInput.TextLiteral("TaxRate"),
                        "B1" to FormulaEval.CellInput.NumberLiteral(0.085),
                    ),
                ),
                MultiSheetBook.SheetDef(
                    "Summary",
                    linkedMapOf(
                        "A1" to FormulaEval.CellInput.Formula("='Q1 Sales'!B4"),
                        "A2" to FormulaEval.CellInput.Formula("=ROUND(A1 * Rates!B1, 2)"),
                        "A3" to FormulaEval.CellInput.Formula("=VLOOKUP(\"South\", 'Q1 Sales'!A1:B3, 2, FALSE)"),
                        "A4" to FormulaEval.CellInput.Formula("=IFNA(VLOOKUP(\"West\", 'Q1 Sales'!A1:B3, 2, FALSE), 0)"),
                    ),
                ),
            ),
        )
        Files.write(workDir.resolve("kotlin_multisheet.xlsx"), bookBytes)

        // 2. Read openpyxl-authored multi-sheet .xlsx and evaluate in Kotlin
        val loadedForeign = MultiSheetBook.read(Files.readAllBytes(foreignXlsx))
        val evalForeign = MultiSheetBook.evaluate(loadedForeign)

        // 3. Emit paginated PDF 1.7
        val pdfElements = buildList {
            add(PdfPageWriter.Element.Heading(1, "R46 Differential PDF Oracle"))
            add(PdfPageWriter.Element.Table(listOf(listOf("Metric", "Value"), listOf("Sheets", "3"), listOf("Total", "2500.00"))))
            repeat(34) { idx ->
                add(PdfPageWriter.Element.Paragraph("Paragraph ${idx + 1}: clean-room ISO 32000-1 stream line for multi-page verification."))
            }
        }
        val renderedPdf = PdfPageWriter.render(pdfElements, PdfPageWriter.PageSetup(headerTitle = "Fusion R46"))
        Files.write(workDir.resolve("kotlin_out.pdf"), renderedPdf.bytes)

        // 4. Emit deterministic POSIX PAX tar + read Python-authored PAX tar
        val longUtf8Path = "reports/2026/" + "nested_audit_folder_".repeat(6) + "données_ finales.txt"
        val tarBytes = TarPax.writeDeterministic(
            listOf(
                TarPax.TarFile("README.txt", "Fusion TAR PAX\n".toByteArray(StandardCharsets.UTF_8)),
                TarPax.TarFile(longUtf8Path, "UTF-8 payload αβγ".toByteArray(StandardCharsets.UTF_8)),
            ),
        )
        Files.write(workDir.resolve("kotlin_out.tar"), tarBytes)
        val pyTarEntries = TarPax.read(Files.readAllBytes(foreignTar))

        // 5. Quoted-Printable + RFC 2047
        val qpSample = "Café = 100% pure JVM! Trailing tab\t\r\n" + ("Long UTF-8 segment éàü ".repeat(6))
        val qpEncoded = QuotedPrintable.encodeUtf8(qpSample)
        Files.writeString(workDir.resolve("kotlin_qp.txt"), qpEncoded, StandardCharsets.US_ASCII)
        val rfc2047Decoded = QuotedPrintable.decodeHeaderWords("=?UTF-8?B?RnVzaW9u?= =?UTF-8?Q?_R46_V=C3=A9rifi=C3=A9?=")

        // 6. PNG Metadata Strip
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val ihdr = byteArrayOf(0, 0, 0, 4, 0, 0, 0, 4, 8, 2, 0, 0, 0)
        val idat = byteArrayOf(0x78, 0x9C.toByte(), 0x63, 0x60, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01)
        val dirtyPng = ByteArrayOutputStream().apply {
            write(sig)
            write(PngMetadataStrip.buildChunk("IHDR", ihdr))
            write(PngMetadataStrip.buildChunk("tEXt", "Comment\u0000SecretHost".toByteArray()))
            write(PngMetadataStrip.buildChunk("IDAT", idat))
            write(PngMetadataStrip.buildChunk("IEND", ByteArray(0)))
        }.toByteArray()
        val cleanPng = PngMetadataStrip.inspectAndStrip(dirtyPng)
        Files.write(workDir.resolve("kotlin_clean.png"), cleanPng.cleanBytes)

        // 7. MacRandomizer HMAC-SHA256 derivation
        val secret = "0123456789abcdef0123456789abcdef".toByteArray(StandardCharsets.US_ASCII)
        val mac = MacRandomizer.derivePerNetworkMac(secret, "wlan0", "AirportFreeWiFi", 7L)

        // 8. SheetPivot + WasmSandbox fib(20)
        val piv = SheetPivot.summarize(
            listOf("Dept", "Qtr", "Cost"),
            listOf(
                listOf("Eng", "Q1", "400"),
                listOf("Eng", "Q2", "600"),
                listOf("Sec", "Q1", "250"),
                listOf("Sec", "Q2", "350"),
            ),
            SheetPivot.PivotSpec("Dept", "Qtr", "Cost", SheetPivot.AggFunc.SUM),
        )
        val wasmFib20 = buildFibWasmAndRun(20)

        val summary = buildString {
            append("{\n")
            append("  \"foreign_total\": ${(evalForeign.cell("Rollup", "B2") as FormulaEval.CellValue.Num).value},\n")
            append("  \"foreign_vlookup\": ${(evalForeign.cell("Rollup", "B3") as FormulaEval.CellValue.Num).value},\n")
            append("  \"pdf_pages\": ${renderedPdf.pageCount},\n")
            append("  \"py_tar_count\": ${pyTarEntries.size},\n")
            append("  \"py_tar_long_utf8\": \"${pyTarEntries.last().data.toString(StandardCharsets.UTF_8)}\",\n")
            append("  \"rfc2047\": \"$rfc2047Decoded\",\n")
            append("  \"mac\": \"${mac.formatColonLower()}\",\n")
            append("  \"pivot_grand\": ${piv.grandTotal},\n")
            append("  \"wasm_fib20\": $wasmFib20\n")
            append("}\n")
        }
        Files.writeString(workDir.resolve("probe.json"), summary, StandardCharsets.UTF_8)
    }

    private fun buildFibWasmAndRun(n: Int): Int {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x00, 0x61, 0x73, 0x6D, 0x01, 0x00, 0x00, 0x00))
        val secType = byteArrayOf(0x01, 0x60, 0x01, 0x7F, 0x01, 0x7F)
        out.write(0x01); out.write(secType.size); out.write(secType)
        val secFunc = byteArrayOf(0x01, 0x00)
        out.write(0x03); out.write(secFunc.size); out.write(secFunc)
        val secExp = byteArrayOf(0x01, 0x03, 'f'.code.toByte(), 'i'.code.toByte(), 'b'.code.toByte(), 0x00, 0x00)
        out.write(0x07); out.write(secExp.size); out.write(secExp)
        val body = byteArrayOf(
            0x01, 0x03, 0x7F,
            0x41, 0x00, 0x21, 0x01,
            0x41, 0x01, 0x21, 0x02,
            0x02, 0x40, 0x03, 0x40,
            0x20, 0x00, 0x41, 0x00, 0x4C, 0x0D, 0x01,
            0x20, 0x01, 0x20, 0x02, 0x6A, 0x21, 0x03,
            0x20, 0x02, 0x21, 0x01,
            0x20, 0x03, 0x21, 0x02,
            0x20, 0x00, 0x41, 0x01, 0x6B, 0x21, 0x00,
            0x0C, 0x00, 0x0B, 0x0B,
            0x20, 0x01, 0x0B,
        )
        out.write(0x0A); out.write(1 + 1 + body.size); out.write(0x01); out.write(body.size); out.write(body)
        return WasmSandbox.decode(out.toByteArray()).instantiate(1).invoke("fib", listOf(n)).returnValues.single()
    }
}
"""


def compile_probe(jars: list[Path]) -> Path:
    kotlinc, _, _, env = run_r46_contracts.resolve_toolchain()
    BRIDGE_DIR.mkdir(parents=True, exist_ok=True)
    probe_kt = BRIDGE_DIR / "R46RefProbe.kt"
    probe_jar = BRIDGE_DIR / "r46-ref-probe.jar"
    stamp = BRIDGE_DIR / "r46-ref-probe.sha256"
    if not probe_kt.is_file() or probe_kt.read_text(encoding="utf-8") != KOTLIN_PROBE_SRC:
        probe_kt.write_text(KOTLIN_PROBE_SRC, encoding="utf-8")
    sig = run_r46_contracts._sha256_paths([*jars, probe_kt])
    if probe_jar.is_file() and stamp.is_file() and stamp.read_text().strip() == sig:
        return probe_jar
    cp = os.pathsep.join(str(j) for j in jars)
    subprocess.run(
        [str(kotlinc), "-Werror", "-jvm-target", "17", "-cp", cp, str(probe_kt), "-d", str(probe_jar)],
        cwd=ROOT,
        env=env,
        check=True,
    )
    stamp.write_text(sig + "\n")
    return probe_jar


def verify_pdf_xref_independently(pdf_bytes: bytes) -> dict[str, int]:
    assert pdf_bytes.startswith(b"%PDF-1.7\n"), "missing %PDF-1.7 header"
    eof_idx = pdf_bytes.rfind(b"startxref\n")
    assert eof_idx > 0, "missing startxref"
    tail = pdf_bytes[eof_idx:].decode("ascii")
    m = re.search(r"startxref\n(\d+)\n%%EOF", tail)
    assert m is not None, f"malformed trailer tail: {tail!r}"
    xref_off = int(m.group(1))
    assert pdf_bytes[xref_off : xref_off + 5] == b"xref\n", f"xref not at declared offset {xref_off}"

    xref_block = pdf_bytes[xref_off:eof_idx].decode("ascii").splitlines()
    hdr_parts = xref_block[1].split()
    assert hdr_parts[0] == "0", f"unexpected xref start {hdr_parts}"
    total_objs = int(hdr_parts[1])
    entries = xref_block[2 : 2 + total_objs]
    assert entries[0].startswith("0000000000 65535 f"), "invalid free object 0 entry"
    for obj_id in range(1, total_objs):
        line = entries[obj_id]
        assert len(line) in (19, 20), f"ISO 32000-1 requires 20-byte xref lines, got {len(line)+1}"
        off = int(line[:10])
        expected_prefix = f"{obj_id} 0 obj\n".encode("ascii")
        assert pdf_bytes[off : off + len(expected_prefix)] == expected_prefix, (
            f"xref offset {off} for obj {obj_id} did not match {expected_prefix!r}"
        )

    pages_m = re.search(rb"/Type /Pages /Kids \[[^\]]+\] /Count (\d+)", pdf_bytes)
    assert pages_m is not None, "missing /Pages dictionary"
    page_count = int(pages_m.group(1))
    for p in range(1, page_count + 1):
        needle = f"Page {p} of {page_count}".encode("ascii")
        assert needle in pdf_bytes, f"missing footer {needle!r}"
    return {"total_objects": total_objs - 1, "pages": page_count, "xref_offset": xref_off}


def verify_png_chunks_independently(png_bytes: bytes) -> list[str]:
    assert png_bytes[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8
    types: list[str] = []
    while pos < len(png_bytes):
        length = struct.unpack(">I", png_bytes[pos : pos + 4])[0]
        ctype = png_bytes[pos + 4 : pos + 8]
        data = png_bytes[pos + 8 : pos + 8 + length]
        stored_crc = struct.unpack(">I", png_bytes[pos + 8 + length : pos + 12 + length])[0]
        calc_crc = zlib.crc32(ctype + data) & 0xFFFFFFFF
        assert stored_crc == calc_crc, f"PNG CRC mismatch on {ctype!r}"
        types.append(ctype.decode("ascii"))
        pos += 12 + length
    return types


def run_all_oracles() -> dict[str, object]:
    _, java_bin, stdlib, env = run_r46_contracts.resolve_toolchain()
    jars, _ = run_r46_contracts.compile_all(workers=2, use_cache=True)
    probe_jar = compile_probe(jars)

    checks = 0
    with tempfile.TemporaryDirectory() as td:
        tdir = Path(td)

        # Build foreign multi-sheet .xlsx using openpyxl
        wb = openpyxl.Workbook()
        ws1 = wb.active
        ws1.title = "Inputs"
        ws1["A1"] = "Hardware"
        ws1["B1"] = 320.25
        ws1["A2"] = "Licenses"
        ws1["B2"] = 179.75
        ws1["A3"] = "Support"
        ws1["B3"] = 100.00
        ws2 = wb.create_sheet("Rollup")
        ws2["B1"] = 1.10
        ws2["B2"] = "=ROUND(SUM(Inputs!B1:B3)*B1, 2)"
        ws2["B3"] = '=VLOOKUP("Licenses", Inputs!A1:B3, 2, FALSE)'
        foreign_xlsx = tdir / "openpyxl_in.xlsx"
        wb.save(foreign_xlsx)

        # Build foreign POSIX PAX .tar using Python stdlib tarfile
        foreign_tar = tdir / "python_in.tar"
        long_py_name = "archive/" + ("very_long_subdir_" * 7) + "épreuve.txt"
        with tarfile.open(foreign_tar, "w", format=tarfile.PAX_FORMAT) as tf:
            for name, payload in [("a/hello.txt", b"hello"), (long_py_name, "salut monde 🌍".encode("utf-8"))]:
                ti = tarfile.TarInfo(name)
                ti.size = len(payload)
                ti.mtime = 0
                ti.mode = 0o644
                tf.addfile(ti, io.BytesIO(payload))

        run_cp = os.pathsep.join([*(str(j) for j in jars), str(probe_jar), str(stdlib)])
        subprocess.run(
            [str(java_bin), "-cp", run_cp, "dev.fusion.testing.R46RefProbe", str(tdir), str(foreign_xlsx), str(foreign_tar)],
            cwd=ROOT,
            env=env,
            check=True,
        )

        # Verify 1A: openpyxl reads Kotlin MultiSheetBook in both formula and cached-value modes
        kt_xlsx = tdir / "kotlin_multisheet.xlsx"
        wb_f = openpyxl.load_workbook(kt_xlsx, data_only=False)
        wb_v = openpyxl.load_workbook(kt_xlsx, data_only=True)
        assert wb_f.sheetnames == ["Q1 Sales", "Rates", "Summary"]; checks += 1
        assert wb_f["Summary"]["A2"].value == "=ROUND(A1 * Rates!B1, 2)"; checks += 1
        assert math.isclose(wb_v["Q1 Sales"]["B4"].value, 2500.0, rel_tol=0, abs_tol=1e-9); checks += 1
        assert math.isclose(wb_v["Q1 Sales"]["B5"].value, 1700.5, rel_tol=0, abs_tol=1e-9); checks += 1
        assert math.isclose(wb_v["Q1 Sales"]["B6"].value, 799.5, rel_tol=0, abs_tol=1e-9); checks += 1
        assert math.isclose(wb_v["Summary"]["A1"].value, 2500.0, rel_tol=0, abs_tol=1e-9); checks += 1
        assert math.isclose(wb_v["Summary"]["A2"].value, 212.5, rel_tol=0, abs_tol=1e-9); checks += 1
        assert math.isclose(wb_v["Summary"]["A3"].value, 799.5, rel_tol=0, abs_tol=1e-9); checks += 1
        assert math.isclose(wb_v["Summary"]["A4"].value, 0.0, rel_tol=0, abs_tol=1e-9); checks += 1

        # Verify 1B: Kotlin evaluated openpyxl-authored multi-sheet workbook
        probe = json.loads((tdir / "probe.json").read_text(encoding="utf-8"))
        assert math.isclose(probe["foreign_total"], 660.0, rel_tol=0, abs_tol=1e-9); checks += 1
        assert math.isclose(probe["foreign_vlookup"], 179.75, rel_tol=0, abs_tol=1e-9); checks += 1

        # Verify 2: Independent ISO 32000-1 PDF 1.7 xref & page-stream verification
        pdf_meta = verify_pdf_xref_independently((tdir / "kotlin_out.pdf").read_bytes())
        assert pdf_meta["pages"] == probe["pdf_pages"] and pdf_meta["pages"] >= 2; checks += 3

        # Verify 3: Python tarfile opens Kotlin TarPax archive & Kotlin read Python tarfile
        with tarfile.open(tdir / "kotlin_out.tar", "r:*") as tf:
            members = tf.getmembers()
            assert len(members) == 2; checks += 1
            assert all(m.mtime == 0 and m.uid == 0 and m.gid == 0 and m.uname == "" for m in members); checks += 1
            long_m = [m for m in members if len(m.name) > 100][0]
            assert tf.extractfile(long_m).read().decode("utf-8") == "UTF-8 payload αβγ"; checks += 1
        assert probe["py_tar_count"] == 2 and probe["py_tar_long_utf8"] == "salut monde 🌍"; checks += 2

        # Verify 4: Python stdlib quopri & email.header match Kotlin QuotedPrintable
        qp_raw = (tdir / "kotlin_qp.txt").read_bytes()
        expected_qp_text = "Café = 100% pure JVM! Trailing tab\t\r\n" + ("Long UTF-8 segment éàü " * 6)
        py_decoded = quopri.decodestring(qp_raw).decode("utf-8")
        assert py_decoded == expected_qp_text; checks += 1
        py_hdr = "".join(
            frag.decode(enc or "ascii") if isinstance(frag, bytes) else frag
            for frag, enc in email.header.decode_header("=?UTF-8?B?RnVzaW9u?= =?UTF-8?Q?_R46_V=C3=A9rifi=C3=A9?=")
        )
        assert probe["rfc2047"] == py_hdr == "Fusion R46 Vérifié"; checks += 1

        # Verify 5: Independent PNG CRC-32 + chunk strip, RFC 8948 HMAC-SHA256 MAC, Pivot, & Wasm fib(20)
        png_types = verify_png_chunks_independently((tdir / "kotlin_clean.png").read_bytes())
        assert png_types == ["IHDR", "IDAT", "IEND"]; checks += 1

        hm = hmac.new(b"0123456789abcdef0123456789abcdef", digestmod=hashlib.sha256)
        hm.update(b"wlan0\x00AirportFreeWiFi\x00" + struct.pack(">q", 7))
        d = bytearray(hm.digest()[:6])
        d[0] = (d[0] & 0xFE) | 0x02
        py_mac = ":".join(f"{b:02x}" for b in d)
        assert probe["mac"] == py_mac; checks += 1
        assert math.isclose(probe["pivot_grand"], 1600.0, rel_tol=0, abs_tol=1e-9); checks += 1
        assert probe["wasm_fib20"] == 6765; checks += 1

    return {
        "oracle_checks": checks,
        "pdf_pages": pdf_meta["pages"],
        "pdf_objects": pdf_meta["total_objects"],
        "png_surviving_chunks": png_types,
    }


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--verify", action="store_true", help="Run all R46 differential reference oracles")
    ap.parse_args(argv)
    report = run_all_oracles()
    print(
        f"R46 REFERENCE ORACLE PASS: {report['oracle_checks']} cross-engine checks "
        f"(openpyxl multi-sheet .xlsx, PDF 1.7 xref/{report['pdf_pages']}p, POSIX PAX tar, RFC 2045/2047 MIME, PNG CRC-32, HMAC MAC, Wasm fib20)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
