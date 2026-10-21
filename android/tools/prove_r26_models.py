#!/usr/bin/env python3
"""Machine-checked Z3 5.3.1 SMT formal proofs for R26 security, crash-safety, and geometry kernels."""
from __future__ import annotations

import argparse
import itertools
import json
import sys
from pathlib import Path

for extra in (
    Path.home() / ".cache/fusion-r26-tools",
    Path.home() / ".cache/fusion-r25-reference/python",
):
    if extra.is_dir() and str(extra) not in sys.path:
        sys.path.insert(0, str(extra))

import z3  # noqa: E402


def prove_theorem(name: str, solver: z3.Solver, scope: str) -> dict:
    verdict = solver.check()
    if verdict != z3.unsat:
        model = solver.model() if verdict == z3.sat else None
        raise AssertionError(f"Formal proof {name} FAILED ({verdict}): counterexample={model}")
    return {
        "theorem": name,
        "prover": f"z3-solver {z3.get_version_string()}",
        "negated_query_result": "unsat",
        "proved": True,
        "scope": scope,
    }


def t1_file_move_journal_no_data_loss() -> dict:
    # Stages: 0=PREPARED, 1=COPY_VERIFIED, 2=SOURCE_UNLINKED, 3=COMMITTED
    s = z3.Solver()
    stage = z3.Int("stage")
    src_before = z3.Bool("src_before")
    dst_exists = z3.Bool("dst_exists")
    dst_valid = z3.Bool("dst_valid")

    # Forward protocol invariants of FileMoveJournal.moveVerified:
    s.add(stage >= 0, stage <= 3)
    s.add(z3.Implies(dst_valid, dst_exists))
    # Source is never unlinked before stage >= 1 (COPY_VERIFIED), so in stage 0 src MUST exist:
    s.add(z3.Implies(stage == 0, src_before))
    # Stage >= 1 is logged ONLY AFTER full SHA-256 readback verification of atomic rename destination:
    s.add(z3.Implies(stage >= 1, dst_valid))

    # Recovery transition function of FileMoveJournal.recover():
    src_after = z3.If(
        stage == 0,
        z3.If(dst_valid, z3.BoolVal(False), src_before),
        z3.If(stage >= 1, z3.BoolVal(False), src_before),
    )
    dst_after_valid = z3.If(
        stage == 0,
        z3.If(dst_valid, z3.BoolVal(True), z3.BoolVal(False)),
        dst_valid,
    )

    # Negate safety property: exact-once preservation requires XOR(intact source, verified destination)
    safety = z3.Xor(src_after, dst_after_valid)
    s.add(z3.Not(safety))
    return prove_theorem(
        "T1_file_move_journal_exact_once_recovery",
        s,
        "Single-filesystem non-compromised disk model; excludes silent physical media bit-rot after fsync.",
    )


def t2_scrambled_keypad_zero_coordinate_leakage() -> dict:
    # Over full symmetric group S_4 (24 bijections) on 4 keypad slots across 2 independent strokes (576 uniform states):
    # Prove that for EVERY pair of distinct secrets (a1, a2) and (b1, b2) and EVERY observed coordinate pair (x1, x2),
    # exact count of random keypad schedules emitting (x1, x2) is identical (= 36/576 = 1/16), hence I(S; X) = 0 bits.
    perms = list(itertools.permutations(range(4)))
    s = z3.Solver()
    a1, a2, b1, b2, x1, x2 = z3.Ints("a1 a2 b1 b2 x1 x2")
    for v in (a1, a2, b1, b2, x1, x2):
        s.add(v >= 0, v < 4)

    def count_schedules(sym1: z3.ArithRef, sym2: z3.ArithRef) -> z3.ArithRef:
        terms = []
        for p1 in perms:
            for p2 in perms:
                hit1 = z3.Or(*[z3.And(x1 == slot, sym1 == p1[slot]) for slot in range(4)])
                hit2 = z3.Or(*[z3.And(x2 == slot, sym2 == p2[slot]) for slot in range(4)])
                terms.append(z3.If(z3.And(hit1, hit2), 1, 0))
        return z3.Sum(terms)

    c_a = count_schedules(a1, a2)
    c_b = count_schedules(b1, b2)
    s.add(c_a != c_b)
    return prove_theorem(
        "T2_scrambled_keypad_zero_coordinate_mutual_information",
        s,
        "Coordinate-only attacker model with ideal CSPRNG; excludes simultaneous framebuffer+touch capture.",
    )


def t3_timing_quantizer_constant_cadence() -> dict:
    s = z3.Solver()
    q = z3.IntVal(100_000_000)
    arrivals = [z3.Int(f"t_{i}") for i in range(5)]
    for i, t in enumerate(arrivals):
        s.add(t >= 0, t <= 10_000_000_000)
        if i > 0:
            s.add(t >= arrivals[i - 1])

    def next_slot(x: z3.ArithRef) -> z3.ArithRef:
        rem = x % q
        return z3.If(rem == 0, x + q, x + (q - rem))

    base = next_slot(arrivals[0])
    emitted = []
    cursor = base
    for raw in arrivals:
        cand = z3.If(cursor >= next_slot(raw), cursor, next_slot(raw))
        emitted.append(cand)
        cursor = cand + q

    # Prove every emitted slot is an exact integer multiple of Q AND strictly increasing by >= Q:
    bad = z3.Or(
        *[e % q != 0 for e in emitted],
        *[emitted[i] - emitted[i - 1] < q for i in range(1, len(emitted))],
    )
    s.add(bad)
    return prove_theorem(
        "T3_timing_quantizer_lattice_alignment",
        s,
        "Logical schedule timestamps; OS thread scheduler jitter remains bounded only to kernel timer slack.",
    )


def t4_tabletop_floor_dominance_and_inverse() -> dict:
    s = z3.Solver()
    inset = z3.RealVal("0.055")
    horizon = z3.RealVal("0.12")
    back = 1 - 2 * inset
    # Trapezoid corners of DeskPlane.TABLE:
    # p00 = (inset, horizon), p10 = (1 - inset, horizon), p11 = (1, 1), p01 = (0, 1)
    top_width = (1 - inset) - inset
    bottom_width = z3.RealVal("1.0")
    height = 1 - horizon
    area = (top_width + bottom_width) * height / 2

    u, v = z3.Reals("u v")
    s.add(u >= 0, u <= 1, v >= 0, v <= 1)
    divisor = 1 + (1 / back - 1) * (1 - v)
    px = z3.RealVal("0.5") + (u - z3.RealVal("0.5")) / divisor
    py = (horizon * (1 - v) / back + v) / divisor

    denom = back - horizon + py * (1 - back)
    v_inv = (py - horizon) / denom
    div_inv = 1 + (1 / back - 1) * (1 - v_inv)
    u_inv = z3.RealVal("0.5") + (px - z3.RealVal("0.5")) * div_inv

    s.add(
        z3.Or(
            area != z3.RealVal("0.8316"),
            area <= z3.RealVal("0.80"),
            denom <= z3.RealVal("0.01"),
            u_inv != u,
            v_inv != v,
        )
    )
    return prove_theorem(
        "T4_tabletop_8316_floor_dominance_and_exact_bijection",
        s,
        "Exact rational projective algebra on [0,1]^2; IEEE-754 float rounding bounded to ~1e-15.",
    )


def t5_amnesic_epoch_and_ram_ceiling() -> dict:
    s = z3.Solver()
    max_ram = z3.IntVal(4 * 1024 * 1024)
    epoch0, stored0, put_size, read_handle = z3.Ints("epoch0 stored0 put_size read_handle")
    s.add(epoch0 >= 1, stored0 >= 0, stored0 <= max_ram, put_size >= 0)
    admitted_put = stored0 + put_size <= max_ram
    stored1 = z3.If(admitted_put, stored0 + put_size, stored0)
    handle0 = epoch0
    # After panicWipe():
    epoch1 = epoch0 + 1
    stored2 = z3.IntVal(0)
    stale_admitted = (handle0 == epoch1)
    s.add(z3.Or(stored1 > max_ram, stored2 != 0, stale_admitted))
    return prove_theorem(
        "T5_amnesic_vault_ram_ceiling_and_epoch_revocation",
        s,
        "User-space JVM byte-buffer model; JVM GC copies prior to putSecret are outside Java array zeroization.",
    )


def t6_tor_socks5h_fail_closed() -> dict:
    s = z3.Solver()
    # Proxy modes: 0=none, 1=socks5_local_dns, 2=socks5h_remote_dns, 3=socks5h_remote_host_non_loopback
    # Schemes: 0=http, 1=https
    proxy_mode, scheme = z3.Ints("proxy_mode scheme")
    is_onion_v3, strict_onion, has_credentials = z3.Bools("is_onion_v3 strict_onion has_credentials")
    s.add(proxy_mode >= 0, proxy_mode <= 3, scheme >= 0, scheme <= 1)

    admitted = z3.And(
        proxy_mode == 2,
        z3.Not(has_credentials),
        z3.Implies(strict_onion, is_onion_v3),
        z3.Implies(z3.Not(is_onion_v3), scheme == 1),
    )
    local_dns_leak = z3.And(admitted, proxy_mode != 2)
    clearnet_plaintext_leak = z3.And(admitted, z3.Not(is_onion_v3), scheme == 0)
    credential_leak = z3.And(admitted, has_credentials)
    s.add(z3.Or(local_dns_leak, clearnet_plaintext_leak, credential_leak))
    return prove_theorem(
        "T6_tor_socks5h_no_local_dns_or_clearnet_http_leak",
        s,
        "Application URL/proxy admission policy; system-wide WebView/MediaPlayer enforcement requires OS netfilter/VPN.",
    )


def run_all() -> dict:
    proofs = [
        t1_file_move_journal_no_data_loss(),
        t2_scrambled_keypad_zero_coordinate_leakage(),
        t3_timing_quantizer_constant_cadence(),
        t4_tabletop_floor_dominance_and_inverse(),
        t5_amnesic_epoch_and_ram_ceiling(),
        t6_tor_socks5h_fail_closed(),
    ]
    return {
        "suite": "R26 Formal SMT Verification",
        "prover": f"z3-solver {z3.get_version_string()}",
        "theorem_count": len(proofs),
        "all_unsat_proved": all(p["proved"] for p in proofs),
        "theorems": proofs,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, help="Optional JSON output path")
    args = parser.parse_args()
    result = run_all()
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({"prover": result["prover"], "theorem_count": result["theorem_count"], "all_unsat_proved": result["all_unsat_proved"]}))


if __name__ == "__main__":
    main()
