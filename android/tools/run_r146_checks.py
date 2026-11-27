#!/usr/bin/env python3
"""R146 differential runner: JVM probe vs independent Python oracle.

Protocol (locked for R146):
  * compile all 21 R146 production files + the probe with kotlinc -Werror;
  * build the corpus from r146_corpus (314 cases, 22 lanes);
  * execute every case on a real JVM (java 17);
  * diff each verdict against r146_oracle (independent implementation);
  * run negative controls so a silent skip cannot read green:
      W1 wiring     - corpus must cover all 22 lanes and >=220 cases;
      W2 tamper     - weakening a bound in a COPY must produce a mismatch;
      W3 defect     - the pre-fix MessageThread from the pinned baseline must mismatch
                      on forward-reference / cycle cases (reproduced defect);
      W4 binding    - every production source exists and declares its scope.
Exit code 0 only if the differential is clean AND every control fires.
"""
from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ANDROID = HERE.parent
REPO = ANDROID.parent
sys.path.insert(0, str(HERE))

from bootstrap_host_tools import environment  # noqa: E402
from r146_corpus import build_corpus  # noqa: E402
from r146_oracle import FEATURES, Refusal, oracle  # noqa: E402

PROBE_SOURCES = [
    "feature/alerts/src/main/kotlin/dev/fusion/alerts/InexactAlarmPlanner.kt",
    "feature/accessibility/src/main/kotlin/dev/fusion/a11y/KeyToTouchMapper.kt",
    "format/pdf/src/main/java/dev/fusion/format/pdf/PdfAnnotationPlan.kt",
    "format/archive/src/main/kotlin/dev/fusion/archive/CpioNewcParser.kt",
    "format/psd/src/main/kotlin/dev/fusion/psd/PsdThumbnailInspector.kt",
    "format/lok/src/main/kotlin/dev/fusion/lok/OdfMetadataScrubber.kt",
    "feature/maps/src/main/kotlin/dev/fusion/maps/GeohashCodec.kt",
    "feature/find/src/main/kotlin/dev/fusion/find/QuerySynonymRingExpander.kt",
    "feature/terminal/src/main/kotlin/dev/fusion/terminal/TerminalUrlDetector.kt",
    "feature/notes/src/main/kotlin/dev/fusion/notes/NoteTagHierarchy.kt",
    "feature/together/src/main/kotlin/dev/fusion/together/EditLogCoalescer.kt",
    "privacy/src/main/java/dev/fusion/privacy/DiagnosticExportAllowlist.kt",
    "feature/automation/src/main/kotlin/dev/fusion/automation/MacroExchangeCodec.kt",
    "cast/airplay/src/main/kotlin/dev/fusion/airplay/NtpTimestampCodec.kt",
    "core/model/src/main/kotlin/dev/fusion/model/DurationPhraseParser.kt",
    "feature/media/src/main/kotlin/dev/fusion/media/QueueShufflePlanner.kt",
    "feature/browser/src/main/kotlin/dev/fusion/browser/model/SubresourceIntegrityParser.kt",
    "core/hw/src/main/kotlin/dev/fusion/hw/OemThrottleDetector.kt",
    "feature/vault/src/main/kotlin/dev/fusion/vault/PasswordHistoryHashChain.kt",
    "feature/capture/src/main/kotlin/dev/fusion/capture/DuplicateFrameDetector.kt",
    "testing/src/r146/kotlin/dev/fusion/testing/R146Checks.kt",
]
MESSAGE_THREAD = "feature/messages/src/main/kotlin/dev/fusion/messages/MessageThread.kt"
GEOHASH = "feature/maps/src/main/kotlin/dev/fusion/maps/GeohashCodec.kt"
CACHE = Path.home() / ".cache" / "fusion-host-r23" / "r146"


def run(cmd, **kwargs):
    return subprocess.run(cmd, capture_output=True, text=True, **kwargs)


TOOL_ENV = None  # set in main() from bootstrap_host_tools.environment()


def compile_probe(kotlinc: str, sources: list[Path], out_dir: Path) -> None:
    if out_dir.exists():
        shutil.rmtree(out_dir)
    out_dir.mkdir(parents=True)
    cmd = [kotlinc, "-jvm-target", "17", "-Werror", "-d", str(out_dir)]
    cmd += [str(s) for s in sources]
    proc = run(cmd, cwd=str(ANDROID), env=TOOL_ENV)
    if proc.returncode != 0:
        raise SystemExit("kotlinc failed:\n" + proc.stdout + proc.stderr)


def run_probe(java: str, classes: Path, cases_path: Path, out_path: Path) -> str:
    if out_path.exists():
        out_path.unlink()
    stdlib = Path(TOOL_ENV["KOTLINC"]).parent.parent / "lib" / "kotlin-stdlib.jar"
    cp = str(classes) + os.pathsep + str(stdlib)
    proc = run([java, "-cp", cp, "dev.fusion.testing.R146Checks",
                str(cases_path), str(out_path)], env=TOOL_ENV)
    if proc.returncode != 0 or not out_path.exists():
        raise SystemExit("probe execution failed:\n" + proc.stdout + proc.stderr)
    return out_path.read_text(encoding="utf-8")


def write_cases(cases) -> tuple[Path, dict[str, str]]:
    cases_path = CACHE / "cases.tsv"
    expected: dict[str, str] = {}
    lines = []
    for i, (feature, payload, _note) in enumerate(cases):
        case_id = "c%04d" % i
        lines.append("%s\t%s\t%s" % (case_id, feature, payload))
        try:
            expected[case_id] = oracle(feature, payload)
        except Refusal:
            expected[case_id] = "!REF"
    cases_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return cases_path, expected


def diff_results(text: str, expected: dict[str, str], cases) -> list[str]:
    note_by_id = {"c%04d" % i: (f, n) for i, (f, _p, n) in enumerate(cases)}
    got: dict[str, str] = {}
    for line in text.splitlines():
        if not line:
            continue
        parts = line.split("\t")
        if len(parts) != 3:
            return ["malformed result line: %r" % line[:120]]
        case_id, feature, enc_verdict = parts
        if case_id in got:
            return ["duplicate result: " + case_id]
        if case_id in note_by_id and feature != note_by_id[case_id][0]:
            return ["wrong lane for result: " + case_id]
        verdict = _dec_verdict(enc_verdict)
        got[case_id] = verdict
    problems = []
    for case_id, want in expected.items():
        have = got.get(case_id)
        if have != want:
            feature, note = note_by_id.get(case_id, ("?", "?"))
            problems.append(
                "%s[%s] %s: oracle=%r probe=%r"
                % (case_id, feature, note,
                   want[:160], (have or "<missing>")[:160]))
    for case_id in got:
        if case_id not in expected:
            problems.append("%s: unexpected result" % case_id)
    return problems


def _dec_verdict(s: str) -> str:
    """Decode the probe's transport encoding (mirror of R146Checks.dec)."""
    if s == "!REF":
        return s
    out = []
    i = 0
    while i < len(s):
        if s[i] == "%":
            if i + 2 >= len(s):
                return "<bad-escape>"
            try:
                out.append(chr(int(s[i + 1:i + 3], 16)))
            except ValueError:
                return "<bad-escape>"
            i += 3
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def control_wiring(cases) -> list[str]:
    problems = []
    present = {f for f, _p, _n in cases}
    if present != set(FEATURES):
        problems.append("corpus lanes %s != oracle lanes %s"
                        % (sorted(present), sorted(FEATURES)))
    if len(cases) < 220:
        problems.append("only %d cases (need >=220)" % len(cases))
    if len({n for _f, _p, n in cases}) < 200:
        problems.append("corpus notes not unique enough")
    return problems


def control_binding() -> list[str]:
    problems = []
    for rel in PROBE_SOURCES + [MESSAGE_THREAD]:
        path = ANDROID / rel
        if not path.is_file():
            problems.append("missing source %s" % rel)
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if "R146" not in text and "MessageThread" not in rel:
            problems.append("source %s lacks R146 marker" % rel)
        if "SPDX-License-Identifier" not in text:
            problems.append("source %s lacks SPDX header" % rel)
    return problems


def _run_variant(env, source_overrides: dict[str, str],
                 tag: str, cases_path: Path, cases, expected) -> tuple[dict[str, str], list[str]]:
    """Compile a variant of the probe (with overridden sources) and diff."""
    with tempfile.TemporaryDirectory(prefix="r146_%s_" % tag) as tmp:
        tmp_path = Path(tmp)
        sources = []
        for rel in PROBE_SOURCES + [MESSAGE_THREAD]:
            src = ANDROID / rel
            if rel in source_overrides:
                variant = tmp_path / Path(rel).name
                variant.write_text(source_overrides[rel], encoding="utf-8")
                sources.append(variant)
            else:
                sources.append(src)
        classes = tmp_path / "classes"
        kotlinc = env["KOTLINC"]
        compile_probe(kotlinc, sources, classes)
        out = tmp_path / "results.tsv"
        text = run_probe(env["JAVA_HOME"] + "/bin/java", classes, cases_path, out)
    return {}, diff_results(text, expected, cases)


def main() -> int:
    CACHE.mkdir(parents=True, exist_ok=True)
    # Toolchain lives at the bootstrap root; CACHE only holds R146 artifacts.
    env = environment(Path.home() / ".cache" / "fusion-host-r23")
    global TOOL_ENV
    TOOL_ENV = env
    kotlinc = env["KOTLINC"]
    java = env["JAVA_HOME"] + "/bin/java"
    print("== R146 differential: kotlinc=%s" % kotlinc)

    cases = build_corpus()
    print("== corpus: %d cases, %d lanes" % (len(cases),
                                             len({f for f, _p, _n in cases})))

    failures: list[str] = []

    # ---- W1 wiring ----
    w1 = control_wiring(cases)
    print("== control W1 wiring: %s" % ("PASS" if not w1 else "FAIL"))
    failures += w1

    # ---- W4 binding ----
    w4 = control_binding()
    print("== control W4 binding: %s" % ("PASS" if not w4 else "FAIL"))
    failures += w4

    # ---- main differential ----
    sources = [ANDROID / rel for rel in PROBE_SOURCES + [MESSAGE_THREAD]]
    classes = CACHE / "classes"
    print("== compiling %d sources -> %s" % (len(sources), classes))
    compile_probe(kotlinc, sources, classes)
    cases_path, expected = write_cases(cases)
    results_text = run_probe(java, classes, cases_path, CACHE / "results.tsv")
    problems = diff_results(results_text, expected, cases)
    print("== differential: cases=%d mismatches=%d %s"
          % (len(cases), len(problems), "PASS" if not problems else "FAIL"))
    for p in problems[:20]:
        print("   MISMATCH", p)
    failures += problems

    # ---- W2 tamper: weaken MAX_PRECISION in a copy of GeohashCodec ----
    geo_src = (ANDROID / GEOHASH).read_text(encoding="utf-8")
    if "MAX_PRECISION = 12" not in geo_src:
        failures.append("W2: MAX_PRECISION marker missing (cannot tamper)")
        print("== control W2 tamper: FAIL (marker)")
    else:
        tampered = geo_src.replace("const val MAX_PRECISION = 12",
                                   "const val MAX_PRECISION = 20")
        _got, tamper_problems = _run_variant(
            env, {GEOHASH: tampered}, "tamper", cases_path, cases, expected)
        if not tamper_problems:
            failures.append("W2: tampered build produced zero mismatches "
                            "(control did not fire)")
            print("== control W2 tamper: FAIL (no mismatch after weakening)")
        else:
            print("== control W2 tamper: PASS (%d mismatches after weakening)"
                  % len(tamper_problems))

    # ---- W3 defect: pre-fix MessageThread from git HEAD ----
    head_msg = run(["git", "show",
                    "92bcd3278445af8467da5e3fd5cea7531b9815f4:android/" + MESSAGE_THREAD], cwd=str(REPO))
    if head_msg.returncode != 0:
        failures.append("W3: git show HEAD:%s failed" % MESSAGE_THREAD)
        print("== control W3 defect: FAIL (git show)")
    else:
        # Baseline calls a missing extension. Mechanical compile repair only:
        # map lookup defaults to empty, retaining the original broken graph logic.
        print("== W3 baseline: getOrEmpty -> [id].orEmpty compile adapter")
        _got, defect_problems = _run_variant(
            env, {MESSAGE_THREAD: head_msg.stdout.replace("children.getOrEmpty(id)",
                                                        "children[id].orEmpty()")},
            "predefect", cases_path, cases, expected)
        defect_classes = [p for p in defect_problems
                          if "[msgthread]" in p]
        if not defect_classes:
            failures.append("W3: pre-fix MessageThread produced zero "
                            "mismatches (defect did not reproduce)")
            print("== control W3 defect: FAIL (no reproduced defect)")
        else:
            print("== control W3 defect: PASS (%d msgthread mismatches on "
                  "pre-fix source)" % len(defect_classes))
            for p in defect_classes[:6]:
                print("   DEFECT", p)

    if failures:
        print("== R146_DIFF_FAIL problems=%d" % len(failures))
        return 1
    print("== R146_DIFF_OK cases=%d lanes=22 controls=W1,W2,W3,W4"
          % len(cases))
    return 0


if __name__ == "__main__":
    sys.exit(main())
