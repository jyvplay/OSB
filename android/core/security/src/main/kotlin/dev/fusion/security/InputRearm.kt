// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/** A topology/configuration change is a reason to discard, not evidence that an implant was detected.
 * Only a fresh explicit restart may acknowledge the current epoch. Focus/resume does not rearm it.
 */
class InputRearm {
    var epoch = 0L; private set
    var armed = true; private set
    fun changed(): Long { check(epoch < Long.MAX_VALUE); epoch++; armed = false; return epoch }
    fun restart(observedEpoch: Long): Boolean {
        if (observedEpoch != epoch) return false
        armed = true; return true
    }
    fun permits(callbackEpoch: Long): Boolean = armed && callbackEpoch == epoch
}
