package dev.fusion.shell

import dev.fusion.commands.Command
import dev.fusion.find.CommandPalette
import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.fusion.settings.AppearancePreferences
import dev.fusion.settings.DesktopLayout
import dev.fusion.designsystem.FusionTheme
import dev.fusion.settingsui.AppearanceControls
import dev.fusion.windowing.InputPresentation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import dev.fusion.editor.EditorViewModel
import dev.fusion.a11y.AccessibilityScreen
import dev.fusion.alerts.AlertsScreen
import dev.fusion.alerts.FusionNotificationListener
import dev.fusion.archive.ZipGuard
import dev.fusion.backup.BackupEngine
import dev.fusion.backup.BackupScreen
import dev.fusion.beam.BeamDiscovery
import dev.fusion.beam.BeamScreen
import dev.fusion.browser.BrowserScreen
import dev.fusion.calc.CalcScreen
import dev.fusion.calc.CalculatorViewModel
import dev.fusion.capabilities.CapabilityProbe
import dev.fusion.capture.CaptureScreen
import dev.fusion.capture.ScreenRecordService
import dev.fusion.cast.dlna.DlnaScreen
import dev.fusion.continuity.ContinuityScreen
import dev.fusion.editor.EditorScreen
import dev.fusion.editor.CopyDestinationContract
import dev.fusion.filesui.FilesScreen
import dev.fusion.files.SafRepository
import dev.fusion.files.TrashRepository
import dev.fusion.format.image.ImageOps
import dev.fusion.format.pdf.PdfScreen
import dev.fusion.hw.HwProbe
import dev.fusion.messages.MessagesScreen
import dev.fusion.notes.NotesScreen
import dev.fusion.notes.NotesViewModel
import dev.fusion.ooxml.Ooxml
import dev.fusion.perf.MemoryProbe
import dev.fusion.perf.ThermalProbe
import dev.fusion.settings.SettingsGraph
import dev.fusion.settingsui.SettingsScreen
import dev.fusion.terminal.LocalShell
import dev.fusion.terminal.TerminalScreen
import dev.fusion.together.TogetherScreen
import dev.fusion.vault.VaultScreen
import dev.fusion.model.PilotAccess
import dev.fusion.model.PilotPolicy
import dev.fusion.capture.RecorderState
import dev.fusion.windowing.WindowDensity
import dev.fusion.windowing.Bounds
import dev.fusion.automation.AutomationScreen
import dev.fusion.lens.LensScreen
import dev.fusion.windowing.DesktopState
import dev.fusion.windowing.ShellWindow
import dev.fusion.windowing.Tile
import dev.fusion.windowing.WindowCommand
import dev.fusion.windowing.WindowEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val bg:Color @Composable get()=MaterialTheme.colorScheme.background
private val panel:Color @Composable get()=MaterialTheme.colorScheme.surface
private val border:Color @Composable get()=MaterialTheme.colorScheme.outline
private val mint:Color @Composable get()=MaterialTheme.colorScheme.primary

private val MODULES = PilotPolicy.modules.map { it.id }

class MainActivity : ComponentActivity() {

    private val repository by lazy { SafRepository(this) }
    private val prefs by lazy { getSharedPreferences("fusion", MODE_PRIVATE) }
    private val settingsGraph by lazy { SettingsGraph(this) }
    private val notesModel by lazy { ViewModelProvider(this)[NotesViewModel::class.java] }
    private val localShell by lazy { LocalShell(this) }
    private val beamDiscovery by lazy { BeamDiscovery(this) }
    private val backupEngine by lazy { BackupEngine(this) }
    private val trashRepository by lazy { TrashRepository(this) }

    private val desktopModel by lazy { ViewModelProvider(this)[DesktopSessionModel::class.java] }
    private val desktop get() = desktopModel.desktop
    private var acknowledgedExperiments by mutableStateOf(emptySet<String>())
    private var pilotIntro by mutableStateOf(false)
    private var resetLayoutPrompt by mutableStateOf(false)
    private var treeUri by mutableStateOf<Uri?>(null)
    private var message by mutableStateOf("Local first. No app telemetry configured; main shell, providers and host are not anonymous.")
    private val calculatorModel by lazy { ViewModelProvider(this)[CalculatorViewModel::class.java] }
    private val editorModel by lazy { ViewModelProvider(this)[EditorViewModel::class.java] }
    private var closingEditorWindow by mutableStateOf<Long?>(null)
    private var closingNotesWindow by mutableStateOf<Long?>(null)
    private var pendingBackupPassword: CharArray? = null
    private var backupStatus by mutableStateOf("No export or restore run yet.")
    private var pdfUri by mutableStateOf<Uri?>(null)
    private var imageSummary by mutableStateOf("No image opened.")
    private var docSummary by mutableStateOf("No document opened.")
    private var psdUri by mutableStateOf<Uri?>(null)
    private var incomingShare by mutableStateOf<String?>(null)
    private var externalDisplayLabel by mutableStateOf<String?>(null)
    private var presentation: DesktopPresentation? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    // ---- activity results -------------------------------------------------

    private val chooseFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            treeUri = uri
            prefs.edit().putString("tree", uri.toString()).apply()
            message = "Read-only folder access retained. Only this tree is visible."
        } catch (e: SecurityException) {
            message = "Folder permission was not retained: ${e.localizedMessage}"
        }
    }

    private val saveCopy = registerForActivityResult(CopyDestinationContract()) { result ->
        editorModel.completeCopy(result)
    }

    private val captureNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && PilotPolicy.mayRun("capture", acknowledgedExperiments)) captureConsent.launch(ScreenRecordService.consentIntent(this))
        else message = "Capture requires a visible stop notification and session consent. Nothing recorded."
    }

    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null && PilotPolicy.mayRun("capture", acknowledgedExperiments)) {
            runCatching { ScreenRecordService.start(this, result.resultCode, data) }
                .onFailure { message = "Capture did not start: ${it.javaClass.simpleName}" }
        } else {
            message = "Screen capture consent was declined. Nothing recorded."
        }
    }

    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri == null || password == null || !PilotPolicy.mayRun("backup", acknowledgedExperiments)) {
            password?.fill('\u0000'); backupStatus = "Backup unavailable or cancelled; no password retained."; return@registerForActivityResult
        }
        lifecycleScope.launch {
            try { backupEngine.export(uri, password).fold(
                onSuccess = {
                    backupStatus = "Exported ${it.fileCount} note(s): ${it.plainBytes}B -> ${it.sealedBytes}B " +
                        "sealed, sha256 ${it.sha256}..."
                },
                onFailure = { backupStatus = "Export failed: ${it.message}" }
            )
            } finally { password.fill('\u0000') }
        }
    }

    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri == null || password == null || !PilotPolicy.mayRun("backup", acknowledgedExperiments)) {
            password?.fill('\u0000'); backupStatus = "Backup unavailable or cancelled; no password retained."; return@registerForActivityResult
        }
        lifecycleScope.launch {
            try { backupEngine.restore(uri, password).fold(
                onSuccess = { backupStatus = "Restored $it note file(s). Existing files were kept." },
                onFailure = { backupStatus = "Restore failed (wrong password or not a Fusion backup): ${it.message}" }
            )
            } finally { password.fill('\u0000') }
        }
    }

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && PilotPolicy.mayRun("pdf-preview", acknowledgedExperiments)) pdfUri = uri
    }

    private val openPsd = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && PilotPolicy.mayRun("psd", acknowledgedExperiments)) psdUri = uri
    }

    private val openImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // A restored/stale picker result is not permission to bypass an unavailable module.
        if (uri == null || !PilotPolicy.mayRun("image", acknowledgedExperiments)) return@registerForActivityResult
        lifecycleScope.launch {
            ImageOps.probe(this@MainActivity, uri).fold(
                onSuccess = { imageSummary = "${it.mime} ${it.width}x${it.height}, ${it.bytes} bytes on disk." },
                onFailure = { imageSummary = "Cannot decode on this device: ${it.message}" }
            )
        }
    }

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || !PilotPolicy.mayRun("docs", acknowledgedExperiments)) return@registerForActivityResult
        lifecycleScope.launch { inspectDocument(uri) }
    }

    // ---- lifecycle ---------------------------------------------------------

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // [U] verify on API 34/36/37: system bars, cutouts and IME inset reflow on real UI.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        editorModel // Begin recovery before file/picker events.
        desktopModel // Recover geometry off main before accepting window commands.
        pilotIntro = !prefs.getBoolean("pilot-notice-v2", false)
        treeUri = prefs.getString("tree", null)?.let(Uri::parse)?.takeIf { selected ->
            contentResolver.persistedUriPermissions.any { it.uri == selected && it.isReadPermission }
        }
        handleShare(intent)
        setContent {
            val preferences by settingsGraph.snapshots.collectAsState()
            val appearance=AppearancePreferences.read(preferences)
            LaunchedEffect(appearance.palette) {
                val light=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if(appearance.palette.dark) 0 else light,light)
            }
            FusionTheme(palette=appearance.palette) {
                Surface(Modifier.fillMaxSize(), color = bg) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding().semantics {testTagsAsResourceId=true}) { ShellScreen() }
                }
            }
        }
    }

    override fun onDestroy() {settingsGraph.close();super.onDestroy()}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            incomingShare = intent.getStringExtra(Intent.EXTRA_TEXT)?.take(64 * 1024)
            if (incomingShare != null) {
                openModule("continuity")
                message = "Received shared text from another app."
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // SC-08 / CA-14: granting Files access is NOT consent to publish a tree on LAN.
        // Local sharing remains disabled until a per-file token/session and stop indicator exist.
        val manager = getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = syncExternalDisplay()
            override fun onDisplayRemoved(displayId: Int) = syncExternalDisplay()
            override fun onDisplayChanged(displayId: Int) = syncExternalDisplay()
        }
        displayListener = listener
        manager.registerDisplayListener(listener, null)
        syncExternalDisplay()
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { editorModel.flush() }
        lifecycleScope.launch { calculatorModel.flush() }
        lifecycleScope.launch { desktopModel.flush() }
        // A Presentation is owned by this Activity; tearing it down avoids a leaked window.
        displayListener?.let { getSystemService(DisplayManager::class.java).unregisterDisplayListener(it) }
        displayListener = null
        runCatching { presentation?.dismiss() }
        presentation = null
        externalDisplayLabel = null
    }

    /** Attaches or detaches the secondary-display window to match reality. */
    private fun syncExternalDisplay() {
        val manager = getSystemService(DisplayManager::class.java)
        val target = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()

        if (target == null) {
            runCatching { presentation?.dismiss() }
            presentation = null
            externalDisplayLabel = null
            return
        }
        if (presentation?.display?.displayId == target.displayId && presentation?.isShowing == true) return

        runCatching { presentation?.dismiss() }
        presentation = null

        val created = DesktopPresentation(this, target) {
            val values by settingsGraph.snapshots.collectAsState()
            FusionTheme(palette=AppearancePreferences.read(values).palette) {ExternalDesktopScreen(desktop, externalDisplayLabel ?: target.name)}
        }
        try {
            created.show()
            presentation = created
            externalDisplayLabel = "${target.name} (${target.displayId})"
            message = "External display connected: ${target.name}. Session mirror is live."
        } catch (e: Exception) {
            presentation = null
            externalDisplayLabel = null
            message = "Could not open a window on the external display: ${e.message}"
        }
    }

    // ---- window plumbing ---------------------------------------------------

    private fun dispatch(command: WindowCommand) {
        if (command is WindowCommand.Close && desktop.windows.any { it.id == command.id && it.module == "editor" } &&
            editorModel.state.value.session.documents.any { it.dirty }) {
            closingEditorWindow = command.id
            return
        }
        if(command is WindowCommand.Close && desktop.windows.any { it.id==command.id && it.module=="notes" } && notesModel.state.value.dirty) {
            closingNotesWindow=command.id
            return
        }
        desktopModel.dispatch(command)
    }

    private fun titleFor(module: String) = PilotPolicy.module(module).title

    private fun openModule(module: String) {
        if (desktopModel.loading || desktopModel.blocked) { message = desktopModel.status; return }
        val existing = desktop.windows.firstOrNull { it.module == module && it.workspace == desktop.workspace }
        if (existing != null) {
            dispatch(WindowCommand.Focus(existing.id)); return
        }
        if (desktop.windows.size >= WindowEngine.MAX_WINDOWS) { message = "Pilot window limit reached; close a window first."; return }
        dispatch(WindowCommand.Open(module, titleFor(module), Bounds(0f, 0f, 600f, 430f), java.util.UUID.randomUUID().toString()))
    }

    private suspend fun inspectDocument(uri: Uri) = withContext(Dispatchers.IO) {
        val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        } ?: uri.lastPathSegment.orEmpty()

        docSummary = runCatching {
            when {
                Ooxml.isDocx(name) ->
                    contentResolver.openInputStream(uri)!!.use { Ooxml.extractDocxText(it) }
                        .fold({ text -> "DOCX text (${text.length} chars):\n\n" + text.take(4000) },
                            { e -> "DOCX extract failed: ${e.message}" })

                Ooxml.isXlsx(name) ->
                    contentResolver.openInputStream(uri)!!.use { Ooxml.extractXlsxSheet(it) }
                        .fold({ sheet ->
                            "XLSX ${sheet.name}: ${sheet.rows.size} row(s)\n\n" +
                                sheet.rows.take(40).joinToString("\n") { row -> row.joinToString(" | ") }
                        }, { e -> "XLSX extract failed: ${e.message}" })

                name.endsWith(".zip", true) -> {
                    val tmp = File(cacheDir, "inspect.zip")
                    contentResolver.openInputStream(uri)!!.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    val result = ZipGuard.list(tmp).fold(
                        { entries ->
                            "ZIP: ${entries.size} entr(ies), guards passed\n\n" +
                                entries.take(60).joinToString("\n") { e ->
                                    (if (e.dir) "[dir]  " else "[file] ") + e.name + "  " + e.size + "B"
                                }
                        },
                        { e -> "ZIP rejected: ${e.message}" }
                    )
                    tmp.delete()
                    result
                }

                else -> "Unsupported here: $name. This window handles .docx, .xlsx and .zip only."
            }
        }.getOrElse { "Could not read the document: ${it.message}" }
    }

    // ---- UI ----------------------------------------------------------------

    @Composable
    private fun ShellScreen() {
        val preferences by settingsGraph.snapshots.collectAsState()
        val appearance=AppearancePreferences.read(preferences)
        val imeVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
        var controlVisible by remember {mutableStateOf(false)}
        var paletteVisible by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
        LaunchedEffect(desktopModel.loading, incomingShare) {
            if (!desktopModel.loading && incomingShare != null) openModule("continuity")
        }

        if (pilotIntro) AlertDialog(onDismissRequest = {}, title = { Text("Fusion pilot — evaluation only") },
            text = { Text("This is not a release-ready desktop. Use disposable documents and test accounts only. Hardware, accessibility and protocol gates are not passed. No billing or license entitlement is implied. No telemetry is sent.") },
            confirmButton = { TextButton(onClick = {
                prefs.edit().putBoolean("pilot-notice-v2", true).apply(); pilotIntro = false
            }) { Text("I understand — evaluation only") } })
        if (resetLayoutPrompt) AlertDialog(onDismissRequest = { resetLayoutPrompt = false }, title = { Text("Reset only the window layout?") },
            text = { Text("All windows will close. Editor drafts remain. Old layout bytes are retained locally for inspection; this does not reset permissions or erase files.") },
            confirmButton = { TextButton(onClick = { desktopModel.resetLayout(); resetLayoutPrompt = false }) { Text("Reset layout") } },
            dismissButton = { TextButton(onClick = { resetLayoutPrompt = false }) { Text("Cancel") } })
        closingEditorWindow?.let { id ->
            AlertDialog(onDismissRequest = { closingEditorWindow = null },
                title = { Text("Keep unsaved editor drafts?") },
                text = { Text("Closing hides the editor. Drafts will be kept locally, not saved to the original documents. Reopen Editor to continue.") },
                confirmButton = { TextButton(onClick = {
                    lifecycleScope.launch {
                        if (editorModel.flush()) {
                            desktopModel.dispatch(WindowCommand.Close(id))
                            closingEditorWindow = null
                        }
                    }
                }) { Text("Keep drafts and close") } },
                dismissButton = { TextButton(onClick = { closingEditorWindow = null }) { Text("Keep editing") } })
        }
        closingNotesWindow?.let { id ->
            AlertDialog(onDismissRequest={ closingNotesWindow=null },title={ Text("Close with an unsaved note?") },
                text={ Text("The latest acknowledged draft checkpoint can be recovered; recent unconfirmed edits can be lost. Closing this window does not save Markdown.") },
                confirmButton={ TextButton(onClick={ desktopModel.dispatch(WindowCommand.Close(id)); closingNotesWindow=null }) { Text("Hide note window") } },
                dismissButton={ TextButton(onClick={ closingNotesWindow=null }) { Text("Keep editing") } })
        }
        if(controlVisible) AlertDialog(onDismissRequest={controlVisible=false},
            title={Text("Control panel")}, text={
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    AppearanceControls(settingsGraph,"control")
                    Text("Workspaces")
                    Row(Modifier.horizontalScroll(rememberScrollState())) {repeat(WindowEngine.WORKSPACES) {ws->
                        OutlinedButton(onClick={dispatch(WindowCommand.SwitchWorkspace(ws))},modifier=Modifier.testTag("control-workspace-$ws")) {Text("${ws+1}")}
                    }}
                    Text("Main shell has networking permission. Appearance is not anonymity; the offline privacy compartment is a separate APK.")
                    TextButton(onClick={openModule("settings");controlVisible=false}) {Text("Open full Fusion settings")}
                    TextButton(onClick={runCatching {startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))}.onFailure {message="Android settings unavailable."}}) {Text("Android settings…")}
                }
            },confirmButton={TextButton(onClick={controlVisible=false},modifier=Modifier.testTag("control-done")) {Text("Done")}})
        Column(
            Modifier.fillMaxSize().focusRequester(focusRequester).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.isCtrlPressed && event.isAltPressed) {
                    val active = desktop.activeId
                    val sw = desktop.screenWidth; val sh = desktop.screenHeight
                    val step = 16f
                    val delta = when (event.key) {
                        Key.DirectionLeft -> -step to 0f; Key.DirectionRight -> step to 0f
                        Key.DirectionUp -> 0f to -step; Key.DirectionDown -> 0f to step
                        else -> null
                    }
                    when {
                        delta != null && active != null && imeVisible -> {message="Dismiss the keyboard before moving/resizing windows.";return@onPreviewKeyEvent true}
                        delta != null && active != null -> {
                            dispatch(if (event.isShiftPressed) WindowCommand.Resize(active, delta.first, delta.second, sw, sh)
                                else WindowCommand.Move(active, delta.first, delta.second, sw, sh)); return@onPreviewKeyEvent true
                        }
                        event.key == Key.Tab -> { dispatch(WindowCommand.FocusNext(event.isShiftPressed)); return@onPreviewKeyEvent true }
                        event.key == Key.M && active != null -> { dispatch(WindowCommand.Minimize(active)); return@onPreviewKeyEvent true }
                        event.key == Key.W && active != null -> { dispatch(WindowCommand.Close(active)); return@onPreviewKeyEvent true }
                        event.key == Key.PageUp -> { dispatch(WindowCommand.SwitchWorkspace(desktop.workspace - 1)); return@onPreviewKeyEvent true }
                        event.key == Key.PageDown -> { dispatch(WindowCommand.SwitchWorkspace(desktop.workspace + 1)); return@onPreviewKeyEvent true }
                        event.key == Key.R && active != null -> { dispatch(WindowCommand.RestoreBounds(active)); return@onPreviewKeyEvent true }
                        event.isShiftPressed && event.key in listOf(Key.One,Key.Two,Key.Three) && active != null -> {
                            val workspace=when(event.key) { Key.One -> 0; Key.Two -> 1; else -> 2 }
                            dispatch(WindowCommand.MoveToWorkspace(active,workspace)); return@onPreviewKeyEvent true
                        }
                        event.key in listOf(Key.One, Key.Two, Key.Zero) && active != null -> {
                            val tile = when(event.key) { Key.One -> Tile.LEFT; Key.Two -> Tile.RIGHT; else -> Tile.MAXIMIZE }
                            dispatch(WindowCommand.TileWindow(active, tile, sw, sh)); return@onPreviewKeyEvent true
                        }
                    }
                }
                when {
                    event.isCtrlPressed && event.key == Key.K -> { paletteVisible = true; true }
                    event.key == Key.Escape && paletteVisible -> { paletteVisible = false; true }
                    else -> false
                }
            }.focusable()
        ) {
            DesktopTopBar(appearance,desktop.windows.find {it.id==desktop.activeId}?.title ?: "Fusion",desktop.workspace,
                onFind={paletteVisible=true},onSafety={openModule("pilot")},onControl={controlVisible=true})

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(bg).testTag("desktop-canvas")) {
                val sw = maxWidth.value
                val sh = maxHeight.value
                val scale = LocalDensity.current.density
                LaunchedEffect(sw, sh, desktopModel.loading,imeVisible) {
                    // Keyboard presentation is transient, not a new persistent desktop geometry.
                    if (!desktopModel.loading && !imeVisible) dispatch(WindowCommand.SetViewport(sw, sh))
                }

                DesktopWallpaper(appearance.layout==DesktopLayout.ART)

                desktop.windows.filter { it.workspace == desktop.workspace && !it.minimized }
                    .sortedBy { it.z }.forEach { w -> key(w.id) {
                        val adjusted = InputPresentation.fit(w.bounds,sw.coerceAtLeast(1f),sh.coerceAtLeast(1f),desktop.density,imeVisible)
                        val windowFocus = remember { FocusRequester() }
                        var windowHasFocus by remember { mutableStateOf(false) }
                        LaunchedEffect(desktop.activeId) {
                            if (desktop.activeId == w.id && !windowHasFocus) runCatching { windowFocus.requestFocus() }
                        }
                        Column(
                            Modifier.offset(adjusted.x.dp, adjusted.y.dp)
                                .size(adjusted.width.dp, adjusted.height.dp)
                                .zIndex(w.z.toFloat())
                                .clip(RoundedCornerShape(if(appearance.layout==DesktopLayout.ART) 14.dp else 4.dp))
                                .border(1.dp, if (desktop.activeId == w.id) mint else border,RoundedCornerShape(if(appearance.layout==DesktopLayout.ART) 14.dp else 4.dp))
                                .background(panel)
                                .focusRequester(windowFocus)
                                .onFocusChanged {
                                    windowHasFocus=it.hasFocus
                                    if (it.hasFocus && desktop.activeId != w.id) dispatch(WindowCommand.Focus(w.id))
                                }
                                .focusable()
                                .semantics {
                                    paneTitle = w.title
                                    customActions = listOf(
                                        CustomAccessibilityAction("Focus") { dispatch(WindowCommand.Focus(w.id)); true },
                                        CustomAccessibilityAction("Move left") { if(imeVisible) false else {dispatch(WindowCommand.Move(w.id, -16f, 0f, sw, sh)); true} },
                                        CustomAccessibilityAction("Move right") { if(imeVisible) false else {dispatch(WindowCommand.Move(w.id, 16f, 0f, sw, sh)); true} },
                                        CustomAccessibilityAction("Move up") { if(imeVisible) false else {dispatch(WindowCommand.Move(w.id, 0f, -16f, sw, sh)); true} },
                                        CustomAccessibilityAction("Move down") { if(imeVisible) false else {dispatch(WindowCommand.Move(w.id, 0f, 16f, sw, sh)); true} },
                                        CustomAccessibilityAction("Increase width") { if(imeVisible) false else {dispatch(WindowCommand.Resize(w.id, 16f, 0f, sw, sh)); true} },
                                        CustomAccessibilityAction("Decrease width") { if(imeVisible) false else {dispatch(WindowCommand.Resize(w.id, -16f, 0f, sw, sh)); true} },
                                        CustomAccessibilityAction("Increase height") { if(imeVisible) false else {dispatch(WindowCommand.Resize(w.id, 0f, 16f, sw, sh)); true} },
                                        CustomAccessibilityAction("Decrease height") { if(imeVisible) false else {dispatch(WindowCommand.Resize(w.id, 0f, -16f, sw, sh)); true} },
                                        CustomAccessibilityAction("Maximize") { if(imeVisible) false else {dispatch(WindowCommand.TileWindow(w.id, Tile.MAXIMIZE, sw, sh)); true} },
                                        CustomAccessibilityAction("Restore previous size") { if(imeVisible) false else {dispatch(WindowCommand.RestoreBounds(w.id)); true} },
                                        CustomAccessibilityAction("Move to next workspace") { dispatch(WindowCommand.MoveToWorkspace(w.id,(w.workspace+1)%WindowEngine.WORKSPACES)); true },
                                        CustomAccessibilityAction("Minimize") { dispatch(WindowCommand.Minimize(w.id)); true },
                                        CustomAccessibilityAction("Close") { dispatch(WindowCommand.Close(w.id)); true }
                                    )
                                }
                                // Observe press without consuming child input or merging their pane semantics.
                                .pointerInput(w.id) {
                                    awaitPointerEventScope {
                                        while(true) {
                                            val event=awaitPointerEvent(PointerEventPass.Initial)
                                            if(event.changes.any { it.pressed && !it.previousPressed } && desktop.activeId!=w.id)
                                                dispatch(WindowCommand.Focus(w.id))
                                        }
                                    }
                                }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("window-title-${w.module}").background(MaterialTheme.colorScheme.surfaceVariant)
                                    .pointerInput(w.id, sw, sh,imeVisible) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            if(!imeVisible) dispatch(WindowCommand.Move(w.id, drag.x / scale, drag.y / scale, sw, sh))
                                        }
                                    }.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if(appearance.layout==DesktopLayout.ART) {
                                    TitleAction("X",description="Close window",tint=Color(0xffb42318)) {dispatch(WindowCommand.Close(w.id))}
                                    TitleAction("_",description="Minimize",tint=MaterialTheme.colorScheme.onSurfaceVariant) {dispatch(WindowCommand.Minimize(w.id))}
                                    TitleAction("+",description="Maximize or restore",enabled=!imeVisible) {
                                        if(w.restoreBounds!=null) dispatch(WindowCommand.RestoreBounds(w.id))
                                        else dispatch(WindowCommand.TileWindow(w.id,Tile.MAXIMIZE,sw,sh))
                                    }
                                }
                                Text(w.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                // Three 48dp controls leave a title/drag region even at 240dp.
                                // Density changes layout, never which window actions are available.
                                var actionsOpen by remember(w.id) { mutableStateOf(false) }
                                Box {
                                    TitleAction("⋮", Modifier.testTag("window-actions-${w.module}")) { actionsOpen = true }
                                    DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                                        listOf("Tile left" to Tile.LEFT, "Tile right" to Tile.RIGHT, "Maximize" to Tile.MAXIMIZE).forEach { (label, tile) ->
                                            DropdownMenuItem(text = { Text(if(WindowEngine.canTile(desktop,tile)) label else "$label — viewport too small") },
                                                enabled=!imeVisible && WindowEngine.canTile(desktop,tile), onClick = {
                                                actionsOpen = false
                                                dispatch(WindowCommand.TileWindow(w.id, tile, sw, sh))
                                            })
                                        }
                                        DropdownMenuItem(text={ Text("Restore previous size") },enabled=!imeVisible && w.restoreBounds!=null,onClick={
                                            actionsOpen=false; dispatch(WindowCommand.RestoreBounds(w.id))
                                        })
                                        repeat(WindowEngine.WORKSPACES) { workspace ->
                                            DropdownMenuItem(text={ Text("Move to workspace ${workspace+1}") },enabled=w.workspace!=workspace,onClick={
                                                actionsOpen=false; dispatch(WindowCommand.MoveToWorkspace(w.id,workspace))
                                                message="Moved ${w.title} to workspace ${workspace+1}."
                                            })
                                        }
                                    }
                                }
                                if(appearance.layout==DesktopLayout.BUSINESS) {
                                    TitleAction("_") { dispatch(WindowCommand.Minimize(w.id)) }
                                    TitleAction("X") { dispatch(WindowCommand.Close(w.id)) }
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                ModuleContent(w.module)
                                Text(
                                    "/",
                                    Modifier.align(Alignment.BottomEnd).pointerInput(w.id, sw, sh,imeVisible) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            if(!imeVisible) dispatch(WindowCommand.Resize(w.id, drag.x / scale, drag.y / scale, sw, sh))
                                        }
                                    }.padding(6.dp),
                                    color = mint
                                )
                            }
                        }
                    } }

                if (paletteVisible) {
                    val commands=MODULES.map { module ->
                        Command("module:$module",titleFor(module),"Module • ${PilotPolicy.module(module).boundary}",listOf(module))
                    } + desktop.windows.map { window ->
                        Command("window:${window.id}","${if(window.minimized) "Restore" else "Focus"}: ${window.title}",
                            "Open window • workspace ${window.workspace+1}",listOf(window.module))
                    } + (0 until WindowEngine.WORKSPACES).map { workspace ->
                        Command("workspace:$workspace","Switch to workspace ${workspace+1}","Desktop workspace")
                    }
                    CommandPalette(commands,onDismiss={ paletteVisible=false },onSelect={ id ->
                        when {
                            id.startsWith("module:") -> id.removePrefix("module:").takeIf { it in MODULES }?.let(::openModule)
                            id.startsWith("window:") -> id.removePrefix("window:").toLongOrNull()?.let { dispatch(WindowCommand.Focus(it)) }
                            id.startsWith("workspace:") -> id.removePrefix("workspace:").toIntOrNull()?.takeIf { it in 0 until WindowEngine.WORKSPACES }
                                ?.let { dispatch(WindowCommand.SwitchWorkspace(it)) }
                        }
                        paletteVisible=false
                    })
                }
            }

            val recording by RecorderState.recording.collectAsState()
            if (recording) Row(Modifier.fillMaxWidth().background(Color(0xFF6E1822)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recording selected screen/app • local MP4 • AVC • hardware/latency unmeasured", Modifier.weight(1f), color = Color.White)
                Button(onClick = { ScreenRecordService.stop(this@MainActivity) }) { Text("Stop recording") }
            }
            if (desktopModel.loading || desktopModel.blocked) {
                Text(desktopModel.status, Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite })
                if (desktopModel.blocked) Button(onClick = { resetLayoutPrompt = true }) { Text("Reset window layout…") }
            }
            if(!imeVisible) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                desktop.windows.filter { it.workspace == desktop.workspace }.forEach { w ->
                    OutlinedButton(onClick = { dispatch(WindowCommand.Focus(w.id)) }) {
                        Text((if (w.minimized) "Restore: " else "Focus: ") + w.title)
                    }
                }
            }
            Text(
                message, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if(imeVisible) 1 else 2
            )

            DesktopShelf(MODULES.map {module->ShelfApp(module,module.replaceFirstChar {it.uppercase()},
                desktop.windows.any {it.module==module},desktop.windows.any {it.module==module && it.id==desktop.activeId && !it.minimized})},
                appearance,imeVisible,!desktopModel.loading && !desktopModel.blocked,::openModule)

        }
    }

    @Composable
    private fun TitleAction(label: String, modifier: Modifier = Modifier, description:String?=null,tint:Color=mint,enabled:Boolean=true,onClick: () -> Unit) {
        val name = description ?: when(label) { "⋮" -> "Window actions"; "L" -> "Tile left"; "R" -> "Tile right"; "[]" -> "Maximize"; "_" -> "Minimize"; "X" -> "Close window"; else -> label }
        TextButton(onClick = onClick, enabled=enabled,contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
            modifier = modifier.width(48.dp).heightIn(min = 48.dp).semantics { contentDescription = name }) { Text(label, color = tint, fontSize = 12.sp) }
    }

    @Composable
    private fun ModuleContent(module: String) {
        val policy = PilotPolicy.module(module)
        if (!PilotPolicy.mayRun(module, acknowledgedExperiments)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(policy.title, style = MaterialTheme.typography.titleLarge)
                Text(if (policy.access == PilotAccess.BLOCKED) "Unavailable in this pilot" else "Experimental — explicit consent required")
                Text(policy.boundary)
                if (policy.access == PilotAccess.CONSENT_REQUIRED) Button(onClick = {
                    acknowledgedExperiments = acknowledgedExperiments + module
                }) { Text("Enable for this session with test data") }
                Text("Code presence is not a passed capability test. No permissions or network operations have been started by this panel.")
            }
            return
        }
        when (module) {
            "pilot" -> PilotContent()
            "files" -> FilesScreen(
                repo = repository,
                trash = trashRepository,
                mutationsAllowed = false,
                treeUri = treeUri,
                onChooseFolder = { chooseFolder.launch(null) },
                onOpenText = { entry ->
                    editorModel.open(entry.uri, entry.name)
                    openModule("editor")
                },
                onMessage = { message = it }
            )

            "editor" -> EditorScreen(editorModel) { request -> saveCopy.launch(request) }

            "docs" -> DocsContent()
            "pdf", "pdf-preview" -> PdfScreen(this, pdfUri) { openPdf.launch(arrayOf("application/pdf")) }
            "image" -> ImageContent()
            "psd" -> dev.fusion.psd.PsdScreen(this, psdUri) { openPsd.launch(arrayOf("image/vnd.adobe.photoshop", "application/x-photoshop", "*/*")) }
            "browser" -> BrowserScreen()
            "terminal" -> TerminalScreen(localShell)
            "vault" -> VaultScreen()
            "notes" -> NotesScreen(notesModel)
            "media" -> dev.fusion.media.MediaScreen(this) { uri, mime ->
                runCatching {
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime.ifEmpty { "*/*" })
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Open with"
                        )
                    )
                }.onFailure { message = "No viewer available: ${it.message}" }
            }
            "beam" -> BeamScreen(
                discovery = beamDiscovery,
                localMediaServerAlive = false,
                serverPort = 8080
            )
            "dlna" -> DlnaScreen(
                context = this,
                localMediaServerAlive = false,
                serverPort = 8080
            )
            "alerts" -> AlertsScreen(this) {
                runCatching { startActivity(Intent(FusionNotificationListener.settingsAction())) }
                    .onFailure { message = "Could not open notification settings: ${it.message}" }
            }
            "capture" -> CaptureScreen(
                onRequestConsent = {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        captureNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else captureConsent.launch(ScreenRecordService.consentIntent(this))
                },
                onStop = { ScreenRecordService.stop(this) }
            )
            "backup" -> BackupScreen(
                status = backupStatus,
                onExport = { pw -> pendingBackupPassword = pw; exportBackup.launch("fusion-backup.fsb") },
                onRestore = { pw -> pendingBackupPassword = pw; importBackup.launch(arrayOf("*/*")) }
            )
            "continuity" -> ContinuityScreen(this, incomingShare)
            "a11y" -> AccessibilityScreen(this)
            "settings" -> SettingsContent()
            "apps" -> AppsContent()
            "calculator" -> CalcScreen(calculatorModel)
            "together" -> TogetherScreen()
            "lens" -> LensScreen()
            "messages" -> MessagesScreen(this)
            "automation" -> AutomationScreen(this) { action ->
                if(desktopModel.loading || desktopModel.blocked) false
                else when(action) {
                    is dev.fusion.automation.LocalAction.Open -> {
                        openModule(action.module)
                        desktop.windows.any { it.module==action.module && it.workspace==desktop.workspace }
                    }
                    is dev.fusion.automation.LocalAction.Workspace -> {
                        dispatch(WindowCommand.SwitchWorkspace(action.index));desktop.workspace==action.index
                    }
                }
            }
            else -> Text("Module not available in this build.", Modifier.padding(16.dp))
        }
    }

    @Composable
    private fun PilotContent() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pilot safety and recovery", style = MaterialTheme.typography.titleLarge)
            Text("No feature acceptance is implied. Do not use real secrets or your only copy of a document. Release gates remain blocked.")
            Text(desktopModel.status)
            Text("Keyboard (inside Fusion): Ctrl+Alt+arrows move; add Shift to resize; Ctrl+Alt+1/2/0 tile; Ctrl+Alt+Tab cycles windows; M minimizes; W closes; PageUp/PageDown changes workspace. OEM conflicts need S1 testing.")
            Text("Window geometry density is configured in Settings; full density profiles are not yet implemented.")
            Button(onClick = { resetLayoutPrompt = true }) { Text("Reset window layout…") }
            Text("Reset retains editor drafts. Pending saves are verified separately; local recovery is not a backup.")
            PilotPolicy.modules.forEach { module ->
                Text("${module.title}: ${module.access.name}")
                Text(module.boundary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun DocsContent() {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { openDoc.launch(arrayOf("*/*")) }) { Text("Open .docx / .xlsx / .zip") }
            Text(
                "Own scanner, not Apache POI or LibreOffice: text and cell values only, " +
                    "no layout, styles, formulas or editing.",
                style = MaterialTheme.typography.labelSmall
            )
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(docSummary, fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun ImageContent() {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { openImage.launch(arrayOf("image/*")) }) { Text("Inspect an image") }
            Text(imageSummary)
            Text(
                "Decoding uses the platform decoder, so HEIC/AVIF work only if this device supports them. " +
                    "Export can write JPEG, PNG or WebP; HEIC encoding is not available.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    @Composable
    private fun SettingsContent() {
        var probeText by remember { mutableStateOf("Collecting local observations…") }
        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    probeText = withContext(Dispatchers.IO) {
                        val report = CapabilityProbe.inspect(this@MainActivity)
                        val hw = HwProbe.inspect(this@MainActivity)
                        val thermal = ThermalProbe.current(this@MainActivity).name
                        val pss = MemoryProbe.pssMb(this@MainActivity)
                        buildString {
                            appendLine("Fusion pilot • API ${report.sdk} • all performance budgets unmeasured")
                            appendLine("SoC: ${report.soc}; RAM: ${report.ramGb?.let { String.format(java.util.Locale.ROOT, "%.2f", it) } ?: "unknown"} GiB")
                            appendLine(report.deviceClass)
                            appendLine("Presentation displays: ${report.presentationDisplays ?: "unknown"} (not evidence of physical DP-out)")
                            appendLine("Home role: ${report.homeRoleHeld}; freeform declared: ${report.freeformDeclared}; desktop session: unknown")
                            appendLine("Advertised HW encoders: ${report.videoEncoders?.joinToString()?.ifEmpty { "none advertised" } ?: "unknown"}")
                            appendLine("HW codec candidate: ${hw.preferredCastCodec ?: "none verified/advertised"} — no encoder initialized by this probe")
                            appendLine("Thermal: $thermal; own-process total PSS: ${if (pss >= 0) "$pss MiB" else "unknown"}")
                            appendLine("NPU: ${report.npuStatus}; low-latency audio flag: ${hw.lowLatencyAudio}")
                            appendLine("Polling while Settings is visible and the Activity is started; measurements are point-in-time.")
                            (report.errors + hw.errors).forEach { appendLine(it) }
                        }
                    }
                    kotlinx.coroutines.delay(5_000)
                }
            }
        }
        SettingsScreen(settingsGraph, probeText, desktop.density.name,
            onDensity = { dispatch(WindowCommand.SetDensity(WindowDensity.valueOf(it))) },
            onShareReport = {
                runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, probeText)
                }, "Share redacted pilot observations")) }.onFailure { message = "No share target available." }
            })
    }

    @Composable
    private fun AppsContent() {
        val activities = remember {
            runCatching {
                packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
                ).sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            }.getOrDefault(emptyList())
        }
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Text("Launchable apps (${activities.size}). Running-task state is not available to apps.", color = mint)
            LazyColumn {
                items(activities) { item ->
                    Text(
                        item.loadLabel(packageManager).toString(),
                        Modifier.fillMaxWidth().clickable {
                            val launch = packageManager.getLaunchIntentForPackage(item.activityInfo.packageName)
                            if (launch != null) {
                                runCatching { startActivity(launch) }
                                    .onFailure { message = "Launch failed: ${it.localizedMessage}" }
                            } else message = "That app has no launch intent."
                        }.padding(9.dp)
                    )
                }
            }
        }
    }
}
