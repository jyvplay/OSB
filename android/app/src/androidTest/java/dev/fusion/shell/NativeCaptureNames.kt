// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/** Filename allowlist only. It does not authorize a capture or establish that pixels exist. */
internal object NativeCaptureNames {
    private val dialog = Regex("(?:(?:r24c|r58|r59)-[a-z-]{1,40}|r60-writer-numbered|r60-sheet-transpose|r61-sheet-protection|r62-writer-strike|r62-sheet-range|r63-sheet-formula|r63-writer-restart)")
    fun dialog(name: String): Boolean = dialog.matches(name)
}
