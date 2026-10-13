package dev.fusion.shell

import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
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
import dev.fusion.capabilities.CapabilityProbe
import dev.fusion.capture.CaptureScreen
import dev.fusion.capture.ScreenRecordService
import dev.fusion.cast.dlna.DlnaScreen
import dev.fusion.continuity.ContinuityScreen
import dev.fusion.editor.EditorScreen
import dev.fusion.filesui.FilesScreen
import dev.fusion.files.SafRepository
import dev.fusion.files.TrashRepository
import dev.fusion.format.image.ImageOps
import dev.fusion.format.pdf.PdfScreen
import dev.fusion.hw.HwProbe
import dev.fusion.messages.MessagesScreen
import dev.fusion.notes.NotesScreen
import dev.fusion.notes.NotesStore
import dev.fusion.ooxml.Ooxml
import dev.fusion.perf.MemoryProbe
import dev.fusion.perf.ThermalProbe
import dev.fusion.settings.SettingsGraph
import dev.fusion.settingsui.SettingsScreen
import dev.fusion.terminal.LocalShell
import dev.fusion.terminal.TerminalScreen
import dev.fusion.together.TogetherScreen
import dev.fusion.vault.VaultScreen
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

private val bg = Color(0xFF0C1011)
private val panel = Color(0xFF191E20)
private val border = Color(0xFF384044)
private val mint = Color(0xFF65DDB4)

private val MODULES = listOf(
    "files", "editor", "docs", "pdf", "image", "psd", "browser", "terminal",
    "vault", "notes", "media", "beam", "dlna", "alerts", "capture",
    "backup", "continuity", "a11y", "settings", "apps", "calculator",
    "together", "lens", "messages", "automation"
)

class MainActivity : ComponentActivity() {

    private val repository by lazy { SafRepository(this) }
    private val prefs by lazy { getSharedPreferences("fusion", MODE_PRIVATE) }
    private val settingsGraph by lazy { SettingsGraph(this) }
    private val notesStore by lazy { NotesStore(this) }
    private val localShell by lazy { LocalShell(this) }
    private val beamDiscovery by lazy { BeamDiscovery(this) }
    private val backupEngine by lazy { BackupEngine(this) }
    private val trashRepository by lazy { TrashRepository(this) }
    private var localMediaServer: dev.fusion.beam.LocalMediaServer? = null

    private var desktop by mutableStateOf(DesktopState())
    private var treeUri by mutableStateOf<Uri?>(null)
    private var message by mutableStateOf("Local-first. Nothing leaves this device unless you share it.")
    private var editorText by mutableStateOf("")
    private var editorName by mutableStateOf("Untitled.txt")
    private var pendingSaveText = ""
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
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            treeUri = uri
            prefs.edit().putString("tree", uri.toString()).apply()
            message = "Folder access granted. Only this tree is visible."
        } catch (e: SecurityException) {
            message = "Folder permission was not retained: ${e.localizedMessage}"
        }
    }

    private val saveCopy = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            repository.writeNewText(uri, pendingSaveText).fold(
                onSuccess = { message = "New document saved. Original untouched." },
                onFailure = { message = "Save failed: ${it.localizedMessage}" }
            )
        }
    }

    private val captureConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenRecordService.start(this, result.resultCode, data)
        } else {
            message = "Screen capture consent was declined. Nothing recorded."
        }
    }

    private val exportBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri == null || password == null) return@registerForActivityResult
        lifecycleScope.launch {
            backupEngine.export(uri, password).fold(
                onSuccess = {
                    backupStatus = "Exported ${it.fileCount} note(s): ${it.plainBytes}B -> ${it.sealedBytes}B " +
                        "sealed, sha256 ${it.sha256}..."
                },
                onFailure = { backupStatus = "Export failed: ${it.message}" }
            )
            password.fill('\u0000')
        }
    }

    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri == null || password == null) return@registerForActivityResult
        lifecycleScope.launch {
            backupEngine.restore(uri, password).fold(
                onSuccess = { backupStatus = "Restored $it note file(s). Existing files were kept." },
                onFailure = { backupStatus = "Restore failed (wrong password or not a Fusion backup): ${it.message}" }
            )
            password.fill('\u0000')
        }
    }

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pdfUri = uri
    }

    private val openPsd = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) psdUri = uri
    }

    private val openImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            ImageOps.probe(this@MainActivity, uri).fold(
                onSuccess = { imageSummary = "${it.mime} ${it.width}x${it.height}, ${it.bytes} bytes on disk." },
                onFailure = { imageSummary = "Cannot decode on this device: ${it.message}" }
            )
        }
    }

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch { inspectDocument(uri) }
    }

    // ---- lifecycle ---------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        desktop = restoreDesktop()
        treeUri = prefs.getString("tree", null)?.let(Uri::parse)?.takeIf { selected ->
            contentResolver.persistedUriPermissions.any { it.uri == selected && it.isReadPermission }
        }
        handleShare(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = mint, background = bg, surface = panel)) {
                Surface(Modifier.fillMaxSize(), color = bg) { ShellScreen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            incomingShare = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (incomingShare != null) {
                open("continuity")
                message = "Received shared text from another app."
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (localMediaServer == null) {
            localMediaServer = dev.fusion.beam.LocalMediaServer(port = 8080) { cleanPath ->
                // Strict SAF lookup: traverse the granted tree segment by segment.
                // No Uri.withAppendedPath guessing — providers reject fabricated URIs.
                val root = treeUri?.let { androidx.documentfile.provider.DocumentFile.fromTreeUri(this, it) }
                    ?: return@LocalMediaServer null
                var node: androidx.documentfile.provider.DocumentFile? = root
                for (segment in cleanPath.split("/")) {
                    node = node?.findFile(segment) ?: return@LocalMediaServer null
                }
                val file = node
                if (file == null || !file.isFile) return@LocalMediaServer null
                try {
                    val stream = contentResolver.openInputStream(file.uri) ?: return@LocalMediaServer null
                    val length = contentResolver.openAssetFileDescriptor(file.uri, "r")?.use { it.length } ?: -1L
                    val mime = file.type
                        ?: contentResolver.getType(file.uri)
                        ?: "application/octet-stream"
                    dev.fusion.beam.MediaStream(stream, length, mime)
                } catch (_: Exception) { null }
            }.also {
                if (!it.start()) {
                    message = "Media server could not bind port 8080: ${it.bindError ?: "in use"}. DLNA URLs will fail."
                }
            }
        }
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
        localMediaServer?.stop()
        localMediaServer = null
        persistDesktop()
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
            ExternalDesktopScreen(desktop, externalDisplayLabel ?: target.name)
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
        desktop = WindowEngine.reduce(desktop, command)
    }

    private fun titleFor(module: String) = when (module) {
        "files" -> "Files"; "editor" -> "Editor"; "docs" -> "Documents (text extract)"
        "pdf" -> "PDF viewer"; "image" -> "Image tools"; "psd" -> "PSD viewer"
        "browser" -> "Browser"
        "terminal" -> "Local shell"; "vault" -> "Vault"; "notes" -> "Notes"
        "media" -> "Media"; "beam" -> "Beam discovery"; "dlna" -> "DLNA cast"
        "alerts" -> "Alerts"; "capture" -> "Screen capture"; "backup" -> "Encrypted backup"
        "continuity" -> "Peripherals and sharing"; "a11y" -> "Accessibility"
        "settings" -> "Settings and probe"; "apps" -> "Installed apps"
        "calculator" -> "Calculator"; "together" -> "Together"
        "lens" -> "Lens (preview only)"; "messages" -> "Messages"
        "automation" -> "Automation"; else -> module
    }

    private fun open(module: String) {
        val existing = desktop.windows.firstOrNull { it.module == module && it.workspace == desktop.workspace }
        if (existing != null) {
            dispatch(WindowCommand.Focus(existing.id)); return
        }
        dispatch(WindowCommand.Open(module, titleFor(module), Bounds(42f, 46f, 600f, 430f)))
    }

    private fun persistDesktop() {
        val array = JSONArray()
        desktop.windows.forEach { w ->
            array.put(JSONObject().apply {
                put("id", w.id); put("module", w.module); put("title", w.title)
                put("x", w.bounds.x); put("y", w.bounds.y)
                put("w", w.bounds.width); put("h", w.bounds.height)
                put("z", w.z); put("min", w.minimized); put("ws", w.workspace)
            })
        }
        prefs.edit().putString("windows", array.toString()).putInt("workspace", desktop.workspace).apply()
    }

    private fun restoreDesktop(): DesktopState = try {
        val array = JSONArray(prefs.getString("windows", "[]"))
        val list = (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val module = o.getString("module")
            if (module !in MODULES) null else ShellWindow(
                id = o.getLong("id"), module = module, title = o.getString("title"),
                bounds = Bounds(
                    o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                    o.getDouble("w").toFloat(), o.getDouble("h").toFloat()
                ),
                z = o.getInt("z"), minimized = o.optBoolean("min"), workspace = o.optInt("ws")
            )
        }
        val workspace = prefs.getInt("workspace", 0).coerceIn(0, 2)
        DesktopState(
            list, workspace,
            list.filter { it.workspace == workspace && !it.minimized }.maxByOrNull { it.z }?.id,
            (list.maxOfOrNull { it.id } ?: 0L) + 1L
        )
    } catch (_: Exception) {
        DesktopState()
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
        var paletteVisible by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

        Column(
            Modifier.fillMaxSize().focusRequester(focusRequester).onPreviewKeyEvent { event ->
                when {
                    event.isCtrlPressed && event.key == Key.K -> { paletteVisible = true; true }
                    event.key == Key.Escape && paletteVisible -> { paletteVisible = false; true }
                    else -> false
                }
            }.focusable()
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).background(panel).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("F / FUSION", color = mint, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(20.dp))
                Text("Find", Modifier.clickable { paletteVisible = true }, color = Color.LightGray)
                Spacer(Modifier.weight(1f))
                Text("WORKSPACE ${desktop.workspace + 1}", fontSize = 11.sp, color = Color.Gray)
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(bg)) {
                val sw = maxWidth.value
                val sh = maxHeight.value
                val scale = LocalDensity.current.density

                Column(Modifier.padding(20.dp)) {
                    Text("Fusion Shell", fontSize = 34.sp, color = mint, fontWeight = FontWeight.Bold)
                    Text("Android desktop / local-first", color = Color.Gray)
                    Spacer(Modifier.height(14.dp))
                    Text("Pick a module from the shelf below, or press Ctrl+K.", fontSize = 12.sp)
                }

                desktop.windows.filter { it.workspace == desktop.workspace && !it.minimized }
                    .sortedBy { it.z }.forEach { w ->
                        val adjusted = w.bounds.constrained(sw, sh)
                        Column(
                            Modifier.offset(adjusted.x.dp, adjusted.y.dp)
                                .size(adjusted.width.dp, adjusted.height.dp)
                                .zIndex(w.z.toFloat())
                                .border(1.dp, if (desktop.activeId == w.id) mint else border)
                                .background(panel)
                                .clickable { dispatch(WindowCommand.Focus(w.id)) }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(38.dp).background(Color(0xFF252C2D))
                                    .pointerInput(w.id, sw, sh) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            dispatch(WindowCommand.Move(w.id, drag.x / scale, drag.y / scale, sw, sh))
                                        }
                                    }.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(w.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TitleAction("L") { dispatch(WindowCommand.TileWindow(w.id, Tile.LEFT, sw, sh)) }
                                TitleAction("R") { dispatch(WindowCommand.TileWindow(w.id, Tile.RIGHT, sw, sh)) }
                                TitleAction("[]") { dispatch(WindowCommand.TileWindow(w.id, Tile.MAXIMIZE, sw, sh)) }
                                TitleAction("_") { dispatch(WindowCommand.Minimize(w.id)) }
                                TitleAction("X") { dispatch(WindowCommand.Close(w.id)) }
                            }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                ModuleContent(w.module)
                                Text(
                                    "/",
                                    Modifier.align(Alignment.BottomEnd).pointerInput(w.id, sw, sh) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            dispatch(WindowCommand.Resize(w.id, drag.x / scale, drag.y / scale, sw, sh))
                                        }
                                    }.padding(6.dp),
                                    color = mint
                                )
                            }
                        }
                    }

                if (paletteVisible) {
                    Column(
                        Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                            .width(330.dp).background(Color(0xFF262F30)).padding(12.dp)
                    ) {
                        var query by remember { mutableStateOf("") }
                        OutlinedTextField(query, { query = it }, label = { Text("Search modules (local only)") })
                        Column(Modifier.height(260.dp).verticalScroll(rememberScrollState())) {
                            MODULES.filter { query.isBlank() || it.contains(query, true) }.forEach { key ->
                                Text(
                                    titleFor(key),
                                    Modifier.fillMaxWidth()
                                        .clickable { open(key); paletteVisible = false }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(panel).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MODULES.forEach { module ->
                    Button(
                        onClick = { open(module) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF303A39))
                    ) { Text(module.replaceFirstChar { it.uppercase() }) }
                }
                (0..2).forEach { ws ->
                    OutlinedButton(onClick = { dispatch(WindowCommand.SwitchWorkspace(ws)) }) {
                        Text(if (ws == desktop.workspace) "[${ws + 1}]" else "${ws + 1}")
                    }
                }
            }

            Text(
                message, Modifier.fillMaxWidth().background(Color(0xFF12191A)).padding(6.dp),
                fontSize = 11.sp, color = mint, maxLines = 2
            )
        }
    }

    @Composable
    private fun TitleAction(label: String, onClick: () -> Unit) {
        Text(label, Modifier.clickable(onClick = onClick).padding(horizontal = 5.dp), color = mint, fontSize = 12.sp)
    }

    @Composable
    private fun ModuleContent(module: String) {
        when (module) {
            "files" -> FilesScreen(
                repo = repository,
                trash = trashRepository,
                treeUri = treeUri,
                onChooseFolder = { chooseFolder.launch(null) },
                onOpenText = { entry ->
                    lifecycleScope.launch {
                        repository.readText(entry.uri).fold(
                            onSuccess = { editorText = it; editorName = entry.name; open("editor") },
                            onFailure = { message = it.localizedMessage ?: "Read failed" }
                        )
                    }
                },
                onMessage = { message = it }
            )

            "editor" -> EditorScreen(editorText, editorName) { name, text ->
                pendingSaveText = text
                saveCopy.launch(name.substringBeforeLast('.') + "-copy.txt")
            }

            "docs" -> DocsContent()
            "pdf" -> PdfScreen(this, pdfUri) { openPdf.launch(arrayOf("application/pdf")) }
            "image" -> ImageContent()
            "psd" -> dev.fusion.psd.PsdScreen(this, psdUri) { openPsd.launch(arrayOf("image/vnd.adobe.photoshop", "application/x-photoshop", "*/*")) }
            "browser" -> BrowserScreen()
            "terminal" -> TerminalScreen(localShell)
            "vault" -> VaultScreen()
            "notes" -> NotesScreen(notesStore)
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
                localMediaServerAlive = localMediaServer?.isAlive == true,
                serverPort = 8080,
                currentThermal = ThermalProbe.current(this),
                linkSpeedMbps = 100
            )
            "dlna" -> DlnaScreen(
                context = this,
                localMediaServerAlive = localMediaServer?.isAlive == true,
                serverPort = 8080
            )
            "alerts" -> AlertsScreen(this) {
                runCatching { startActivity(Intent(FusionNotificationListener.settingsAction())) }
                    .onFailure { message = "Could not open notification settings: ${it.message}" }
            }
            "capture" -> CaptureScreen(
                onRequestConsent = { captureConsent.launch(ScreenRecordService.consentIntent(this)) },
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
            "calculator" -> CalcScreen()
            "together" -> TogetherScreen()
            "lens" -> LensScreen()
            "messages" -> MessagesScreen(this)
            "automation" -> AutomationScreen(this)
            else -> Text("Module not available in this build.", Modifier.padding(16.dp))
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
        val report = remember { CapabilityProbe.inspect(this@MainActivity) }
        val hw = remember { HwProbe.inspect(this@MainActivity) }
        val thermal = remember { ThermalProbe.current(this@MainActivity).name }
        val pss = remember { MemoryProbe.pssMb(this@MainActivity) }
        val probeText = remember(report, hw) {
            buildString {
                appendLine("API ${report.sdk} / ${report.deviceClass}")
                appendLine("SoC ${report.soc} / RAM ${report.ramGb} GB")
                appendLine(
                    "Presentation displays: ${report.presentationDisplays} · " +
                        (externalDisplayLabel?.let { "live session mirror on $it" }
                            ?: "no external window attached")
                )
                appendLine("HW encoders: ${report.videoEncoders.joinToString().ifEmpty { "none" }}")
                appendLine("Preferred cast codec if casting existed: ${hw.preferredCastCodec}")
                appendLine("Thermal $thermal / PSS ${if (pss >= 0) "$pss MB" else "unknown"}")
                appendLine("NPU: ${report.npuStatus}")
                append("Fingerprint: ${report.fingerprint.take(64)}")
            }
        }
        SettingsScreen(settingsGraph, probeText)
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
