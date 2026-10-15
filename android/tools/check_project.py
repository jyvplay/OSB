#!/usr/bin/env python3
"""SDK-independent structural checks. Does not type-check Kotlin or run Android."""
import hashlib
import re
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1]
subprocess.run([sys.executable, str(root/'tools/requirements.py'), '--check'], check=True)
subprocess.run([sys.executable, str(root/'tools/module_audit.py'), '--check'], check=True)
catalog=tomllib.loads((root/'gradle/libs.versions.toml').read_text())
assert catalog['versions']['minSdk']=='34'
assert catalog['versions']['targetSdk']=='36'
assert catalog['versions']['compileSdk']=='36'  # Explicit API37 deferral, docs/BUILD.md.
modules=re.findall(r'"(:[\w:]+)"', (root/'settings.gradle.kts').read_text())
for module in modules:
    p=root/module[1:].replace(':','/')/'build.gradle.kts'
    assert p.exists(), f'Missing module: {module}'
    text=p.read_text()
    assert not re.search(r'(?:implementation|api|testImplementation)\("[^":]+:[^"\n]+"\)', text), f'Uncatalogued dependency: {module}'
    for dep in re.findall(r'project\("(:[\w:]+)"\)', text):
        assert dep in modules, f'{module} references missing module {dep}'
for manifest in root.rglob('AndroidManifest.xml'): ET.parse(manifest)
for resource in (root/'app/src/main/res').rglob('*.xml'): ET.parse(resource)
sha=hashlib.sha256((root/'gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest()
assert sha=='81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f', 'Wrapper changed: verify upstream and update review record'
main=(root/'app/src/main/java/dev/fusion/shell/MainActivity.kt').read_text()
assert 'LocalMediaServer(' not in main, 'Files grants must not start network sharing'
assert 'pendingSaveText' not in main, 'Picker payload must survive Activity/process recreation'
assert 'linkSpeedMbps = 100' not in main, 'Do not invent hardware/link measurements'
shared=(root/'feature/messages/src/main/AndroidManifest.xml').read_text()
assert 'android.permission.READ_SMS' not in shared and 'android.permission.SEND_SMS' not in shared
for p in root.rglob('*.gradle.kts'):
    assert 'com.google.android.gms:play-services-cast' not in p.read_text(), 'Cast SDK must never leak into pro'
print(f'{len(modules)} included modules; XML/catalog/project references/wrapper checksum/privacy contracts checked.')
print('This is source validation only. Run Gradle, lint, JVM and device tests separately.')

policy=(root/'core/model/src/main/kotlin/dev/fusion/model/PilotPolicy.kt').read_text()
assert 'PilotPolicy.mayRun(module, acknowledgedExperiments)' in main
assert 'mutationsAllowed = false' in main
assert 'key(w.id)' in main, 'Window focus reordering must not transfer Compose state between windows'
listener=ET.parse(root/'feature/alerts/src/main/AndroidManifest.xml').getroot().find('application/service')
assert listener.attrib['{http://schemas.android.com/apk/res/android}enabled']=='false', 'Unreviewed notification ingestion must remain disabled'
recorder=(root/'feature/capture/src/main/java/dev/fusion/capture/ScreenRecordService.kt').read_text()
for contract in ['PendingIntent.FLAG_IMMUTABLE','Intent.ACTION_SCREEN_OFF','THERMAL_STATUS_SEVERE','.mp4.partial','METADATA_KEY_DURATION','unregisterCallback(callback)']:
    assert contract in recorder, 'Missing capture safety contract: '+contract
print('Pilot entry gates, read-only Files, stable window keys, disabled listener and capture safety source contracts checked.')

copy_manifest=ET.parse(root/'feature/editor/src/main/AndroidManifest.xml').getroot()
copy_activity=copy_manifest.find('application/activity')
ns='{http://schemas.android.com/apk/res/android}'
assert copy_activity is not None and copy_activity.attrib.get(ns+'exported')=='false', 'Copy broker must not be exported'
assert copy_activity.attrib.get(ns+'name')=='dev.fusion.editor.CopyDestinationActivity'
assert copy_activity.find('intent-filter') is None, 'Copy broker must not accept implicit external requests'
assert 'registerForActivityResult(CopyDestinationContract())' in main, 'Do not restore uncorrelated Save a Copy callbacks'
fixture=root/'core/documents/src/contract/resources/editor-session-v1.bin'
assert hashlib.sha256(fixture.read_bytes()).hexdigest()=='225712d0e470b2ff9706b49fb8c1f6c344608cebd3a39ef983c601f50a6088a0', 'Historical migration fixture changed'
print('Copy broker manifest/routing and historical fixture fingerprint checked (not Android execution).')

subprocess.run([sys.executable, str(root/'tools/native_source_audit.py'), '--check'], check=True)

assert hashlib.sha256((root/"core/windowing/src/contract/resources/desktop-v1.bin").read_bytes()).hexdigest()=="3a5356222b28828fc11a95317d3c4c8766af824f37ebd6d76be8c64bbb753d5d", "Historical desktop fixture changed"

assert hashlib.sha256((root/'core/documents/src/contract/resources/editor-session-v2.bin').read_bytes()).hexdigest()=='369cf464ed4a97a484e79c954bf87395f21ebab285081b77f381d6baac0733d4', 'Historical editor v2 fixture changed'

subprocess.run([sys.executable,str(root/'tools/module_testability.py'),'--check'],check=True)
