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
        PilotModule("notes", "Notes", PilotAccess.CONSENT_REQUIRED, "Bounded UTF-8 Markdown with revision-checked publication/readback and explicit saved-note wiki-backlink scans. Use disposable text; Android acceptance pending. Unsaved drafts are memory-only; no backup or encryption."),
        PilotModule("browser", "Browser", PilotAccess.BLOCKED, "Process isolation, origin/permission and session security review pending. Use an external browser for the pilot."),
        PilotModule("terminal", "Local shell", PilotAccess.CONSENT_REQUIRED, "Allowlisted Android diagnostics and echo only. No command interpreter, subprocess execution, PTY, SSH or Linux userland."),
        PilotModule("media", "Media", PilotAccess.CONSENT_REQUIRED, "MediaStore listing and explicit external viewer handoff, not a native player."),
        PilotModule("capture", "Screen capture", PilotAccess.CONSENT_REQUIRED, "Records the screen/app selected in Android consent. Video only; codec hardware use/latency unmeasured. Persistent Stop, lock/critical-thermal stop. Exclude all real secrets."),
        PilotModule("lens", "Lens preview", PilotAccess.CONSENT_REQUIRED, "Physical camera preview only. No effects, virtual camera, conferencing or recording route."),
        PilotModule("automation", "Automation drafts", PilotAccess.CONSENT_REQUIRED, "Draft storage only. No scheduled or executed routines."),
        PilotModule("backup", "Backup", PilotAccess.BLOCKED, "Restore/key custody/fault matrix S7 not passed. Do not entrust pilot data to this engine."),
        PilotModule("alerts", "Alerts", PilotAccess.BLOCKED, "Encrypted history and privacy review incomplete. Listener ingestion is disabled in the pilot build."),
        PilotModule("vault", "Vault", PilotAccess.BLOCKED, "Existing Keystore exercise is not a password manager. Authentication, custody and capture exclusion acceptance pending."),
        PilotModule("docs", "Documents", PilotAccess.BLOCKED, "Office/ZIP parsing not isolated; S10/S11 and hostile-file tests pending."),
        PilotModule("pdf", "PDF", PilotAccess.BLOCKED, "Untrusted PDF parsing has no isolated service/watchdog yet."),
        PilotModule("image", "Images", PilotAccess.BLOCKED, "Decoder isolation, size guards and S12 OEM matrix pending."),
        PilotModule("psd", "PSD", PilotAccess.BLOCKED, "Parser isolation and S13 fidelity matrix pending."),
        PilotModule("beam", "Beam", PilotAccess.BLOCKED, "Receiver authentication/trust, owned session indicator and S17–S21 not passed. No automatic LAN file serving."),
        PilotModule("dlna", "DLNA", PilotAccess.BLOCKED, "LAN peer/XML/URL trust and owned session stop/privacy review pending."),
        PilotModule("messages", "Messages", PilotAccess.BLOCKED, "SMS role/permission policy and delivery receipts incomplete. No iMessage, RCS or Matrix transport."),
        PilotModule("together", "Together", PilotAccess.BLOCKED, "Local state model is not a network co-watching service. S22 not passed."),
    ))
    fun module(id: String): PilotModule = modules.find { it.id == id }
        ?: PilotModule(id, "Unknown module", PilotAccess.BLOCKED, "Unknown modules fail closed.")
    fun mayRun(id: String, acknowledged: Set<String>): Boolean = when (module(id).access) {
        PilotAccess.CANDIDATE -> true
        PilotAccess.CONSENT_REQUIRED -> id in acknowledged
        PilotAccess.BLOCKED -> false
    }
}
