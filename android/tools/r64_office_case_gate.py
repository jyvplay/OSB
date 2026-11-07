#!/usr/bin/env python3
"""Reject incomplete/duplicate Office JUnit runs; not visual or physical acceptance."""
import argparse
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import report_device_results

# The first 33 are the actual named cases observed in R63 Office run 41801535542;
# four R64 methods are added. This is not inferred from a pass-count alone.
EXPECTED = frozenset((
    'dev.fusion.shell.CopyContractDeviceTest.brokerIsExplicitAndNotExported',
    'dev.fusion.shell.CopyContractDeviceTest.officeMimeIsExplicitlyAllowlistedWithoutChangingPlainTextContract',
    'dev.fusion.shell.CopyContractDeviceTest.selectedResultRetainsItsOriginalIdentity',
    'dev.fusion.shell.CopyContractDeviceTest.uncorrelatedResultNeverBecomesCurrentCancellation',
    'dev.fusion.shell.NativeVisualEvidenceDeviceTest.dialogCaptureFilenameAllowlistRejectsTraversalAndUnknownPrefixes',
    'dev.fusion.shell.NativeVisualEvidenceDeviceTest.minimumTargetCheckerAcceptsFortyEightDpAndRejectsThirtyTwoDp',
    'dev.fusion.shell.NativeVisualEvidenceDeviceTest.textFitCheckerAcceptsVisibleTextAndRejectsClippedScrollableText',
    'dev.fusion.shell.R24MapAndServiceDeviceTest.providerLinksRequireExplicitDisclosureAndDoNotMasqueradeAsPlayback',
    'dev.fusion.shell.R24MapAndServiceDeviceTest.realOfflineAtlasSavesPlacePansMeasuresAndReloadsWithoutLocationPermission',
    'dev.fusion.shell.R24MapAndServiceDeviceTest.serviceReviewRejectsOversizeTextAndKeepsStatusForIdenticalText',
    'dev.fusion.shell.R54OfficeDeviceTest.formattedWriterRejectsConsentAfterDraftRevisionChanges',
    'dev.fusion.shell.R54OfficeDeviceTest.numberedWriterOptInCancelResetAndRealPicker',
    'dev.fusion.shell.R54OfficeDeviceTest.numberedWriterRealProviderReadbackKeepsSourceAndNumbering',
    'dev.fusion.shell.R54OfficeDeviceTest.realProviderRefusesAliasAndStaleResultCannotAuthorizeNewExport',
    'dev.fusion.shell.R54OfficeDeviceTest.realProviderRefusesAnUnrelatedNonemptyDestination',
    'dev.fusion.shell.R54OfficeDeviceTest.realProviderWritesAndReadsFormulaWorkbook',
    'dev.fusion.shell.R54OfficeDeviceTest.sheetEditsRecalculatesKeepsUnappliedInputAndCapturesActualUi',
    'dev.fusion.shell.R54OfficeDeviceTest.strikeWriterRealProviderReadbackKeepsSourceAndList',
    'dev.fusion.shell.R54OfficeDeviceTest.structuredWriterOptionUsesRealEditorAndSystemPicker',
    'dev.fusion.shell.R54OfficeDeviceTest.structuredWriterRealProviderReadbackPreservesSource',
    'dev.fusion.shell.R54OfficeDeviceTest.unavailableSourceIdentitiesRefuseBeforeOpeningDestination',
    'dev.fusion.shell.R54OfficeDeviceTest.unreadableProviderDestinationFailsClosed',
    'dev.fusion.shell.R54OfficeDeviceTest.writerExportDisclosureIsVisibleInRealEditor',
    'dev.fusion.shell.R54OfficeDeviceTest.writerListRestartProviderReadbackContainsIndependentOverrides',
    'dev.fusion.shell.R54OfficeDeviceTest.writerListRestartReviewIsOptInResettableAndReachesRealPicker',
    'dev.fusion.shell.R54OfficeDeviceTest.writerPipeTableProviderReadbackPreservesSourceAndLiteralCells',
    'dev.fusion.shell.R54OfficeDeviceTest.writerPipeTableReviewResetsAndUsesRealPicker',
    'dev.fusion.shell.R54OfficeDeviceTest.writerStrikeReviewIsOptInResettableAndCancelable',
    'dev.fusion.shell.R58SheetDeviceTest.textGridReviewCancelApplyUndoRedoCheckpointAndRealProviderReadback',
    'dev.fusion.shell.R59SheetDeviceTest.blankSkipAndExistingCellProtectionAreReviewedAndUndoable',
    'dev.fusion.shell.R59SheetDeviceTest.csvValuesDisclosureRealPickerProviderAndRiskRefusal',
    'dev.fusion.shell.R59SheetDeviceTest.formulaFillAdjustsReferencesProtectsConflictsAndSupportsUndoAndExport',
    'dev.fusion.shell.R59SheetDeviceTest.literalFillAndClearRangeAreReviewedProtectedUndoableAndExported',
    'dev.fusion.shell.R59SheetDeviceTest.optInReviewCancelApplyUndoAndProviderReadback',
    'dev.fusion.shell.R59SheetDeviceTest.rectangularCopyReviewsOverlapProtectionUndoAndProvider',
    'dev.fusion.shell.R59SheetDeviceTest.transposeOptInPreviewCancelApplyUndoAndProviderReadback',
    'dev.fusion.shell.ShellSmokeTest.explicitLocalPlanOpensActualCalculatorAndPreservesModuleGates',
))


def verify(root: Path, source: str | None) -> dict:
    if source is None or re.fullmatch(r'[0-9a-f]{40}', source) is None:
        raise ValueError('A full exact GitHub source SHA is required')
    if not root.is_dir() or root.is_symlink():
        raise ValueError('Managed-device result directory is missing or unsafe')
    paths = sorted(root.rglob('TEST-*.xml'))
    if not paths or len(paths) > 100:
        raise ValueError('Missing or excessive managed-device result files')
    names = []
    for path in paths:
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 16 * 1024 * 1024:
            raise ValueError('Unsafe or oversized result')
        for node in ET.parse(path).iter('testcase'):
            cls, name = node.get('classname', ''), node.get('name', '')
            if not cls or not name or len((cls + name).encode('utf-8')) > 1024:
                raise ValueError('Invalid case identity')
            names.append(f'{cls}.{name}')
            if len(names) > 1000: raise ValueError('Too many cases')
    if len(EXPECTED) != 37 or len(names) != len(EXPECTED) or len(set(names)) != len(names) or set(names) != EXPECTED:
        raise ValueError('Expected exactly the 37 unique named Office cases')
    report = report_device_results.collect(root)
    if report['source_commit'] != source or report['counts'] != {'PASS': 37, 'FAIL': 0, 'SKIP': 0}:
        raise ValueError('All 37 Office cases must actually PASS on the candidate SHA')
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', default='app/build/outputs/androidTest-results/managedDevice')
    args = parser.parse_args()
    report = verify(Path(args.root), os.environ.get('GITHUB_SHA'))
    print(f"Exact Office case gate: {report['counts']['PASS']}/37 PASS; frame inspection is separate.")


if __name__ == '__main__': main()
