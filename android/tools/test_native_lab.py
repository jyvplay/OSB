"""Pure admission/parser tests only. No fake adb, Gradle or Android execution."""
import json
from pathlib import Path
import tempfile
import unittest
import native_lab as lab

class NativeLabChecks(unittest.TestCase):
    def test_explicit_serial_and_consent(self):
        self.assertEqual('emulator-5554', lab.serial_checked('emulator-5554', True))
        for serial, consent in [('emulator-5554', False), ('', True), ('192.0.2.1:5555', True),
                                ('physical-id', True), ('emulator-5554; reboot', True), ('emulator-5554\n', True)]:
            with self.assertRaises(ValueError): lab.serial_checked(serial, consent)
    def test_font_restore_input_bounds(self):
        for value in ('null', '1', '1.0', '2.0', '0.85', '10.0'):
            self.assertEqual(value, lab.font_checked(value))
        for value in ('NaN', 'Infinity', '-1', '0', '11', '1; reboot', '1\n2', ''):
            with self.assertRaises(ValueError): lab.font_checked(value)
    def test_instrumentation_requires_exact_completion(self):
        good='OK (12 tests)\n\nINSTRUMENTATION_CODE: -1\n'
        self.assertEqual(12, lab.instrumentation_count(good, 12))
        for value in ('', 'INSTRUMENTATION_CODE: -1', 'OK (0 tests)\nINSTRUMENTATION_CODE: -1',
                      good.replace('-1','0'), good+'FAILURES!!!', good+'Process crashed', good+good):
            with self.assertRaises(ValueError): lab.instrumentation_count(value,12)
        with self.assertRaises(ValueError): lab.instrumentation_count(good,13)
    def test_apk_metadata_identity_path_and_split_guards(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory=Path(temporary)
            # A marker file tests path admission only, never presented as an APK.
            (directory/'marker.apk').write_text('not an APK; metadata parser input only')
            metadata={'applicationId':'dev.fusion.shell.pro','elements':[{'outputFile':'marker.apk'}]}
            def write(value): (directory/'output-metadata.json').write_text(json.dumps(value))
            write(metadata)
            self.assertEqual(directory/'marker.apk',lab.apk_from_metadata(directory,'dev.fusion.shell.pro'))
            with self.assertRaises(ValueError): lab.apk_from_metadata(directory,'dev.fusion.shell.play')
            for name in ('../marker.apk','/marker.apk','..\\marker.apk','missing.apk','marker.txt',''):
                write({**metadata,'elements':[{'outputFile':name}]})
                with self.assertRaises(ValueError): lab.apk_from_metadata(directory,'dev.fusion.shell.pro')
            for elements in ([],[{'outputFile':'marker.apk'}]*2):
                write({**metadata,'elements':elements})
                with self.assertRaises(ValueError): lab.apk_from_metadata(directory,'dev.fusion.shell.pro')
    def test_missing_or_truncated_capture_refused(self):
        for data in (b'', b'not a screenshot', b'\x89PNG\r\n\x1a\n', b'\0'*100):
            with self.assertRaises(ValueError): lab.png_dimensions(data)
    def test_titlebar_and_pending_copy_source_regressions(self):
        editor=(lab.ROOT/'feature/editor/src/main/java/dev/fusion/editor/EditorScreen.kt').read_text()
        self.assertIn('val pendingCopy = session.pendingCopy',editor)
        self.assertNotIn('session.pendingCopy.',editor)
        shell=(lab.ROOT/'app/src/main/java/dev/fusion/shell/MainActivity.kt').read_text()
        self.assertIn('heightIn(min = 48.dp).testTag("window-title-',shell)
        self.assertNotIn('48.dp else 32.dp',shell)
        self.assertNotIn('48.dp else 36.dp',shell)
        self.assertIn('DropdownMenu(expanded = actionsOpen',shell)
