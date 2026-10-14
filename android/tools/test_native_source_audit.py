"""Source/ownership regressions only; never substitutes for Android compilation."""
import copy
import json
from pathlib import Path
import unittest
import native_source_audit as audit

class NativeSourceAuditChecks(unittest.TestCase):
    def test_complete_scope_register_and_foundations_accounted_for(self):
        rows=json.loads((audit.ROOT/'docs/feature-register.json').read_text())
        self.assertEqual(231,len(rows)); self.assertEqual(36,len(audit.FOUNDATIONS))
        self.assertEqual(set(r['id'].split('-')[0] for r in rows),set(audit.FAMILIES))
    def test_current_source_report_is_reproducible(self):
        text,data=audit.report()
        self.assertEqual(text,(audit.ROOT/'docs/FULL-SPEC-REVIEW.md').read_text())
        self.assertEqual(data,(audit.ROOT/'docs/verification/r10-source-inventory.json').read_text())
    def test_removed_real_notes_dependency_is_detected(self):
        _,edges,exports,sources,packages=audit.inventory()
        edges[':app'].remove(':feature:notes')
        missing=audit.missing_edges(edges,exports,sources,packages)
        self.assertTrue(any(':feature:notes' in row['owners'] for row in missing))
    def test_implementation_is_not_blindly_exported(self):
        _,edges,exports,_,_=audit.inventory()
        self.assertIn(':core:documents',audit.reachable(':app',edges,exports))
        for dependencies in exports.values(): dependencies.discard(':core:documents')
        self.assertNotIn(':core:documents',audit.reachable(':app',edges,exports))
        self.assertIn(':core:documents',audit.reachable(':feature:editor',edges,exports))
    def test_backend_denial_replaces_dangerous_legacy_bodies(self):
        paths=['core/files/src/main/java/dev/fusion/files/SafRepository.kt','core/files/src/main/java/dev/fusion/files/TrashRepository.kt']
        for path in paths:
            source=(audit.ROOT/path).read_text()
            self.assertEqual(4,source.count('FileMutationPolicy.denied('))
            for forbidden in ('.delete()', '.renameTo(', 'openOutputStream(', 'writeIndex(', '.writeText('):
                self.assertNotIn(forbidden,source)
        self.assertNotIn('getOrDefault(mutableListOf())',(audit.ROOT/paths[1]).read_text())
    def test_database_upgrade_and_write_failures_are_not_hidden(self):
        source=(audit.ROOT/'core/storage/src/main/java/dev/fusion/storage/FusionDb.kt').read_text()
        self.assertNotIn('DROP TABLE',source)
        self.assertIn('throw android.database.sqlite.SQLiteException',source)
        self.assertEqual(2,source.count('!= -1L'))
    def test_notes_adapter_and_ui_do_not_use_unchecked_filename_io(self):
        adapter=(audit.ROOT/'feature/notes/src/main/java/dev/fusion/notes/NotesStore.kt').read_text()
        self.assertIn('NoteFiles(',adapter)
        for forbidden in ('readText(', 'writeText(', '"$name.md"'): self.assertNotIn(forbidden,adapter)
        ui=(audit.ROOT/'feature/notes/src/main/java/dev/fusion/notes/NotesScreen.kt').read_text()
        self.assertIn('NotesViewModel',ui); self.assertNotIn('store.',ui)
        model=(audit.ROOT/'feature/notes/src/main/java/dev/fusion/notes/NotesViewModel.kt').read_text()
        self.assertIn('fun refresh()',model)
        self.assertNotIn('refresh(initial',model)  # No public flag bypassing the busy barrier.

    def test_native_actions_and_capture_evidence_remain_real(self):
        source=(audit.ROOT/'app/src/main/java/dev/fusion/shell/MainActivity.kt').read_text()
        for required in ('CommandPalette(commands','WindowCommand.RestoreBounds','Move to workspace','closingNotesWindow','enableEdgeToEdge(','safeDrawingPadding()'):
            self.assertIn(required,source)
        capture=(audit.ROOT/'app/src/androidTest/java/dev/fusion/shell/VisualCaptureTest.kt').read_text()
        self.assertIn('uiAutomation.takeScreenshot()',capture)
        self.assertIn('useUnmergedTree=true',capture)

    def test_stale_picker_results_cannot_bypass_module_gates(self):
        source=(audit.ROOT/'app/src/main/java/dev/fusion/shell/MainActivity.kt').read_text()
        for name,module in [('openDoc','docs'),('openImage','image'),('openPdf','pdf'),('openPsd','psd'),('exportBackup','backup'),('importBackup','backup')]:
            section=source.split('private val '+name+' = ',1)[1].split('private val ',1)[0]
            self.assertIn('PilotPolicy.mayRun("'+module+'", acknowledgedExperiments)',section)
        files=(audit.ROOT/'feature/files/src/main/java/dev/fusion/filesui/FilesScreen.kt').read_text()
        section=files.split('LaunchedEffect(dir?.uri, refresh) {',1)[1]
        self.assertLess(section.index('entries = emptyList()'),section.index('repo.list(dir)'))

    def test_keystore_read_paths_do_not_regenerate_keys(self):
        source=(audit.ROOT/'core/security/src/main/java/dev/fusion/security/IndexKeys.kt').read_text()
        read=source.split('fun existing(): SecretKey {',1)[1].split('fun createForNewIndex',1)[0]
        self.assertNotIn('generateKey()',read)
        self.assertIn('throw IllegalStateException',read)
        self.assertIn('synchronized(creationLock)',source)
        vault=(audit.ROOT/'core/security/src/main/java/dev/fusion/security/VaultCrypto.kt').read_text()
        self.assertIn('Cipher.DECRYPT_MODE, key(create = false)',vault)
        self.assertNotIn('Keys never leave TEE',vault)

    def test_native_encryption_obligation_is_in_real_lab(self):
        native=(audit.ROOT/'app/src/androidTest/java/dev/fusion/shell/EncryptedIndexDeviceTest.kt').read_text()
        for operation in ('IndexKeys(alias)','store.save(documents,key,null)','session.unlock(store,freshKey)','keyStore.deleteEntry(alias)'):
            self.assertIn(operation,native)
        lab=(audit.ROOT/'tools/native_lab.py').read_text()
        self.assertIn("'EncryptedIndexDeviceTest'",lab)
        self.assertIn('return capture_binary(command, cwd=ROOT',lab)
        self.assertIn('from png_capture import dimensions as png_dimensions',lab)
