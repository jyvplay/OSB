// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

/**
 * Isolated-job lifecycle model shared by every sandboxed format worker (PDF preview, CSV/XLSX
 * operations, PNG copy). Fixed namespaces, explicit byte/step caps, input-bound outputs and
 * retire/replay checks. This is the job contract — real process isolation (isolatedProcess
 * UID, SELinux, FD ownership) is enforced by the Android host and proven by device tests,
 * never by this model alone.
 */
object SandboxJob {
    enum class Namespace(val id: String, val maxInputBytes: Long, val maxSteps: Long) {
        PARSE("parse", 2L * 1024 * 1024, 1_000_000L),
        RENDER("render", 2L * 1024 * 1024, 4_000_000L),
        CONVERT("convert", 8L * 1024 * 1024, 8_000_000L),
    }
    enum class Phase { QUEUED, RUNNING, SEALED, RETIRED, FAILED }

    data class Output(val bytes: ByteArray, val inputSha256: String, val steps: Long) {
        init {
            require(bytes.size <= 16 * 1024 * 1024 && inputSha256.matches(Regex("[0-9a-f]{64}")) && steps in 1..8_000_000L)
        }
        override fun equals(other: Any?) = other is Output && bytes.contentEquals(other.bytes) &&
            inputSha256 == other.inputSha256 && steps == other.steps
        override fun hashCode() = listOf(bytes.contentHashCode(), inputSha256, steps).hashCode()
    }

    class Job private constructor(
        val id: String,
        val namespace: Namespace,
        val inputSha256: String,
        val inputBytes: Long,
        val phase: Phase,
        val stepsUsed: Long,
        val output: Output?,
    ) {
        init {
            require(id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}")) && inputSha256.matches(Regex("[0-9a-f]{64}")))
            require(inputBytes in 1..namespace.maxInputBytes && stepsUsed in 0..namespace.maxSteps)
            require((phase == Phase.SEALED) == (output != null)) { "Only sealed jobs carry output" }
            require(output == null || (output.inputSha256 == inputSha256 && output.steps <= namespace.maxSteps)) { "Output must bind its input and budget" }
            require(phase != Phase.RETIRED || output == null) { "Retired jobs hold no output" }
        }

        fun start(): Job {
            require(phase == Phase.QUEUED) { "Only queued jobs start" }
            return copy(phase = Phase.RUNNING)
        }

        fun seal(bytes: ByteArray, steps: Long): Job {
            require(phase == Phase.RUNNING) { "Only running jobs seal" }
            require(steps in 1..namespace.maxSteps) { "Step budget exceeded" }
            return copy(phase = Phase.SEALED, stepsUsed = steps, output = Output(bytes.copyOf(), inputSha256, steps))
        }

        fun fail(): Job {
            require(phase == Phase.QUEUED || phase == Phase.RUNNING) { "Only open jobs fail" }
            return copy(phase = Phase.FAILED)
        }

        /** Retirement drops the output. Re-running needs the identical input digest (replay check). */
        fun retire(): Job {
            require(phase == Phase.SEALED || phase == Phase.FAILED) { "Only finished jobs retire" }
            return copy(phase = Phase.RETIRED, output = null)
        }

        fun replay(candidateInputSha256: String): Job {
            require(phase == Phase.RETIRED) { "Only retired jobs replay" }
            require(candidateInputSha256 == inputSha256) { "Replay needs the identical input" }
            return copy(phase = Phase.QUEUED, stepsUsed = 0)
        }

        private fun copy(phase: Phase = this.phase, stepsUsed: Long = this.stepsUsed, output: Output? = this.output) =
            Job(id, namespace, inputSha256, inputBytes, phase, stepsUsed, output)

        override fun equals(other: Any?) = other is Job && id == other.id && namespace == other.namespace &&
            inputSha256 == other.inputSha256 && inputBytes == other.inputBytes && phase == other.phase &&
            stepsUsed == other.stepsUsed && output == other.output
        override fun hashCode() = listOf(id, namespace, inputSha256, inputBytes, phase, stepsUsed, output).hashCode()

        companion object {
            fun queue(id: String, namespace: Namespace, input: ByteArray): Job {
                require(input.size in 1..namespace.maxInputBytes) { "Input outside ${namespace.id} bounds" }
                val digest = MessageDigest.getInstance("SHA-256").digest(input)
                    .joinToString("") { "%02x".format(it) }.lowercase(Locale.ROOT)
                return Job(id.lowercase(Locale.ROOT), namespace, digest, input.size.toLong(), Phase.QUEUED, 0, null)
            }
        }
    }

    /** Registry admits only fixed namespaces; unknown operations fail closed. */
    class Registry private constructor(val jobs: List<Job>) {
        val jobsView: List<Job> = Collections.unmodifiableList(jobs)
        init { require(jobs.size <= 64 && jobs.map { it.id }.distinct().size == jobs.size) }
        fun submit(job: Job): Registry {
            require(jobs.size < 64 && jobs.none { it.id == job.id })
            require(job.phase == Phase.QUEUED)
            return Registry(jobs + job)
        }
        fun update(job: Job): Registry {
            require(jobs.any { it.id == job.id })
            return Registry(jobs.map { if (it.id == job.id) job else it })
        }
        companion object { fun empty() = Registry(emptyList()) }
    }
}
