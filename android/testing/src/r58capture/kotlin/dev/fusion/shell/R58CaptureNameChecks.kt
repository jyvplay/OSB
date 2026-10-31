// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/** Host execution of the same filename policy used by the Android test helper. */
object R58CaptureNameChecks {
    @JvmStatic fun main(args: Array<String>) {
        var checked = 0
        for (name in listOf("r24c-structured", "r58-text-grid", "r58-service-review", "r58-" + "a".repeat(40))) {
            check(NativeCaptureNames.dialog(name)) { "Expected safe name: $name" }; checked++
        }
        for (name in listOf("r58-../outside", "../r58-text-grid", "r59-text-grid", "r58-", "r58-" + "a".repeat(41), "r58-A", "r58-a/b", "r58-a\nb")) {
            check(!NativeCaptureNames.dialog(name)) { "Expected unsafe name refusal: $name" }; checked++
        }
        println("R58 capture filename policy: $checked host assertions passed; not a native screenshot")
    }
}
