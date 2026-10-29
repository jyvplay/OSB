#!/usr/bin/env python3
"""R45 manifest-scope gate across all 17 AndroidManifest.xml files in the repository.

Enforces:
1. `app/src/main/AndroidManifest.xml`: exact R28 component/permission scope
   (2 activities, 1 USB receiver, 0 services/providers, `allowBackup="false"`,
   `INTERNET` + `READ_MEDIA_IMAGES` only).
2. `privacy/src/main/AndroidManifest.xml`: air-gapped companion invariant — ZERO `INTERNET`
   permission and exact `HIDE_OVERLAY_WINDOWS` anti-overlay protection.
3. `app/src/pro/AndroidManifest.xml`: SMS permissions (`READ_SMS`, `SEND_SMS`) isolated
   exclusively to sideload `pro` flavor — forbidden in `main`, `debug`, `privacy`, or libraries.
4. Global zero-spyware ban across all manifests (`SYSTEM_ALERT_WINDOW`, `BIND_ACCESSIBILITY_SERVICE`,
   `QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `MANAGE_EXTERNAL_STORAGE`, `READ_CALL_LOG`,
   `READ_CONTACTS`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`).
Static XML evidence only; merged-manifest truth requires an AGP build.
"""
from __future__ import annotations

from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

GLOBAL_FORBIDDEN = {
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.PACKAGE_USAGE_STATS",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_CALL_LOG",
    "android.permission.READ_CONTACTS",
    "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
}

PRO_ONLY_PERMS = {
    "android.permission.READ_SMS",
    "android.permission.SEND_SMS",
}


def check_manifests() -> dict[str, object]:
    manifests = sorted(
        p for p in ROOT.rglob("AndroidManifest.xml") if "/build/" not in p.as_posix()
    )
    if not manifests:
        raise SystemExit("no AndroidManifest.xml files found")

    for mf in manifests:
        rel = mf.relative_to(ROOT).as_posix()
        root = ET.fromstring(mf.read_text(encoding="utf-8"))
        perms = [
            el.attrib.get(f"{ANDROID_NS}name", "")
            for el in root.findall(".//uses-permission")
        ]
        for perm in perms:
            if perm in GLOBAL_FORBIDDEN:
                raise SystemExit(f"forbidden permission {perm} in {rel}")
            if perm in PRO_ONLY_PERMS and rel != "app/src/pro/AndroidManifest.xml":
                raise SystemExit(f"pro-only SMS permission {perm} leaked into {rel}")

        for app_el in root.findall(".//application"):
            backup = app_el.attrib.get(f"{ANDROID_NS}allowBackup")
            if backup == "true":
                raise SystemExit(f"android:allowBackup='true' forbidden in {rel}")
        for comp_tag in ("activity", "service", "receiver", "provider"):
            for comp in root.findall(f".//{comp_tag}"):
                has_filter = comp.find("intent-filter") is not None
                exported = comp.attrib.get(f"{ANDROID_NS}exported")
                if has_filter and exported is None:
                    raise SystemExit(f"{rel}: <{comp_tag}> with intent-filter missing explicit android:exported")

    # Exact check on app/src/main/AndroidManifest.xml
    app_main = ET.fromstring((ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8"))
    main_perms = sorted(
        e.attrib[f"{ANDROID_NS}name"].split(".")[-1]
        for e in app_main.findall("uses-permission")
    )
    if main_perms != ["INTERNET", "READ_MEDIA_IMAGES"]:
        raise SystemExit(f"unexpected app/src/main permissions: {main_perms}")
    app_node = app_main.find("application")
    assert app_node is not None
    if app_node.attrib.get(f"{ANDROID_NS}allowBackup") != "false":
        raise SystemExit("app/src/main must set android:allowBackup='false'")
    acts = sorted(e.attrib[f"{ANDROID_NS}name"] for e in app_node.findall("activity"))
    recvs = sorted(e.attrib[f"{ANDROID_NS}name"] for e in app_node.findall("receiver"))
    if acts != [".MainActivity", ".PngCopyActivity"] or recvs != [".UsbWatchReceiver"]:
        raise SystemExit(f"unexpected app/src/main components: acts={acts}, recvs={recvs}")
    if app_node.findall("service") or app_node.findall("provider"):
        raise SystemExit("app/src/main must declare 0 services and 0 providers")

    # Exact check on privacy/src/main/AndroidManifest.xml (air-gapped companion: NO INTERNET!)
    priv_main = ET.fromstring((ROOT / "privacy/src/main/AndroidManifest.xml").read_text(encoding="utf-8"))
    priv_perms = sorted(
        e.attrib[f"{ANDROID_NS}name"] for e in priv_main.findall("uses-permission")
    )
    if "android.permission.INTERNET" in priv_perms:
        raise SystemExit("CRITICAL: :privacy companion manifest must NEVER request INTERNET")
    if priv_perms != ["android.permission.HIDE_OVERLAY_WINDOWS"]:
        raise SystemExit(f"unexpected privacy/src/main permissions: {priv_perms}")

    return {
        "manifests_checked": len(manifests),
        "app_main_permissions": main_perms,
        "privacy_permissions": priv_perms,
        "global_forbidden_violations": 0,
    }


def main() -> int:
    summary = check_manifests()
    print(f"R45 manifest-scope gate OK: {summary} (static XML only; merged truth needs AGP)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
