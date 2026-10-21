package dev.fusion.model

import java.util.Collections

/** DP-10 / SH-07: source presence is not permission to exercise an unverified capability. */
enum class PilotAccess { CANDIDATE, CONSENT_REQUIRED, BLOCKED }
data class PilotModule(val id: String, val title: String, val access: PilotAccess, val boundary: String)

object PilotPolicy {
    val modules: List<PilotModule> = Collections.unmodifiableList(listOf(
        PilotModule("pilot", "Pilot safety", PilotAccess.CANDIDATE, "Local status and operator instructions. No telemetry or automatic export."),
        PilotModule("files", "Files", PilotAccess.CANDIDATE, "SAF browsing and UTF-8 opening only. Mutation/trash disabled until S6 crash/provider matrix passes."),
        PilotModule("editor", "Editor", PilotAccess.CANDIDATE, "Eight bounded encoding-preserving drafts, 2 MiB each; undo/redo, literal tools and current-source line diff. Save a Copy only. Android/IME/accessibility acceptance pending; recovery is not a backup. Use disposable files."),
        PilotModule("calculator", "Calculator", PilotAccess.CANDIDATE, "Decimal/scientific/64-bit programmer/unit conversion and local history. Kernels tested on JVM; Android/accessibility acceptance pending. Scientific output is approximate, not a symbolic solver."),
        PilotModule("settings", "Settings and probe", PilotAccess.CANDIDATE, "Read-only device diagnostics and partial settings. Probe values are observations, not performance grades."),
        PilotModule("a11y", "Accessibility", PilotAccess.CANDIDATE, "Read-only system accessibility state. TalkBack/Switch/Voice release gates remain unpassed."),
        PilotModule("apps", "Installed apps", PilotAccess.CANDIDATE, "Launchable apps only. Third-party task/window state is not available."),
        PilotModule("continuity", "Sharing and peripherals", PilotAccess.CANDIDATE, "Explicit Android share intents and attached input inventory; no PC bridge."),
        PilotModule("quickpanel", "Quick panel", PilotAccess.CANDIDATE, "Studio Silver Art / Office Light Business / Graphite Dark presets, 3D tabletop toggle and instant session secret zeroization. UI mode is not OS anonymity."),
        PilotModule("ime", "Hardened input", PilotAccess.CANDIDATE, "CSPRNG Fisher-Yates scrambled touch keypad, <8 ms BadUSB burst quarantine and uniform-cadence timing quantizer. Cannot defeat a compromised kernel reading framebuffer+touch simultaneously."),
        PilotModule("office", "Office workbench", PilotAccess.CONSENT_REQUIRED, "Clean-room Apache-2.0 DOCX/XLSX/PPTX engine with side-effect-free formula evaluation and macro refusal (vbaProject.bin rejected). Does not claim full print pagination, charts or pivot tables."),
        PilotModule("photos", "Photos cleaner", PilotAccess.CONSENT_REQUIRED, "Lossless JPEG APP1/EXIF/GPS/XMP/IPTC/COM segment stripping preserving SOS..EOI scan bytes without recompression, plus album & aspect tools. Sensor noise and visual content remain."),
        PilotModule("browser-workbench", "Sandboxed HTTPS web", PilotAccess.CONSENT_REQUIRED, "Starts at offline about:blank; HTTPS-only navigation with file:///content:///javascript: blocked and one-tap cookie/WebStorage zeroization. Uses platform WebView, not multi-process isolated desktop Chromium."),
        PilotModule("notes", "Notes", PilotAccess.CONSENT_REQUIRED, "Bounded UTF-8 Markdown with revision-checked publication/readback and explicit saved-note wiki-backlink scans. Use disposable text; Android acceptance pending. Coalesced local draft checkpoints are separate from saved notes; unconfirmed edits can be lost. No backup or encryption."),
        PilotModule("browser", "Browser", PilotAccess.BLOCKED, "Process isolation, origin/permission and session security review pending. Use an external browser for the pilot."),
        PilotModule("terminal", "Local shell", PilotAccess.CONSENT_REQUIRED, "Allowlisted Android diagnostics and echo only. No command interpreter, subprocess execution, PTY, SSH or Linux userland."),
        PilotModule("media", "Media", PilotAccess.CONSENT_REQUIRED, "Selected-content native foreground audio/video player, bounded UTF-8 SRT inspection and MediaStore images. Online service links use an explicitly confirmed external-player handoff, NOT integrated/unrestricted streaming. Platform codecs/provider access remain trusted."),
        PilotModule("png-copy", "PNG metadata copy", PilotAccess.CONSENT_REQUIRED, "Bounded isolated RGB/RGBA8 PNG metadata stripping to a new file with exact readback. No native image decoder; text/EXIF/private ancillary metadata removed, image samples retained. Orientation/printing can change; pixels/steganography/encoding fingerprints remain. Not anonymous or malware-safe. Trust the selected provider; use copied data."),
        PilotModule("maps", "Offline atlas", PilotAccess.CONSENT_REQUIRED, "Offline Natural Earth 1:110m land outlines, coordinates, local saved pins and spherical distance. No GPS, street routing, geocoder, traffic or network tiles. Pins are plaintext. Not navigation or surveying."),
        PilotModule("capture", "Screen capture", PilotAccess.CONSENT_REQUIRED, "Records the screen/app selected in Android consent. Video only; codec hardware use/latency unmeasured. Persistent Stop, lock/critical-thermal stop. Exclude all real secrets."),
        PilotModule("lens", "Lens preview", PilotAccess.CONSENT_REQUIRED, "Physical camera preview only. No effects, virtual camera, conferencing or recording route."),
        PilotModule("automation", "Tasks and action plans", PilotAccess.CONSENT_REQUIRED, "Local plaintext tasks with editing, reversible archive, date-only labels and revision-checked storage; explicit own-app actions. No reminders, schedules, external commands, privilege or implicit consent."),
        PilotModule("backup", "Backup", PilotAccess.BLOCKED, "Restore/key custody/fault matrix S7 not passed. Do not entrust pilot data to this engine."),
        PilotModule("alerts", "Alerts", PilotAccess.BLOCKED, "Encrypted history and privacy review incomplete. Listener ingestion is disabled in the pilot build."),
        PilotModule("vault", "Vault", PilotAccess.BLOCKED, "Existing Keystore exercise is not a password manager. Authentication, custody and capture exclusion acceptance pending."),
        PilotModule("docs", "Documents", PilotAccess.BLOCKED, "Office/ZIP parsing not isolated; S10/S11 and hostile-file tests pending."),
        PilotModule("pdf", "PDF", PilotAccess.BLOCKED, "Full PDF editing/signing/fidelity acceptance pending. Bounded isolated readonly preview is a separate pdf-preview experiment."),
        PilotModule("pdf-preview", "Isolated PDF preview", PilotAccess.CONSENT_REQUIRED, "Readonly copied PDF≤2MiB,≤1024pages,≤320px/side. Native renderer only in isolated child via seekable readonly FD; raw pixels validated in parent. Not full PDF/Office fidelity, honest-worker proof, no-disk/erasure or hostile-OS protection. Use disposable data."),
        PilotModule("image", "Images", PilotAccess.BLOCKED, "Decoder isolation, size guards and S12 OEM matrix pending."),
        PilotModule("psd", "PSD", PilotAccess.BLOCKED, "Parser isolation and S13 fidelity matrix pending."),
        PilotModule("beam", "Beam", PilotAccess.BLOCKED, "Receiver authentication/trust, owned session indicator and S17–S21 not passed. No automatic LAN file serving."),
        PilotModule("dlna", "DLNA", PilotAccess.BLOCKED, "LAN peer/XML/URL trust and owned session stop/privacy review pending."),
        PilotModule("messages", "Messages", PilotAccess.BLOCKED, "SMS role/permission policy and delivery receipts incomplete. No iMessage, RCS or Matrix transport."),
        PilotModule("together", "Together", PilotAccess.BLOCKED, "No network co-watching service or measured synchronization. S22 not passed."),
    ))
    fun module(id: String): PilotModule = modules.find { it.id == id }
        ?: PilotModule(id, "Unknown module", PilotAccess.BLOCKED, "Unknown modules fail closed.")
    fun mayRun(id: String, acknowledged: Set<String>): Boolean = when (module(id).access) {
        PilotAccess.CANDIDATE -> true
        PilotAccess.CONSENT_REQUIRED -> id in acknowledged
        PilotAccess.BLOCKED -> false
    }
}
