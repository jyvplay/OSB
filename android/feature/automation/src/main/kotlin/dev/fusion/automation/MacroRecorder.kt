// SPDX-License-Identifier: Apache-2.0
package dev.fusion.automation

/**
 * Bounded macro recorder. Records a sequence of named macro steps with
 * optional parameter maps, then supports bounded playback by replaying
 * step names in order. Steps are bounded in count, name length, parameter
 * count, and total size. Recording can be paused/resumed; playback does
 * not execute any action — it returns the step sequence for the automation
 * engine to interpret.
 *
 * Declared narrowings: at most [MAX_STEPS] steps; step names at most
 * [MAX_NAME_LEN] chars matching [NAME_RE]; at most [MAX_PARAMS] parameters
 * per step; parameter keys and values at most [MAX_PARAM_LEN] chars;
 * total recorded bytes at most [MAX_TOTAL_BYTES].
 */
class MacroRecorder private constructor(
    private val steps: MutableList<MacroStep>,
    private var recording: Boolean,
    private var totalBytes: Int
) {
    companion object {
        const val MAX_STEPS = 1024
        const val MAX_NAME_LEN = 128
        const val MAX_PARAMS = 16
        const val MAX_PARAM_LEN = 256
        const val MAX_TOTAL_BYTES = 1 << 16  // 64 KiB
        private val NAME_RE = Regex("[a-z][a-z0-9_]{0,63}")

        data class MacroStep(
            val name: String,
            val params: Map<String, String>
        )

        data class Playback(
            val steps: List<MacroStep>,
            val totalBytes: Int
        )

        fun empty(): MacroRecorder =
            MacroRecorder(mutableListOf(), false, 0)
    }

    fun startRecording() {
        require(steps.isEmpty()) { "cannot start with existing steps" }
        recording = true
        totalBytes = 0
    }

    fun stopRecording(): Playback {
        require(recording) { "not recording" }
        recording = false
        return Playback(steps.toList(), totalBytes)
    }

    fun addStep(name: String, params: Map<String, String> = emptyMap()) {
        require(recording) { "not recording" }
        require(NAME_RE.matches(name)) { "invalid step name: $name" }
        require(name.length <= MAX_NAME_LEN) { "step name too long" }
        require(steps.size < MAX_STEPS) { "too many steps: $MAX_STEPS" }
        require(params.size <= MAX_PARAMS) { "too many params: $MAX_PARAMS" }
        for ((k, v) in params) {
            require(k.length <= MAX_PARAM_LEN && v.length <= MAX_PARAM_LEN) {
                "param too long"
            }
        }
        var stepBytes = name.length
        for ((k, v) in params) stepBytes += k.length + v.length
        require(totalBytes + stepBytes <= MAX_TOTAL_BYTES) { "macro too large" }
        totalBytes += stepBytes
        steps.add(MacroStep(name, params.toMap()))
    }

    fun pause() { recording = false }
    fun resume() { recording = true }
    fun isRecording(): Boolean = recording
    fun stepCount(): Int = steps.size
    fun clear() { steps.clear(); totalBytes = 0; recording = false }
}
