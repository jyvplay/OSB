#!/usr/bin/env python3
"""Compile and run R45 production-kernel contracts on the host JVM (parallel per-module jars).

Honesty boundary: host JVM bytecode execution via kotlinc 2.2.21 + OpenJDK 17 only.
Does NOT claim Android SDK, Compose UI, ART, or physical-device execution.
"""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "testing/build/r45-host"
FIXTURE_DIR = ROOT / "tools/fixtures/r45"


@dataclass(frozen=True)
class KernelSpec:
    name: str
    module: str
    file: str
    support: tuple[str, ...] = ()


TOUCHED_FILES: tuple[str, ...] = (
    "core/documents/src/main/kotlin/dev/fusion/documents/DashEducate.kt",
)

SPECS: tuple[KernelSpec, ...] = (
    KernelSpec(
        "FormulaEval", ":format:ooxml",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt",
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt",
        ),
    ),
    KernelSpec(
        "FormulaXlsx", ":format:ooxml",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaXlsx.kt",
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/ColLetters.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/SheetRange.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/CellErr.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/FormulaEval.kt",
        ),
    ),
    KernelSpec(
        "DocxNumbering", ":format:ooxml",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxNumbering.kt",
        ("format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",),
    ),
    KernelSpec(
        "DocxPackage", ":format:ooxml",
        "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxPackage.kt",
        (
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/BoundedXml.kt",
            "format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxNumbering.kt",
        ),
    ),
    KernelSpec(
        "PieceTable", ":core:documents",
        "core/documents/src/main/kotlin/dev/fusion/documents/PieceTable.kt",
    ),
    KernelSpec(
        "ProseEducate", ":core:documents",
        "core/documents/src/main/kotlin/dev/fusion/documents/ProseEducate.kt",
        (
            "core/documents/src/main/kotlin/dev/fusion/documents/DashEducate.kt",
            "core/documents/src/main/kotlin/dev/fusion/documents/SmartQuote.kt",
        ),
    ),
    KernelSpec(
        "FindReplace", ":feature:editor",
        "feature/editor/src/main/java/dev/fusion/editor/FindReplace.kt",
    ),
    KernelSpec(
        "OdtExtract", ":format:lok",
        "format/lok/src/main/java/dev/fusion/lok/OdtExtract.kt",
    ),
    KernelSpec(
        "RleBitset", ":format:native",
        "format/native/src/main/kotlin/dev/fusion/native/RleBitset.kt",
    ),
    KernelSpec(
        "FinMath", ":feature:calc",
        "feature/calc/src/main/kotlin/dev/fusion/calc/FinMath.kt",
    ),
    KernelSpec(
        "TocTree", ":feature:notes",
        "feature/notes/src/main/kotlin/dev/fusion/notes/TocTree.kt",
    ),
    KernelSpec(
        "CspHeader", ":feature:browser",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/CspHeader.kt",
    ),
    KernelSpec(
        "PslGate", ":feature:browser",
        "feature/browser/src/main/kotlin/dev/fusion/browser/model/PslGate.kt",
    ),
    KernelSpec(
        "CtSctPolicy", ":core:security",
        "core/security/src/main/kotlin/dev/fusion/security/CtSctPolicy.kt",
    ),
    KernelSpec(
        "DmaGuardAudit", ":core:security",
        "core/security/src/main/kotlin/dev/fusion/security/DmaGuardAudit.kt",
    ),
    KernelSpec(
        "HidReportAudit", ":core:hw",
        "core/hw/src/main/kotlin/dev/fusion/hw/HidReportAudit.kt",
    ),
    KernelSpec(
        "TailsCadence", ":privacy",
        "privacy/src/main/java/dev/fusion/privacy/TailsCadence.kt",
    ),
    KernelSpec(
        "OverlayTouchGuard", ":privacy",
        "privacy/src/main/java/dev/fusion/privacy/OverlayTouchGuard.kt",
    ),
    KernelSpec(
        "TileLabelFit", ":app",
        "app/src/main/java/dev/fusion/shell/TileLabelFit.kt",
    ),
    KernelSpec(
        "SpringStep", ":core:designsystem",
        "core/designsystem/src/main/kotlin/dev/fusion/designsystem/SpringStep.kt",
    ),
    KernelSpec(
        "WorkAreaFit", ":core:windowing",
        "core/windowing/src/main/kotlin/dev/fusion/windowing/WorkAreaFit.kt",
    ),
    KernelSpec(
        "MerkleTree", ":core:files",
        "core/files/src/main/kotlin/dev/fusion/files/MerkleTree.kt",
    ),
    KernelSpec(
        "EnvOverride", ":feature:settings",
        "feature/settings/src/main/kotlin/dev/fusion/settingsui/EnvOverride.kt",
    ),
    KernelSpec(
        "PsdLayerRecords", ":format:psd",
        "format/psd/src/main/kotlin/dev/fusion/psd/PsdLayerRecords.kt",
    ),
    KernelSpec(
        "SsdpHeader", ":cast:dlna",
        "cast/dlna/src/main/kotlin/dev/fusion/cast/dlna/SsdpHeader.kt",
    ),
    KernelSpec(
        "CastTxtProps", ":cast:castv2",
        "cast/castv2/src/main/kotlin/dev/fusion/castv2/CastTxtProps.kt",
    ),
    KernelSpec(
        "TxtRecord", ":cast:airplay",
        "cast/airplay/src/main/kotlin/dev/fusion/airplay/TxtRecord.kt",
    ),
    KernelSpec(
        "KeyChord", ":feature:accessibility",
        "feature/accessibility/src/main/kotlin/dev/fusion/a11y/KeyChord.kt",
    ),
    KernelSpec(
        "UtmGrid", ":feature:maps",
        "feature/maps/src/main/kotlin/dev/fusion/maps/UtmGrid.kt",
    ),
    KernelSpec(
        "HkdfExpand", ":feature:vault",
        "feature/vault/src/main/kotlin/dev/fusion/vault/HkdfExpand.kt",
    ),
    KernelSpec(
        "ClipMime", ":feature:continuity",
        "feature/continuity/src/main/kotlin/dev/fusion/continuity/ClipMime.kt",
    ),
    KernelSpec(
        "DagTopo", ":feature:automation",
        "feature/automation/src/main/kotlin/dev/fusion/automation/DagTopo.kt",
    ),
    KernelSpec(
        "Bm25Rank", ":core:index",
        "core/index/src/main/kotlin/dev/fusion/index/Bm25Rank.kt",
    ),
)

CHECKS_FILE = "testing/src/r45/kotlin/dev/fusion/testing/R45Checks.kt"


def resolve_toolchain() -> tuple[Path, Path, Path, dict[str, str]]:
    cache_root = Path(os.environ.get("FUSION_HOST_CACHE", str(Path.home() / ".cache/fusion-host-r23")))
    kotlinc_env = os.environ.get("KOTLINC")
    kotlinc = Path(kotlinc_env) if kotlinc_env else cache_root / "node_modules/kotlin-compiler/bin/kotlinc"
    if not kotlinc.is_file():
        found_k = sorted(cache_root.rglob("bin/kotlinc")) if cache_root.is_dir() else []
        if found_k:
            kotlinc = found_k[0]
        elif shutil.which("kotlinc"):
            kotlinc = Path(shutil.which("kotlinc") or "")
    if not kotlinc.is_file():
        raise SystemExit("kotlinc not found; run `python3 tools/bootstrap_host_tools.py install` first")

    java_home_env = os.environ.get("JAVA_HOME")
    if java_home_env and (Path(java_home_env) / "bin/java").is_file():
        java_bin = Path(java_home_env) / "bin/java"
    else:
        found_j = sorted(cache_root.rglob("bin/java")) if cache_root.is_dir() else []
        if found_j:
            java_bin = found_j[0]
        elif shutil.which("java"):
            java_bin = Path(shutil.which("java") or "")
        else:
            raise SystemExit("java not found; run `python3 tools/bootstrap_host_tools.py install` first")

    java_home = java_bin.parent.parent
    stdlib = kotlinc.parent.parent / "lib/kotlin-stdlib.jar"
    env = dict(os.environ)
    env["JAVA_HOME"] = str(java_home)
    env["PATH"] = f"{java_bin.parent}:{env.get('PATH', '')}"
    return kotlinc, java_bin, stdlib, env


def module_groups() -> dict[str, list[str]]:
    grouped: dict[str, list[str]] = {}
    for spec in SPECS:
        bucket = grouped.setdefault(spec.module, [])
        for s in (*spec.support, spec.file):
            if s not in bucket:
                bucket.append(s)
    return grouped


def _sha256_paths(paths: list[Path]) -> str:
    import hashlib
    h = hashlib.sha256()
    for p in paths:
        h.update(p.as_posix().encode("utf-8"))
        h.update(b"\x00")
        h.update(p.read_bytes())
        h.update(b"\x00")
    return h.hexdigest()


def compile_all(workers: int = 2, use_cache: bool = True) -> tuple[list[Path], Path]:
    kotlinc, _, _, env = resolve_toolchain()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    groups = module_groups()

    def build_group(item: tuple[str, list[str]]) -> Path:
        module, rel_files = item
        slug = module.lstrip(":").replace(":", "_")
        jar = OUT_DIR / f"r45-{slug}.jar"
        stamp = OUT_DIR / f"r45-{slug}.sha256"
        src_paths = [ROOT / f for f in rel_files]
        sig = _sha256_paths(src_paths)
        if use_cache and jar.is_file() and stamp.is_file() and stamp.read_text().strip() == sig:
            return jar
        cmd = [str(kotlinc), "-Werror", *[str(p) for p in src_paths], "-d", str(jar)]
        subprocess.run(cmd, cwd=ROOT, env=env, check=True)
        stamp.write_text(sig + "\n")
        return jar

    max_w = max(1, min(workers, os.cpu_count() or 2))
    with ThreadPoolExecutor(max_workers=max_w) as pool:
        jars = list(pool.map(build_group, groups.items()))

    checks_jar = OUT_DIR / "r45-checks.jar"
    checks_stamp = OUT_DIR / "r45-checks.sha256"
    checks_sig = _sha256_paths([*jars, ROOT / CHECKS_FILE])
    if not (use_cache and checks_jar.is_file() and checks_stamp.is_file() and checks_stamp.read_text().strip() == checks_sig):
        cp = os.pathsep.join(str(j) for j in jars)
        subprocess.run(
            [str(kotlinc), "-Werror", "-cp", cp, str(ROOT / CHECKS_FILE), "-d", str(checks_jar)],
            cwd=ROOT, env=env, check=True,
        )
        checks_stamp.write_text(checks_sig + "\n")
    return jars, checks_jar


def main() -> int:
    _, java_bin, stdlib, env = resolve_toolchain()
    jars, checks_jar = compile_all(workers=2)
    run_cp = os.pathsep.join([*(str(j) for j in jars), str(checks_jar), str(stdlib)])
    proc = subprocess.run(
        [str(java_bin), "-cp", run_cp, "dev.fusion.testing.R45Checks", str(FIXTURE_DIR)],
        cwd=ROOT, env=env, check=True, capture_output=True, text=True,
    )
    out = proc.stdout.strip()
    if not out.startswith("R45 PASS:"):
        raise SystemExit(f"unexpected R45Checks output: {out!r}")
    count = int(out.split()[2])
    if count < 500:
        raise SystemExit(f"expected >= 500 assertions, got {count}")
    print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
