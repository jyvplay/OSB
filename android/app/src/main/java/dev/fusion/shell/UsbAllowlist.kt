// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import android.content.Context
import android.content.SharedPreferences

/**
 * R29 AUTHORED. Store logic executes on the host JVM against an in-memory
 * fake of the two Android APIs it touches (see R29Checks.usballow); the
 * SharedPreferences wiring itself needs Studio/CI compile plus
 * disposable-emulator verification before any behavior claim.
 *
 * Honest scope: a private allowlist of previously consented "vid:pid" pairs.
 * Membership only softens the receiver's ADVISORY verdict text (the combo
 * rule's already-allowed condition); every attach/detach is still recorded
 * and still notifies. It never authorizes hardware: a non-root app cannot
 * permit or block USB devices, and VID:PID pairs are spoofable, so an
 * allowlist hit means "seen and consented before", not "trusted hardware".
 */
object VidPid {
    private val FORM = Regex("[0-9a-f]{4}:[0-9a-f]{4}")

    fun valid(vid: Int, pid: Int) = vid in 0..0xFFFF && pid in 0..0xFFFF

    fun format(vid: Int, pid: Int): String {
        require(valid(vid, pid))
        return "%04x:%04x".format(vid, pid)
    }

    fun parse(s: String): Pair<Int, Int>? {
        if (!FORM.matches(s)) return null
        return s.substring(0, 4).toInt(16) to s.substring(5, 9).toInt(16)
    }
}

class UsbAllowlist(private val prefs: SharedPreferences) {
    companion object {
        const val PREFS = "usb-allowlist"
        const val KEY = "vids"
        const val MAX_ENTRIES = 64

        fun open(context: Context): UsbAllowlist =
            UsbAllowlist(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }

    fun snapshot(): Set<String> =
        prefs.getStringSet(KEY, mutableSetOf())?.filter { VidPid.parse(it) != null }?.toSet() ?: emptySet()

    fun contains(vid: Int, pid: Int): Boolean {
        require(VidPid.valid(vid, pid))
        return VidPid.format(vid, pid) in snapshot()
    }

    /** False only when the list is full and the pair is not already present. */
    fun add(vid: Int, pid: Int): Boolean {
        require(VidPid.valid(vid, pid))
        val cur = snapshot().toMutableSet()
        if (VidPid.format(vid, pid) in cur) return true
        if (cur.size >= MAX_ENTRIES) return false
        cur.add(VidPid.format(vid, pid))
        prefs.edit().putStringSet(KEY, cur).apply()
        return true
    }

    fun remove(vid: Int, pid: Int): Boolean {
        require(VidPid.valid(vid, pid))
        val cur = snapshot().toMutableSet()
        if (!cur.remove(VidPid.format(vid, pid))) return false
        prefs.edit().putStringSet(KEY, cur).apply()
        return true
    }

    fun clear() {
        prefs.edit().putStringSet(KEY, mutableSetOf()).apply()
    }
}
