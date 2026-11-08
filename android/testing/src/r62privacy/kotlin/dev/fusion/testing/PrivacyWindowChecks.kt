// SPDX-License-Identifier: Apache-2.0
package dev.fusion.testing

import dev.fusion.privacy.PrivacyWindowClassifier

/** Executes the actual failure-evidence classifier; does not emulate Android windows. */
object PrivacyWindowChecks {
    private var checks = 0
    private fun ok(condition: Boolean) { checks++; check(condition) { "Privacy window check $checks failed" } }

    @JvmStatic fun main(args: Array<String>) {
        val kinds = listOf(
            "System UI isn't responding" to "english-not-responding-suffix",
            "Settings is not responding" to "english-not-responding-suffix",
            "Launcher keeps stopping" to "english-stopped-suffix",
            "App has stopped" to "english-stopped-suffix",
            "SYSTEM UI ISN'T RESPONDING" to "english-not-responding-suffix",
            "System UI isn't responding again" to "other",
            "Reagiert nicht" to "other",
            "" to "missing",
            "x".repeat(512) + " isn't responding" to "other",
            "secret-marker" to "other",
        )
        for (pkg in listOf("android", "com.android.systemui")) {
            for ((title, expected) in kinds) {
                val actual = PrivacyWindowClassifier.classify(pkg, title)
                ok(actual.title == expected)
                ok(!actual.toString().contains("secret-marker"))
                ok(!actual.toString().contains("System UI"))
            }
            ok(PrivacyWindowClassifier.classify(pkg, null).title == "missing")
            ok(PrivacyWindowClassifier.classify(pkg, StringBuilder("App has stopped")).title == "english-stopped-suffix")
        }
        for (pkg in listOf(null, "android.evil", "com.example.private", "com.android.permissioncontroller", "com.google.android.permissioncontroller")) {
            for ((title, _) in kinds) {
                val result = PrivacyWindowClassifier.classify(pkg, title)
                ok(result.title == "not-inspected")
                ok(!result.toString().contains("secret-marker"))
                ok(!result.toString().contains("com.example.private"))
            }
        }
        ok(PrivacyWindowClassifier.classify(null, "secret").root == "unknown")
        ok(PrivacyWindowClassifier.classify("android.evil", "secret").root == "other")
        ok(PrivacyWindowClassifier.classify("com.android.permissioncontroller", null).root == "permission-controller")
        println("R62 privacy-window classifier PASS: $checks assertions; host-only, not an Android observation.")
    }
}
