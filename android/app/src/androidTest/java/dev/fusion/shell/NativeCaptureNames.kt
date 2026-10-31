// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/** Filename allowlist only. It does not authorize a capture or establish that pixels exist. */
internal object NativeCaptureNames {
    private val dialog = Regex("(?:r24c|r58)-[a-z-]{1,40}")
    fun dialog(name: String): Boolean = dialog.matches(name)
}
