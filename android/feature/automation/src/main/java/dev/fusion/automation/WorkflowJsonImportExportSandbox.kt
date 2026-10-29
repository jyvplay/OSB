// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Clean-room Versioned JSON Workflow Import / Export Packager with SHA-256 Digest & Zero-Privilege
 * Capability Audit (`AU-04`).
 */
object WorkflowJsonImportExportSandbox {
    const val SCOPE =
        "Pure-JVM versioned JSON workflow share-pack serializer, SHA-256 content verifier, and capability-elevation diff inspector (AU-04)."

    private val FORBIDDEN_SHELL_STEPS = setOf("exec_raw_shell", "install_silent_apk", "read_global_clipboard")

    data class WorkflowPack(
        val schemaVersion: Int,
        val name: String,
        val trigger: String,
        val requiredCapabilities: Set<String>,
        val steps: List<String>,
    )

    data class ImportAudit(
        val pack: WorkflowPack,
        val sha256Hex: String,
        val newlyRequestedCapabilities: Set<String>,
        val dryRunTranscript: List<String>,
    )

    fun exportJson(pack: WorkflowPack): String {
        require(pack.schemaVersion == 1 && pack.name.isNotBlank() && pack.steps.isNotEmpty()) {
            "invalid WorkflowPack"
        }
        require(pack.steps.none { step -> FORBIDDEN_SHELL_STEPS.any { it in step } }) {
            "forbidden ambient-privilege step in workflow export"
        }
        val caps = pack.requiredCapabilities.sorted().joinToString(",") { "\"${esc(it)}\"" }
        val st = pack.steps.joinToString(",") { "\"${esc(it)}\"" }
        return """{"schema":1,"name":"${esc(pack.name)}","trigger":"${esc(pack.trigger)}","capabilities":[$caps],"steps":[$st]}"""
    }

    fun importAndDryRun(json: String, alreadyTrustedCapabilities: Set<String>): ImportAudit {
        require(!json.contains("exec_raw_shell") && !json.contains("install_silent_apk")) {
            "workflow import rejected: contains forbidden ambient shell/install action"
        }
        val schema = Regex(""""schema"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toInt()
            ?: throw IllegalArgumentException("missing workflow schema version")
        require(schema == 1) { "unsupported workflow schema $schema" }
        val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("missing workflow name")
        val trigger = Regex(""""trigger"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("missing workflow trigger")
        val capsBlock = Regex(""""capabilities"\s*:\s*\[([^\]]*)]""").find(json)?.groupValues?.get(1) ?: ""
        val stepsBlock = Regex(""""steps"\s*:\s*\[([^\]]*)]""").find(json)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("missing workflow steps")

        val caps = Regex(""""([^"]+)"""").findAll(capsBlock).map { it.groupValues[1] }.toSet()
        val steps = Regex(""""([^"]+)"""").findAll(stepsBlock).map { it.groupValues[1] }.toList()
        require(steps.isNotEmpty()) { "imported workflow has zero steps" }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(json.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val pack = WorkflowPack(schema, name, trigger, caps, steps)
        val newCaps = caps - alreadyTrustedCapabilities
        val dryRun = steps.mapIndexed { idx, s -> "DRY_RUN[${idx + 1}/${steps.size}]: $s (sideEffects=0)" }
        return ImportAudit(pack, digest, newCaps, dryRun)
    }

    private fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
