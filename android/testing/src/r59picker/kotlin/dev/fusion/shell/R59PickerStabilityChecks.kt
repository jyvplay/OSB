// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

/** Host verification of the exact pre-tap gate, not a device navigation observation. */
object R59PickerStabilityChecks {
    private var count = 0
    private fun verify(value: Boolean, label: String) { count++; check(value) { label } }
    @JvmStatic fun main(args: Array<String>) {
        val root = PickerRootStability.Bounds(0, 0, 600, 1000)
        val target = PickerRootStability.Bounds(30, 140, 260, 200)
        val shifted = PickerRootStability.Bounds(32, 140, 262, 200)
        val gate = PickerRootStability()
        verify(!gate.observe(0, root, target), "first sample cannot pass")
        verify(!gate.observe(249, root, target), "249ms insufficient")
        verify(gate.observe(250, root, target), "stable for threshold")
        verify(gate.observe(300, root, target), "stable continuation")
        verify(!gate.observe(301, root, shifted), "motion resets")
        verify(!gate.observe(550, root, shifted), "new target not stable long enough")
        verify(gate.observe(551, root, shifted), "new target stable")
        verify(!gate.observe(552, root, null), "missing target resets")
        verify(!gate.observe(900, root, target), "new first sample after missing")
        verify(!gate.observe(1100, root, PickerRootStability.Bounds(-1, 2, 50, 40)), "partly outside root refused")
        verify(!gate.observe(1400, root, PickerRootStability.Bounds(0, 4, 0, 10)), "empty target refused")
        verify(!gate.observe(1700, PickerRootStability.Bounds(0, 0, 0, 0), target), "empty root refused")
        verify(!gate.observe(2000, root, target), "first after invalid")
        verify(!gate.observe(1900, root, target), "nonmonotonic clock resets")
        verify(!gate.observe(2150, root, target), "backwards sample did not start acceptance")
        verify(gate.observe(2400, root, target), "stable after reset")
        verify(runCatching { PickerRootStability(0) }.isFailure, "invalid threshold")
        println("R59 picker pre-tap stability host contracts: $count assertions; no Android navigation inferred")
    }
}
