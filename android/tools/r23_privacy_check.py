#!/usr/bin/env python3
"""SDK-independent R23 privacy source checks; never a Kotlin or Android runtime test."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "http://schemas.android.com/apk/res/android"
manifest_path = ROOT / "privacy/src/main/AndroidManifest.xml"
manifest = ET.parse(manifest_path).getroot()
permissions = [node.attrib.get(f"{{{ANDROID}}}name", "") for node in manifest.findall("uses-permission")]
assert "android.permission.INTERNET" not in permissions, "Offline companion must not request INTERNET"
assert "android.permission.QUERY_ALL_PACKAGES" not in permissions, "Do not add broad package visibility for the dashboard"
application = manifest.find("application")
assert application is not None
assert application.attrib.get(f"{{{ANDROID}}}allowBackup") == "false"
assert application.attrib.get(f"{{{ANDROID}}}usesCleartextTraffic") == "false"
activity = application.find("activity")
assert activity is not None and activity.attrib.get(f"{{{ANDROID}}}name") == ".PrivacyActivity"

activity_source = (ROOT / "privacy/src/main/java/dev/fusion/privacy/PrivacyActivity.kt").read_text()
probe_source = (ROOT / "privacy/src/main/java/dev/fusion/privacy/DevicePrivacySnapshot.kt").read_text()
test_source = (ROOT / "privacy/src/androidTest/java/dev/fusion/privacy/PrivacyPostureTest.kt").read_text()
ids = ET.parse(ROOT / "privacy/src/main/res/values/ids.xml").getroot()
resource_ids = {node.attrib.get("name") for node in ids.findall("item")}
required_ids = {
    "device_posture", "device_posture_status", "refresh_device_posture",
    "review_keyboard_settings", "review_accessibility_settings",
    "review_developer_settings", "review_security_settings",
}
assert required_ids <= resource_ids, "Missing R23 view IDs"
for token in (
    "Settings.ACTION_INPUT_METHOD_SETTINGS", "Settings.ACTION_ACCESSIBILITY_SETTINGS",
    "Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS", "Settings.ACTION_SECURITY_SETTINGS",
    "DevicePrivacyProbe.capture", "FLAG_SECURE",
):
    assert token in activity_source, f"Missing user-controlled privacy surface: {token}"
for token in (
    "Settings.Global.getInt", "Settings.Global.ADB_ENABLED, -1", "currentInputMethodInfo",
    "getEnabledAccessibilityServiceList", "InputDevice.getDeviceIds", "getOrNull()",
    "val nonVirtualKeyboardNames: List<String>?", "not a malware or keylogger scan",
    "not proof that the device is safe or anonymous", "unknown", "unavailable",
):
    assert token in probe_source, f"Missing bounded/unknown/anti-overclaim contract: {token}"
for token in (
    "untrustedDeviceTextCannotInjectControlOrBidirectionalFormatting",
    "evenAnAllReassuringSnapshotDoesNotClaimTheDeviceIsSafeOrAnonymous",
    "failedOrFilteredObservationsRemainUnknownRatherThanBecomingFalse",
    "livePanelRendersBoundedObservationsAndSettingsButtons",
):
    assert token in test_source, f"Missing R23 adversarial Android test: {token}"
for source in (activity_source, probe_source):
    for forbidden in (
        "SharedPreferences", "openFileOutput(", "HttpURLConnection", "OkHttpClient",
        "java.net.Socket", "Socket(", "android.net.VpnService",
    ):
        assert forbidden not in source, f"Unexpected R23 persistence/network route: {forbidden}"

print("R23 privacy source contracts PASS: manifest, bounded UI IDs, user-directed Settings, unknown-state and non-claim test presence.")
print("STRUCTURAL CHECK ONLY: Kotlin was not compiled; Android behavior, Settings, IME, accessibility, USB and hardware were not executed or verified.")
