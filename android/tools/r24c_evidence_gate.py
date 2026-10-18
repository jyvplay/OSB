#!/usr/bin/env python3
"""Fail closed on exact-candidate native subset receipts. Metadata consistency, not CI attestation."""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
NEW_CASES = {
    'dev.fusion.shell.R24TabletopDeviceTest.' + name for name in [
        'actualDragPublishesCoordinatesAndSurvivesActivityRecreation',
        'wallFocusMovePileUnpileAndNamedSnapshotLoadUseRealStore',
        'silverAndLightSceneHaveRealTargetsAndNativeCaptures',
        'measuredTargetsAndSavedViewRemainOperableAtActualTwoHundredPercentFont',
        'corruptTabletopIsNotSilentlyReplacedWithDefaults']
} | {'dev.fusion.shell.R24LocalMediaDeviceTest.' + name for name in [
    'actualAudioReadyIsNotAutoplayThenPlayPauseSeekAndBackgroundRelease',
    'networkUrlIsNotTreatedAsAPlayableLocalDocument']
} | {'dev.fusion.shell.R24MapAndServiceDeviceTest.' + name for name in [
    'realOfflineAtlasSavesPlacePansMeasuresAndReloadsWithoutLocationPermission',
    'providerLinksRequireExplicitDisclosureAndDoNotMasqueradeAsPlayback']}
PRIVACY_CASE = 'dev.fusion.privacy.R24InputChangeDeviceTest.actualImeConfigurationChangeDiscardsAndRequiresExplicitEmptyRestart'


def expected(suite):
    if suite == 'continuation': return NEW_CASES
    old = ROOT / 'docs/verification' / ('r24-final-privacy-result.json' if suite == 'privacy' else 'r24-final-main-result.json')
    historical = json.loads(old.read_text())
    # Historical identities define retained regressions, NEVER current pass outcomes.
    return {row['case'] for row in historical['cases']} | ({PRIVACY_CASE} if suite == 'privacy' else NEW_CASES)


def verify(receipt, candidate, suite):
    if not re.fullmatch('[0-9a-f]{40}', candidate) or receipt.get('candidate') != candidate:
        raise ValueError('Exact source candidate mismatch')
    if receipt.get('conclusion') != 'success' or receipt.get('device_conclusion') != 'success':
        raise ValueError('Whole workflow/device job has not succeeded')
    if receipt.get('suite') != suite or receipt.get('platform_api') not in ((34, 37) if suite == 'privacy' else (34,)):
        raise ValueError('Wrong runtime/suite')
    cases = receipt.get('cases', [])
    names = [row['case'] for row in cases]
    if len(set(names)) != len(names) or set(names) != expected(suite):
        raise ValueError('Missing, duplicated or undeclared test identities')
    if any(row.get('result') != 'PASS' for row in cases):
        raise ValueError('Every required named case must execute PASS; skips/failures are not acceptance')
    if receipt.get('counts') != {'PASS': len(cases), 'FAIL': 0, 'SKIP': 0}:
        raise ValueError('Native count inconsistency')
    return {'candidate': candidate, 'suite': suite, 'api': receipt['platform_api'], 'passed_cases': len(cases),
            'accepted_scope': 'Exact named emulator subset only; not full original specification, physical hardware, legal, subjective beauty or AX acceptance.'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('receipt', type=Path); p.add_argument('--candidate', required=True)
    p.add_argument('--suite', choices=['continuation', 'full', 'privacy'], required=True)
    a = p.parse_args()
    print(json.dumps(verify(json.loads(a.receipt.read_text()), a.candidate, a.suite), indent=2))


if __name__ == '__main__': main()
