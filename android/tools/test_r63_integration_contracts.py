"""Static guards for wiring and safety defaults; these do not execute Android UI."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


class R63IntegrationContracts(unittest.TestCase):
    def test_formula_fill_is_explicit_and_protection_resets_on_open(self):
        ui = read('feature/calc/src/main/java/dev/fusion/calc/SheetScreen.kt')
        self.assertIn('testTag("sheet-range-formula")', ui)
        self.assertIn('rangeOperation = OfficeSheetRangeEdit.Operation.FILL_FORMULA', ui)
        self.assertIn('rangeInput = selected; rangeProtection = true', ui)
        self.assertIn('testTag("sheet-range-formula-disclosure")', ui)
        self.assertIn('whole fill is refused', ui)
        self.assertIn('plan.formulasWritten', ui)
        self.assertIn('plan.formulasRemoved', ui)

    def test_formula_fill_preserves_snapshot_and_full_candidate_validation(self):
        source = read('format/ooxml/src/main/kotlin/dev/fusion/ooxml/OfficeSheetRangeEdit.kt')
        self.assertIn('draft === original && currentSelected == selected', source)
        self.assertIn('OfficeSheetDraft.restore(draft.revision + 1, cells)', source)
        self.assertIn('OfficeSheetDraft.MAX_COLUMNS', source)
        self.assertIn('OfficeSheetDraft.MAX_ROWS', source)
        self.assertIn('if (dc == 0 && dr == 0) return formula', source)
        self.assertNotIn('catch (', source)
        self.assertIn('Operation.FILL_LITERAL', source)
        self.assertIn('Operation.CLEAR_CONTENTS', source)

    def test_writer_restart_is_off_and_depends_on_numbering(self):
        ui = read('feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt')
        self.assertIn('var restartOrderedGroups by remember { mutableStateOf(false) }', ui)
        self.assertIn('numberedLines=false; strikeSpans=false; restartOrderedGroups=false', ui)
        self.assertIn('if (!it) restartOrderedGroups=false', ui)
        self.assertIn('value=restartOrderedGroups, enabled=numberedLines', ui)
        self.assertIn('DOCX readers may render numbering differently', ui)
        self.assertIn('fun EditorScreen(', ui)
        self.assertIn('fun EditorScreenWithStrike(', ui)
        self.assertIn('fun EditorScreenWithListRestart(', ui)

    def test_writer_old_overloads_and_explicit_override_are_present(self):
        writer = read('format/ooxml/src/main/kotlin/dev/fusion/ooxml/StructuredWriterDocx.kt')
        package = read('format/ooxml/src/main/kotlin/dev/fusion/ooxml/DocxPackage.kt')
        self.assertIn('restartOrderedGroups = false', writer)
        self.assertIn('require(!restartOrderedGroups || numberedLines)', writer)
        self.assertIn('restartOrderedGroups = false', package)
        self.assertIn('<w:lvlOverride w:ilvl="0"><w:startOverride w:val="1"/>', package)
        self.assertIn('level == 0', package)

    def test_candidate_device_cases_frames_and_readers_are_wired(self):
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text(encoding='utf-8')
        for item in ('r63FormulaFillContracts', 'r63WriterRestartContracts',
                     'check_r63_formula_reference.py', 'check_r63_writer_reference.py',
                     'test_r63_formula_reference', 'test_r63_writer_reference',
                     'r63-sheet-formula.png', 'r63-writer-restart.png'):
            self.assertIn(item, workflow)
        for file, name in (
            ('app/src/androidTest/java/dev/fusion/shell/R59SheetDeviceTest.kt', 'formulaFillAdjustsReferencesProtectsConflictsAndSupportsUndoAndExport'),
            ('app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt', 'writerListRestartReviewIsOptInResettableAndReachesRealPicker'),
            ('app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt', 'writerListRestartProviderReadbackContainsIndependentOverrides'),
        ):
            self.assertIn('@Test fun ' + name, read(file))
        self.assertNotIn('continue-on-error', workflow)


if __name__ == '__main__':
    unittest.main()
