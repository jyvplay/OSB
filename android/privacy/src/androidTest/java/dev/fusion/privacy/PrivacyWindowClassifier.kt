// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.util.Locale

/** Bounded, test-only classification. Raw titles and package names are never returned. */
object PrivacyWindowClassifier {
    data class Kind(val root: String, val title: String)

    fun classify(packageName: String?, title: CharSequence?): Kind {
        val root = when (packageName) {
            "android" -> "android-system"
            "com.android.systemui" -> "system-ui"
            "com.android.permissioncontroller", "com.google.android.permissioncontroller" -> "permission-controller"
            null -> "unknown"
            else -> "other"
        }
        // Inspect titles only for the two recognized Android-owned surfaces. Missing/other does not
        // establish that a dialog is absent; labels and localization can differ.
        val category = if (root !in setOf("android-system", "system-ui")) "not-inspected" else {
            val normalized = title?.take(512)?.toString()?.trim()?.lowercase(Locale.ROOT)
            when {
                normalized.isNullOrEmpty() -> "missing"
                normalized.endsWith(" isn't responding") || normalized.endsWith(" is not responding") -> "english-not-responding-suffix"
                normalized.endsWith(" keeps stopping") || normalized.endsWith(" has stopped") -> "english-stopped-suffix"
                else -> "other"
            }
        }
        return Kind(root, category)
    }
}
