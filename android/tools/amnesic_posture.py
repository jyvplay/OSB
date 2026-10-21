#!/usr/bin/env python3
"""Read-only Linux/Container Amnesic Memory & Hardware-Origin Attack Surface Auditor."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_usb_composite_devices(sysfs_usb_root: Path) -> list[dict]:
    """Identify composite USB devices combining Mass Storage (`08`) and HID Keyboard/Mouse (`03`) interfaces (BadUSB risk)."""
    findings = []
    if not sysfs_usb_root.is_dir():
        return findings
    for dev_dir in sorted(sysfs_usb_root.iterdir()):
        if ":" in dev_dir.name:
            continue
        classes = set()
        for iface in sorted(dev_dir.glob(f"{dev_dir.name}:*")):
            cls_file = iface / "bInterfaceClass"
            if cls_file.is_file():
                classes.add(cls_file.read_text(errors="ignore").strip().lower())
        if classes:
            vendor = (dev_dir / "idVendor").read_text(errors="ignore").strip() if (dev_dir / "idVendor").is_file() else "unknown"
            product = (dev_dir / "idProduct").read_text(errors="ignore").strip() if (dev_dir / "idProduct").is_file() else "unknown"
            findings.append(
                {
                    "device": dev_dir.name,
                    "vid_pid": f"{vendor}:{product}",
                    "interface_classes": sorted(classes),
                    "composite_hid_plus_storage_suspect": ("03" in classes and "08" in classes),
                }
            )
    return findings


def parse_proc_swaps(swaps_text: str) -> list[str]:
    lines = [ln.strip() for ln in swaps_text.splitlines() if ln.strip()]
    return [ln.split()[0] for ln in lines[1:] if ln.split()]


def parse_mounts_tmpfs(mounts_text: str) -> list[str]:
    ram_mounts = []
    for ln in mounts_text.splitlines():
        parts = ln.split()
        if len(parts) >= 3 and parts[2] in ("tmpfs", "ramfs"):
            ram_mounts.append(parts[1])
    return sorted(set(ram_mounts))


def inspect_host(root: Path = Path("/")) -> dict:
    swaps_file = root / "proc/swaps"
    mounts_file = root / "proc/mounts"
    iommu_dir = root / "sys/class/iommu"
    usbguard_conf = root / "etc/usbguard/rules.conf"
    ptrace_file = root / "proc/sys/kernel/yama/ptrace_scope"
    kptr_file = root / "proc/sys/kernel/kptr_restrict"

    active_swaps = parse_proc_swaps(swaps_file.read_text(errors="ignore")) if swaps_file.is_file() else []
    tmpfs_mounts = parse_mounts_tmpfs(mounts_file.read_text(errors="ignore")) if mounts_file.is_file() else []
    usb_devices = parse_usb_composite_devices(root / "sys/bus/usb/devices")
    iommu_groups = [p.name for p in sorted(iommu_dir.iterdir())] if iommu_dir.is_dir() else []
    ptrace_scope = int(ptrace_file.read_text().strip()) if ptrace_file.is_file() else None
    kptr_restrict = int(kptr_file.read_text().strip()) if kptr_file.is_file() else None

    return {
        "mode": "READ_ONLY_HOST_POSTURE_OBSERVATION",
        "amnesic_swap_free": len(active_swaps) == 0,
        "active_swap_devices": active_swaps,
        "tmpfs_ephemeral_mounts": tmpfs_mounts,
        "iommu_dma_units_detected": len(iommu_groups),
        "usbguard_policy_present": usbguard_conf.is_file(),
        "usb_devices": usb_devices,
        "composite_badusb_suspects": [d for d in usb_devices if d["composite_hid_plus_storage_suspect"]],
        "kernel_hardening": {
            "yama_ptrace_scope": ptrace_scope,
            "kptr_restrict": kptr_restrict,
        },
        "honest_boundary": (
            "Passive inline hardware keyloggers, pre-boot firmware implants, and cloned VID:PID HID descriptors "
            "cannot be proved absent from software alone."
        ),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, help="Optional JSON report path")
    args = parser.parse_args()
    result = inspect_host()
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + "\n")
    print(
        json.dumps(
            {
                "amnesic_swap_free": result["amnesic_swap_free"],
                "tmpfs_mounts": len(result["tmpfs_ephemeral_mounts"]),
                "composite_badusb_suspects": len(result["composite_badusb_suspects"]),
            }
        )
    )


if __name__ == "__main__":
    main()
