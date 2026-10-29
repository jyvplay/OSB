#!/usr/bin/env python3
"""Compile and run R46 production-kernel contracts on the host JVM (parallel per-module jars).

Honesty boundary: host JVM bytecode execution via kotlinc 2.2.21 + OpenJDK 17 only.
Does NOT claim Android SDK, Compose UI, ART, or physical-device execution.
"""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "testing/build/r46-host"
FIXTURE_DIR = ROOT / "tools/fixtures/r46"
CHECKS_FILE = "testing/src/r46/kotlin/dev/fusion/testing/R46Checks.kt"


@dataclass(frozen=True)
class KernelSpec:
    name: str
    module: str
    path: str
    deps: tuple[str, ...] = ()


KERNELS: tuple[KernelSpec, ...] = (
    # Office / Writer / Spreadsheet / PDF / ODF (8 new + FormulaEval touched across 5 modules)
    KernelSpec(
        "MultiSheetBook",
        ":format:ooxml",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/MultiSheetBook.kt",
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt",
        ),
    ),
    KernelSpec("SheetPivot", ":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetPivot.kt"),
    KernelSpec("CondFormat", ":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CondFormat.kt"),
    KernelSpec("PdfPageWriter", ":format:pdf", "format/pdf/src/main/java/dev/fusion/format/pdf/PdfPageWriter.kt"),
    KernelSpec("DocOutlineRender", ":core:documents", "core/documents/src/main/kotlin/dev/fusion/documents/DocOutlineRender.kt"),
    KernelSpec("TrackRevisions", ":core:documents", "core/documents/src/main/kotlin/dev/fusion/documents/TrackRevisions.kt"),
    KernelSpec("CitationBib", ":feature:editor", "feature/editor/src/main/java/dev/fusion/editor/CitationBib.kt"),
    KernelSpec("OdsSheetExtract", ":format:lok", "format/lok/src/main/java/dev/fusion/lok/OdsSheetExtract.kt"),
    # Sandbox / Core / Binary / Archive (8 new across 8 modules)
    KernelSpec("WasmSandbox", ":core:capabilities", "core/capabilities/src/main/java/dev/fusion/capabilities/WasmSandbox.kt"),
    KernelSpec("QuotaBudget", ":core:perf", "core/perf/src/main/kotlin/dev/fusion/perf/QuotaBudget.kt"),
    KernelSpec("WalFrame", ":core:storage", "core/storage/src/main/kotlin/dev/fusion/storage/WalFrame.kt"),
    KernelSpec("PipeChain", ":core:commands", "core/commands/src/main/kotlin/dev/fusion/commands/PipeChain.kt"),
    KernelSpec("SemVerRange", ":core:model", "core/model/src/main/kotlin/dev/fusion/model/SemVerRange.kt"),
    KernelSpec("SchemaMigrate", ":core:settings", "core/settings/src/main/kotlin/dev/fusion/settings/SchemaMigrate.kt"),
    KernelSpec("ColumnarPage", ":format:native", "format/native/src/main/kotlin/dev/fusion/native/ColumnarPage.kt"),
    KernelSpec("TarPax", ":format:archive", "format/archive/src/main/kotlin/dev/fusion/archive/TarPax.kt"),
    # Tails-Mod Privacy & Anti-Keylogger / Hardware Defense (6 new across 4 modules)
    KernelSpec("OnionGraterFilter", ":privacy", "privacy/src/main/java/dev/fusion/privacy/OnionGraterFilter.kt"),
    KernelSpec("MetadataScrub", ":privacy", "privacy/src/main/java/dev/fusion/privacy/MetadataScrub.kt"),
    KernelSpec("UsbGuardPolicy", ":core:hw", "core/hw/src/main/kotlin/dev/fusion/hw/UsbGuardPolicy.kt"),
    KernelSpec("BleHidPairingGate", ":core:hw", "core/hw/src/main/kotlin/dev/fusion/hw/BleHidPairingGate.kt"),
    KernelSpec("MacRandomizer", ":core:security", "core/security/src/main/kotlin/dev/fusion/security/MacRandomizer.kt"),
    KernelSpec("KeystrokeJitter", ":app", "app/src/main/java/dev/fusion/shell/KeystrokeJitter.kt"),
    # Browser, Media & 15 Remaining Product Modules (17 new across 17 modules)
    KernelSpec("PermissionsPolicyHeader", ":feature:browser", "feature/browser/src/main/kotlin/dev/fusion/browser/model/PermissionsPolicyHeader.kt"),
    KernelSpec("HlsPlaylist", ":feature:media", "feature/media/src/main/kotlin/dev/fusion/media/HlsPlaylist.kt"),
    KernelSpec("DesktopEntryIni", ":feature:launcher", "feature/launcher/src/main/kotlin/dev/fusion/launcher/DesktopEntryIni.kt"),
    KernelSpec("TrigramIndex", ":feature:find", "feature/find/src/main/kotlin/dev/fusion/find/TrigramIndex.kt"),
    KernelSpec("PathConflict", ":feature:files", "feature/files/src/main/kotlin/dev/fusion/filesui/PathConflict.kt"),
    KernelSpec("VtScreenBuf", ":feature:terminal", "feature/terminal/src/main/kotlin/dev/fusion/terminal/VtScreenBuf.kt"),
    KernelSpec("ChunkedTransfer", ":feature:beam", "feature/beam/src/main/kotlin/dev/fusion/beam/ChunkedTransfer.kt"),
    KernelSpec("RateWindow", ":feature:alerts", "feature/alerts/src/main/kotlin/dev/fusion/alerts/RateWindow.kt"),
    KernelSpec("EdlCutList", ":feature:capture", "feature/capture/src/main/kotlin/dev/fusion/capture/EdlCutList.kt"),
    KernelSpec("RabinChunker", ":feature:backup", "feature/backup/src/main/kotlin/dev/fusion/backup/RabinChunker.kt"),
    KernelSpec("CrdtLwwMap", ":feature:together", "feature/together/src/main/kotlin/dev/fusion/together/CrdtLwwMap.kt"),
    KernelSpec("BarcodeGs1", ":feature:lens", "feature/lens/src/main/kotlin/dev/fusion/lens/BarcodeGs1.kt"),
    KernelSpec("QuotedPrintable", ":feature:messages", "feature/messages/src/main/kotlin/dev/fusion/messages/QuotedPrintable.kt"),
    KernelSpec("PngMetadataStrip", ":format:image", "format/image/src/main/kotlin/dev/fusion/format/image/PngMetadataStrip.kt"),
    KernelSpec("CronExpr", ":feature:automation", "feature/automation/src/main/kotlin/dev/fusion/automation/CronExpr.kt"),
    KernelSpec("PassphraseDice", ":feature:vault", "feature/vault/src/main/kotlin/dev/fusion/vault/PassphraseDice.kt"),
    KernelSpec("MarkdownTableFmt", ":feature:notes", "feature/notes/src/main/kotlin/dev/fusion/notes/MarkdownTableFmt.kt"),
)

TOUCHED_KERNELS: tuple[KernelSpec, ...] = (
    KernelSpec("FormulaEval", ":format:ooxml", "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt"),
    KernelSpec("TabletopDesktop", ":app", "app/src/main/java/dev/fusion/shell/TabletopDesktop.kt"),
    KernelSpec("DesktopChrome", ":app", "app/src/main/java/dev/fusion/shell/DesktopChrome.kt"),
)


def resolve_toolchain() -> tuple[Path, Path, Path, dict[str, str]]:
    cache_root = Path(os.environ.get("FUSION_HOST_CACHE", str(Path.home() / ".cache/fusion-host-r23")))
    kotlinc = cache_root / "node_modules/kotlin-compiler/bin/kotlinc"
    stdlib = cache_root / "node_modules/kotlin-compiler/lib/kotlin-stdlib.jar"
    java_bin = cache_root / "python/jdk4py/java-runtime/bin/java"
    if not (kotlinc.is_file() and stdlib.is_file() and java_bin.is_file()):
        raise SystemExit(
            f"Missing host toolchain under {cache_root}; run `python3 tools/bootstrap_host_tools.py install` first."
        )
    env = dict(os.environ)
    java_home = str(java_bin.parent.parent)
    env["JAVA_HOME"] = java_home
    env["PATH"] = f"{kotlinc.parent}:{java_bin.parent}:{env.get('PATH', '')}"
    return kotlinc, java_bin, stdlib, env


def module_groups() -> dict[str, list[str]]:
    groups: dict[str, list[str]] = {}
    for k in KERNELS:
        bucket = groups.setdefault(k.module, [])
        for dep in k.deps:
            if dep not in bucket:
                bucket.append(dep)
        if k.path not in bucket:
            bucket.append(k.path)
    return groups


def _sha256_paths(paths: list[Path]) -> str:
    h = hashlib.sha256()
    for p in paths:
        h.update(str(p).encode("utf-8"))
        h.update(b"\0")
        h.update(p.read_bytes())
        h.update(b"\0")
    return h.hexdigest()


def compile_all(workers: int = 2, use_cache: bool = True) -> tuple[list[Path], Path]:
    kotlinc, _, _, env = resolve_toolchain()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    groups = module_groups()

    def build_group(item: tuple[str, list[str]]) -> Path:
        module, rel_files = item
        slug = module.lstrip(":").replace(":", "_")
        jar = OUT_DIR / f"r46-{slug}.jar"
        stamp = OUT_DIR / f"r46-{slug}.sha256"
        src_paths = [ROOT / f for f in rel_files]
        sig = _sha256_paths(src_paths)
        if use_cache and jar.is_file() and stamp.is_file() and stamp.read_text().strip() == sig:
            return jar
        cmd = [str(kotlinc), "-Werror", "-jvm-target", "17", *[str(p) for p in src_paths], "-d", str(jar)]
        subprocess.run(cmd, cwd=ROOT, env=env, check=True)
        stamp.write_text(sig + "\n")
        return jar

    max_w = max(1, min(workers, os.cpu_count() or 2))
    with ThreadPoolExecutor(max_workers=max_w) as pool:
        jars = list(pool.map(build_group, groups.items()))

    checks_jar = OUT_DIR / "r46-checks.jar"
    checks_stamp = OUT_DIR / "r46-checks.sha256"
    checks_sig = _sha256_paths([*jars, ROOT / CHECKS_FILE])
    if not (use_cache and checks_jar.is_file() and checks_stamp.is_file() and checks_stamp.read_text().strip() == checks_sig):
        cp = os.pathsep.join(str(j) for j in jars)
        subprocess.run(
            [str(kotlinc), "-Werror", "-jvm-target", "17", "-cp", cp, str(ROOT / CHECKS_FILE), "-d", str(checks_jar)],
            cwd=ROOT,
            env=env,
            check=True,
        )
        checks_stamp.write_text(checks_sig + "\n")
    return jars, checks_jar


def main() -> int:
    _, java_bin, stdlib, env = resolve_toolchain()
    jars, checks_jar = compile_all(workers=2)
    run_cp = os.pathsep.join([*(str(j) for j in jars), str(checks_jar), str(stdlib)])
    proc = subprocess.run(
        [str(java_bin), "-cp", run_cp, "dev.fusion.testing.R46Checks", str(FIXTURE_DIR)],
        cwd=ROOT,
        env=env,
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        sys.stderr.write(proc.stderr)
        raise SystemExit(proc.returncode)
    out = proc.stdout.strip()
    if not out.startswith("R46 PASS:"):
        raise SystemExit(f"unexpected R46Checks output: {out!r}")
    count = int(out.split()[2])
    if count < 650:
        raise SystemExit(f"expected >= 650 assertions, got {count}")
    print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
