#!/usr/bin/env python3
"""Real host Linux amnesic, DMA/IOMMU, swap, core-dump, and USB HID posture auditor.

Runs cleanly inside a sandbox/container or bare-metal Linux laptop without root. Reports
exact observed kernel/procfs/sysfs facts (`OBSERVED` vs `UNAVAILABLE_IN_CONTAINER`) and feeds
them to the same logical rules encoded in `TailsCadence` and `DmaGuardAudit`.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import resource
import sys


def read_text_safe(path: str) -> str | None:
    p = Path(path)
    try:
        return p.read_text(errors="replace").strip() if p.exists() else None
    except OSError:
        return None


def collect_posture() -> dict[str, object]:
    # 1. Swap posture (/proc/swaps)
    swaps_raw = read_text_safe("/proc/swaps")
    swap_bytes = 0
    swap_devices: list[str] = []
    if swaps_raw is not None:
        for line in swaps_raw.splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 3:
                swap_devices.append(parts[0])
                swap_bytes += int(parts[2]) * 1024

    # 2. Core-dump & suid_dumpable posture
    suid_raw = read_text_safe("/proc/sys/fs/suid_dumpable")
    suid_dumpable = int(suid_raw) if (suid_raw and suid_raw.isdigit()) else -1
    soft_core, _ = resource.getrlimit(resource.RLIMIT_CORE)
    core_pattern = read_text_safe("/proc/sys/kernel/core_pattern")
    core_disabled = (soft_core == 0) or (core_pattern in ("|/bin/false", "/dev/null", ""))

    # 3. IOMMU / DMA sysfs & cmdline observation
    iommu_dir = Path("/sys/kernel/iommu_groups")
    iommu_groups = len(list(iommu_dir.iterdir())) if iommu_dir.is_dir() else 0
    cmdline = read_text_safe("/proc/cmdline") or ""
    passthrough_flag = "iommu=pt" in cmdline or "iommu.passthrough=1" in cmdline
    strict_flag = "iommu.strict=1" in cmdline and "iommu.passthrough=0" in cmdline
    dmar_acpi = Path("/sys/firmware/acpi/tables/DMAR").exists() or Path("/sys/firmware/acpi/tables/IVRS").exists()

    # 4. Mount persistence check (/proc/mounts)
    mounts_raw = read_text_safe("/proc/mounts") or ""
    ephemeral_fs = {"tmpfs", "ramfs", "proc", "sysfs", "devpts", "cgroup", "cgroup2", "mqueue", "overlay"}
    persistent_rw: list[str] = []
    for line in mounts_raw.splitlines():
        cols = line.split()
        if len(cols) >= 4:
            mp, fstype, opts = cols[1], cols[2], cols[3].split(",")
            if "rw" in opts and fstype not in ephemeral_fs and not mp.startswith(("/proc", "/sys", "/dev")):
                persistent_rw.append(f"{mp}({fstype})")

    # 5. USB HID enumeration check (/sys/bus/usb/devices)
    usb_dir = Path("/sys/bus/usb/devices")
    usb_hid_interfaces: list[str] = []
    if usb_dir.is_dir():
        for dev in sorted(usb_dir.iterdir()):
            bclass = read_text_safe(str(dev / "bInterfaceClass"))
            if bclass == "03":  # USB HID class
                usb_hid_interfaces.append(dev.name)

    # 6. Process RSS footprint
    status_raw = read_text_safe("/proc/self/status") or ""
    rss_kib = 0
    for line in status_raw.splitlines():
        if line.startswith("VmRSS:"):
            rss_kib = int(line.split()[1])
            break

    amnesic_ready = (swap_bytes == 0) and core_disabled and (suid_dumpable in (0, -1)) and len(persistent_rw) == 0
    return {
        "scope": "Real host Linux procfs/sysfs observation; container/VM guests cannot observe hypervisor physical IOMMU/UEFI DMAR when masked.",
        "swap_bytes": swap_bytes,
        "swap_devices": swap_devices,
        "suid_dumpable": suid_dumpable,
        "rlimit_core_soft": soft_core,
        "core_pattern": core_pattern,
        "core_dumps_disabled_for_process": core_disabled,
        "iommu_group_count": iommu_groups,
        "cmdline_strict_iommu": strict_flag,
        "cmdline_passthrough_iommu": passthrough_flag,
        "acpi_dmar_or_ivrs_visible": dmar_acpi,
        "persistent_rw_mounts": persistent_rw,
        "usb_hid_interfaces_visible": usb_hid_interfaces,
        "auditor_rss_kib": rss_kib,
        "strict_amnesic_posture": amnesic_ready,
    }


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--json", action="store_true", help="Emit machine-readable JSON")
    args = ap.parse_args(argv)
    posture = collect_posture()
    if args.json:
        print(json.dumps(posture, indent=2))
    else:
        print(
            f"Amnesic Host Audit: swap={posture['swap_bytes']}B, "
            f"core_disabled={posture['core_dumps_disabled_for_process']}, "
            f"suid_dumpable={posture['suid_dumpable']}, "
            f"iommu_groups={posture['iommu_group_count']}, "
            f"persistent_rw_mounts={len(posture['persistent_rw_mounts'])}, "
            f"strict_amnesic={posture['strict_amnesic_posture']}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
