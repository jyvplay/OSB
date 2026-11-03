#!/usr/bin/env python3
"""Static integration guardrails only; they do not replace Android execution."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path): return (ROOT / path).read_text(encoding='utf-8')


class R62IntegrationContracts(unittest.TestCase):
    def test_legacy_editor_entry_point_keeps_previous_callback_shape(self):
        source = read('feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt')
        legacy = source.split('fun EditorScreen(', 1)[1].split(') = EditorScreenContent', 1)[0]
        self.assertIn('onExportStructuredDocx: ((dev.fusion.documents.TextDocument, Boolean) -> Unit)? = null', legacy)
        self.assertIn('onSaveCopy: (CopyPickerRequest) -> Unit', legacy)
        self.assertNotIn('onExportStructuredDocxWithStrike', legacy)
        self.assertIn('fun EditorScreenWithStrike(', source)

    def test_strike_resets_off_and_is_checked_after_consent(self):
        source = read('feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt')
        self.assertIn('numberedLines=false; strikeSpans=false', source)
        block = source.split('structuredDocxConsent?.let { consent ->', 1)[1].split('copyConfirmId?.let', 1)[0]
        self.assertLess(block.index('consent.permits(doc,buffer.revision)'), block.index('onExportStructuredDocxWithStrike(doc, numberedLines, strikeSpans)'))
        self.assertIn('writer-strike-option', block)

    def test_range_requires_unchanged_snapshot_and_blocks_other_edits(self):
        model = read('feature/calc/src/main/java/dev/fusion/calc/SheetViewModel.kt')
        ready = model.split('private fun ready(): Boolean', 1)[1].split('fun updateInput', 1)[0]
        self.assertIn('state.value.rangePreview == null', ready)
        accept = model.split('fun acceptRange()', 1)[1].split('fun cancelRangePreview()', 1)[0]
        self.assertIn('!plan.isCurrent(current.data.draft, current.selected)', accept)
        self.assertLess(accept.index('if (plan.changed == 0)'), accept.index('push(undo, plan.original.cells)'))

    def test_protection_is_default_and_export_is_not_automatic(self):
        ui = read('feature/calc/src/main/java/dev/fusion/calc/SheetScreen.kt')
        self.assertIn('rangeInput = selected; rangeProtection = true', ui)
        self.assertIn('Existing-cell protection is off.', ui)
        model = read('feature/calc/src/main/java/dev/fusion/calc/SheetViewModel.kt')
        section = model.split('fun acceptRange()', 1)[1].split('fun cancelRangePreview()', 1)[0]
        self.assertNotIn('onExport(', section)

    def test_real_tests_are_listed_without_claiming_execution(self):
        office = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text()
        for name in ('r62RangeContracts', 'r62WriterContracts', 'check_r62_range_reference.py',
                     'check_r62_writer_reference.py', 'r62-sheet-range.png', 'r62-writer-strike.png'):
            self.assertIn(name, office)
        self.assertNotIn('continue-on-error', office)


if __name__ == '__main__': unittest.main()
