"""Static wiring/default checks only; these do not execute Compose or a provider."""
from pathlib import Path
import unittest

import r64_office_case_gate
import r25_evidence_gate

ROOT = Path(__file__).resolve().parents[1]


def source(path): return (ROOT / path).read_text(encoding='utf-8')


class R64IntegrationContracts(unittest.TestCase):
    def test_copy_defaults_and_explicit_review(self):
        ui = source('feature/calc/src/main/java/dev/fusion/calc/SheetScreen.kt')
        self.assertIn('copySource = selected; copyProtection = true; copySkipMissing = false', ui)
        for tag in ('sheet-copy-range', 'sheet-copy-source', 'sheet-copy-protect', 'sheet-copy-skip-missing',
                    'sheet-copy-preview', 'sheet-copy-review-text', 'sheet-copy-apply', 'sheet-copy-cancel'):
            self.assertIn(f'testTag("{tag}")', ui)
        self.assertIn('state.copyPreview == null', ui)
        self.assertIn('this does not save an external file', ui)

    def test_copy_snapshot_protection_and_history_are_wired(self):
        kernel = source('format/ooxml/src/main/kotlin/dev/fusion/ooxml/OfficeSheetRangeCopy.kt')
        model = source('feature/calc/src/main/java/dev/fusion/calc/SheetViewModel.kt')
        self.assertIn('val sourceCell = draft.cells[from]', kernel)
        self.assertIn('val old = draft.cells[to]', kernel)
        self.assertIn('draft === original && currentSelected == selected', kernel)
        self.assertIn('OfficeSheetDraft.restore(draft.revision + 1, cells)', kernel)
        self.assertIn('if (plan.changed == 0)', model)
        self.assertIn('push(undo, plan.original.cells)', model)
        self.assertIn('!plan.isCurrent(current.data.draft, current.selected)', model)

    def test_writer_option_is_default_off_and_old_entry_points_remain(self):
        ui = source('feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt')
        for name in ('EditorScreen(', 'EditorScreenWithStrike(', 'EditorScreenWithListRestart(', 'EditorScreenWithPipeTables('):
            self.assertIn('fun ' + name, ui)
        self.assertIn('var pipeTables by remember { mutableStateOf(false) }', ui)
        self.assertIn('restartOrderedGroups=false; pipeTables=false', ui)
        self.assertIn('consent.permits(doc,buffer.revision)', ui)
        self.assertIn('malformed blocks stay literal', ui)
        writer = source('format/ooxml/src/main/kotlin/dev/fusion/ooxml/StructuredWriterDocx.kt')
        self.assertIn('pipeTables = false', writer)
        self.assertIn('DocxPackage.Block.Paragraph(listOf(DocxPackage.Run(it)))', writer)
        self.assertIn('DocxPackage.Block.Table(rows)', writer)

    def test_csv_is_explicit_lossy_and_uses_existing_frozen_copy(self):
        ui = source('feature/calc/src/main/java/dev/fusion/calc/SheetScreen.kt')
        export = source('app/src/main/java/dev/fusion/shell/OfficeExportViewModel.kt')
        kernel = source('format/ooxml/src/main/kotlin/dev/fusion/ooxml/OfficeSheetCsv.kt')
        self.assertIn('fun SheetScreen(', ui)
        self.assertIn('fun SheetScreenWithCsv(', ui)
        self.assertIn('formulas, types and formatting are lost', ui)
        self.assertIn('not a universal CSV-injection guarantee', ui)
        self.assertIn('current.data.draft.revision == revision && !exportBusy', ui)
        self.assertIn('prepare("sheet-values-copy.csv", CopyDestinationContract.CSV_MIME', export)
        self.assertIn('OfficeSheetCsv.write(draft)', export)
        self.assertIn('FrozenExport(id, bytes, sources)', export)
        self.assertIn('VerifiedCopy.requireEmpty', export)
        self.assertIn('VerifiedCopy.write', export)
        self.assertIn('if (value is FormulaEval.CellValue.Text)', kernel)
        self.assertIn('MAX_OUTPUT_BYTES', kernel)
        self.assertIn('if (unsigned == \'"\'.code) byte(unsigned)', kernel)

    def test_csv_mime_addition_keeps_plain_and_office_mimes(self):
        picker = source('feature/editor/src/main/java/dev/fusion/editor/CopyDestinationActivity.kt')
        self.assertIn('const val CSV_MIME="text/csv"', picker)
        self.assertIn('setOf("text/plain", DOCX_MIME, XLSX_MIME, CSV_MIME)', picker)
        self.assertIn('if (input.mimeType != "text/plain")', picker)
        activity = source('app/src/main/java/dev/fusion/shell/MainActivity.kt')
        self.assertIn('SheetScreenWithCsv(', activity)
        self.assertIn('officeExportModel.sheetCsv(', activity)
        self.assertIn('officeExportModel.sheet(draft,', activity)

    def test_all_real_device_names_are_required_not_just_counted(self):
        cases = r25_evidence_gate.R64_OFFICE_CASES
        self.assertEqual(len(cases), 4)
        self.assertTrue(cases <= r64_office_case_gate.EXPECTED)
        self.assertEqual(len(r64_office_case_gate.EXPECTED), 37)
        for identity in cases:
            cls, method = identity.rsplit('.', 1)
            file = 'app/src/androidTest/java/dev/fusion/shell/' + cls.rsplit('.', 1)[1] + '.kt'
            self.assertIn('@Test fun ' + method + '()', source(file))

    def test_device_cases_include_actual_picker_provider_and_frame_routes(self):
        sheet = source('app/src/androidTest/java/dev/fusion/shell/R59SheetDeviceTest.kt')
        writer = source('app/src/androidTest/java/dev/fusion/shell/R54OfficeDeviceTest.kt')
        for item in ('sheet-copy-apply', 'sheet-undo', 'sheet-redo', 'FormulaXlsx.read(bytes)',
                     'sheet-csv-confirm', 'CopyDestinationContract.CSV_MIME', 'Until.hasObject(documentsUi)',
                     'CSV refused', 'r64-sheet-copy', 'r64-sheet-csv'):
            self.assertIn(item, sheet)
        for item in ('writer-pipe-table-option', 'pipeTables = true', 'zipPart(destination, "word/document.xml")',
                     'assertArrayEquals(source.toByteArray', 'r64-writer-table'):
            self.assertIn(item, writer)

    def test_ci_requires_readers_cases_and_actual_frame_transport(self):
        workflow = (ROOT.parent / '.github/workflows/android-r54-office.yml').read_text(encoding='utf-8')
        for name in ('r64SheetCopyContracts', 'r64SheetCsvContracts', 'r64WriterTableContracts',
                     'check_r64_sheet_copy_reference.py', 'check_r64_formula_caches.py',
                     'check_r64_writer_reference.py', 'check_r64_csv_reference.py',
                     'test_r64_sheet_reference', 'test_r64_formula_caches', 'test_r64_writer_reference',
                     'test_r64_csv_reference', 'test_r64_office_case_gate', 'test_r64_runners',
                     'r64_office_case_gate.py', 'r64-sheet-copy.png', 'r64-sheet-csv.png', 'r64-writer-table.png'):
            self.assertIn(name, workflow)
        self.assertNotIn('continue-on-error', workflow)
        self.assertEqual(workflow.count('bash tools/run_gradle_ci.sh device'), 1)
        self.assertLess(workflow.index('r64_office_case_gate.py'), workflow.index('Upload actual diagnostics'))


if __name__ == '__main__': unittest.main()
