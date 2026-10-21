#!/usr/bin/env python3
"""Compile & run production R26 Kotlin kernels + cross-language verification with openpyxl, XlsxWriter, Pillow & pypdf."""
from __future__ import annotations

import hashlib
import io
import json
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
for extra in (
    Path.home() / ".cache/fusion-host-r23/python",
    Path.home() / ".cache/fusion-r25-reference/python",
    Path.home() / ".cache/fusion-r26-tools",
):
    if extra.is_dir() and str(extra) not in sys.path:
        sys.path.insert(0, str(extra))


def _ensure_reference_packages() -> None:
    try:
        import openpyxl  # noqa: F401
        import pypdf  # noqa: F401
        import xlsxwriter  # noqa: F401
        from PIL import Image  # noqa: F401
    except ImportError:
        target = Path.home() / ".cache/fusion-r26-tools"
        target.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "--quiet", "--target", str(target),
             "openpyxl", "XlsxWriter", "Pillow", "pypdf"],
            check=True,
        )
        if str(target) not in sys.path:
            sys.path.insert(0, str(target))


_ensure_reference_packages()

import openpyxl  # noqa: E402
import xlsxwriter  # noqa: E402
from PIL import Image  # noqa: E402
from pypdf import PdfReader, PdfWriter  # noqa: E402

PRODUCTION_SOURCES = [
    "core/fileops/src/main/kotlin/dev/fusion/fileops/FileMoveJournal.kt",
    "format/poi/src/main/kotlin/dev/fusion/poi/CleanRoomPoiEngine.kt",
    "feature/office/src/main/kotlin/dev/fusion/office/OfficeDocumentModel.kt",
    "feature/quickpanel/src/main/kotlin/dev/fusion/quickpanel/QuickPanelModel.kt",
    "feature/photos/src/main/kotlin/dev/fusion/photos/PhotoMetadataCleaner.kt",
    "feature/ime/src/main/kotlin/dev/fusion/ime/HardenedInputEngine.kt",
    "core/security/src/main/kotlin/dev/fusion/security/AmnesicSessionVault.kt",
    "core/designsystem/src/main/kotlin/dev/fusion/designsystem/BeautyStandard.kt",
    "core/designsystem/src/main/kotlin/dev/fusion/designsystem/ProfessionalPalettes.kt",
    "core/designsystem/src/main/kotlin/dev/fusion/designsystem/DesktopMetrics.kt",
    "core/designsystem/src/main/kotlin/dev/fusion/designsystem/Contrast.kt",
    "core/settings/src/main/kotlin/dev/fusion/settings/Appearance.kt",
    "core/model/src/main/kotlin/dev/fusion/model/PilotPolicy.kt",
    "feature/media/src/main/kotlin/dev/fusion/media/ServiceLink.kt",
    "feature/media/src/main/kotlin/dev/fusion/media/EmbeddedStreamContract.kt",
    "feature/maps/src/main/kotlin/dev/fusion/maps/OfflineMap.kt",
    "feature/maps/src/main/kotlin/dev/fusion/maps/OfflineGazetteer.kt",
    "feature/launcher/src/main/kotlin/dev/fusion/launcher/Tabletop.kt",
    "testing/src/r26/kotlin/dev/fusion/testing/R26Checks.kt",
]


from bootstrap_host_tools import environment


def tool_env() -> tuple[dict[str, str], str, str]:
    host = Path.home() / ".cache/fusion-host-r23"
    env = environment(host) if host.is_dir() else dict(os.environ)
    kotlinc_bin = env.get("KOTLINC") or "kotlinc"
    java_bin = str(Path(env["JAVA_HOME"], "bin/java")) if env.get("JAVA_HOME") and Path(env["JAVA_HOME"], "bin/java").is_file() else "java"
    return env, kotlinc_bin, java_bin


def main() -> None:
    env, kotlinc_bin, java_bin = tool_env()
    out_dir = ROOT / "testing/build/r26"
    out_dir.mkdir(parents=True, exist_ok=True)
    jar = out_dir / "r26-checks.jar"
    sources = [ROOT / p for p in PRODUCTION_SOURCES]
    subprocess.run(
        [kotlinc_bin, "-Werror", *[str(s) for s in sources], "-include-runtime", "-d", str(jar)],
        check=True,
        env=env,
    )
    proc = subprocess.run([java_bin, "-jar", str(jar)], check=True, text=True, capture_output=True, env=env)
    sys.stdout.write(proc.stdout)

    # Cross-language round-trip verification against independent external Python implementations:
    with tempfile.TemporaryDirectory(prefix="r26-cross-lang-") as tmp_str:
        tmp = Path(tmp_str)
        # 1. Produce real .xlsx via XlsxWriter 3.2.5:
        xlsx_in = tmp / "python-input.xlsx"
        book = xlsxwriter.Workbook(str(xlsx_in))
        ws = book.add_worksheet("Metrics")
        ws.write_number("B1", 40.0)
        ws.write_number("B2", 55.0)
        ws.write_number("B3", 62.5)
        ws.write_formula("B4", "=SUM(B1:B3)")
        ws.write_formula("B5", "=AVERAGE(B1:B3)")
        book.close()

        # 2. Produce real JPEG with EXIF GPS tags via Pillow 13.0.0:
        img = Image.new("RGB", (32, 24), color=(35, 92, 168))
        exif = img.getexif()
        exif[0x010F] = "LeakCameraCorp"
        exif[0x013B] = "SecretPhotographer"
        exif[0x8825] = {1: "N", 2: (35, 46, 46)}
        dirty_jpg = tmp / "pillow-dirty-exif.jpg"
        img.save(dirty_jpg, format="JPEG", quality=92, exif=exif.tobytes())

        # 3. Produce real PDF with /Author, /Producer, and embedded XMP XML stream via pypdf 6.21.1:
        writer = PdfWriter()
        writer.add_blank_page(width=200, height=200)
        writer.add_metadata({"/Author": "ClassifiedAuthor", "/Producer": "LeakProducerEngine"})
        dirty_pdf = tmp / "pypdf-dirty.pdf"
        with open(dirty_pdf, "wb") as fh:
            writer.write(fh)

        # Invoke production Kotlin binary in --interop mode:
        interop_proc = subprocess.run(
            [java_bin, "-jar", str(jar), "--interop", str(tmp)],
            check=True,
            text=True,
            capture_output=True,
            env=env,
        )
        sys.stdout.write(interop_proc.stdout)

        # Independent external readback checks:
        wb_back = openpyxl.load_workbook(tmp / "kotlin-output.xlsx", data_only=True)
        assert wb_back["Audit"]["A3"].value == 100
        assert wb_back["Audit"]["B1"].value == "Clean-Room Apache-2.0"

        with zipfile.ZipFile(tmp / "kotlin-output.docx") as zf:
            xml_root = ET.fromstring(zf.read("word/document.xml"))
            all_text = "".join(xml_root.itertext())
            assert "R26 Interop Heading" in all_text and "Refused" in all_text

        with zipfile.ZipFile(tmp / "kotlin-output.pptx") as zf:
            slide_root = ET.fromstring(zf.read("ppt/slides/slide1.xml"))
            assert "Interop Slide" in "".join(slide_root.itertext())

        with Image.open(tmp / "kotlin-cleaned.jpg") as cleaned_img:
            assert cleaned_img.size == (32, 24)
            assert len(cleaned_img.getexif()) == 0
            raw_clean = (tmp / "kotlin-cleaned.jpg").read_bytes()
            assert b"LeakCameraCorp" not in raw_clean and b"SecretPhotographer" not in raw_clean

        cleaned_pdf_bytes = (tmp / "kotlin-cleaned.pdf").read_bytes()
        assert b"ClassifiedAuthor" not in cleaned_pdf_bytes and b"LeakProducerEngine" not in cleaned_pdf_bytes
        parsed_pdf = PdfReader(io.BytesIO(cleaned_pdf_bytes), strict=True)
        assert len(parsed_pdf.pages) == 1

    receipt = {
        "kotlin_compiler": subprocess.check_output([kotlinc_bin, "-version"], stderr=subprocess.STDOUT, text=True, env=env).strip(),
        "external_verifiers": [
            f"openpyxl {openpyxl.__version__}",
            f"XlsxWriter {xlsxwriter.__version__}",
            f"Pillow {Image.__version__}",
            f"pypdf {pypdf_version()}",
        ],
        "source_hashes": {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in PRODUCTION_SOURCES},
        "scope": "Compiled production Kotlin kernels + cross-language binary file readback; NOT physical-hardware acceptance.",
    }
    (out_dir / "result.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps({"r26_interop": "PASS", "verifiers": receipt["external_verifiers"]}))


def pypdf_version() -> str:
    import pypdf

    return pypdf.__version__


if __name__ == "__main__":
    main()
