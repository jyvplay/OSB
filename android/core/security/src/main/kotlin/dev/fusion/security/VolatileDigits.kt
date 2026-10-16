package dev.fusion.security

/** Fixed-capacity, UI-owned decimal scratch buffer. Not a credential store or secure-memory API.
 * Caller supplies elapsed realtime, serializes calls and MUST clear on focus/background/teardown.
 * Strings returned for display and JVM/platform copies cannot be reliably erased by this class.
 */
class VolatileDigits {
    companion object { const val CAPACITY = 64; const val IDLE_MS = 60_000L }
    private val buffer = CharArray(CAPACITY)
    private var used = 0
    private var lastChange: Long? = null

    fun clear() { buffer.fill('\u0000'); used = 0; lastChange = null }

    /** A reversed/invalid clock is a discard, not a renewed lease. Deadline equality expires. */
    fun expire(now: Long): Boolean {
        val last = lastChange ?: return false
        if (now < last || now - last >= IDLE_MS) { clear(); return true }
        return false
    }

    fun append(digit: Char, now: Long): Boolean {
        expire(now)
        require(now >= 0 && digit in '0'..'9') { "Nonnegative monotonic time and decimal digit required" }
        if (used == CAPACITY) return false
        buffer[used++] = digit; lastChange = now; return true
    }

    fun backspace(now: Long): Boolean {
        expire(now)
        require(now >= 0)
        if (used == 0) return false
        buffer[--used] = '\u0000'
        lastChange = if (used == 0) null else now
        return true
    }

    fun size(now: Long): Int { expire(now); return used }
    fun reveal(now: Long): String { expire(now); return buffer.concatToString(0, used) }
    fun remainingMs(now: Long): Long {
        expire(now)
        return lastChange?.let { IDLE_MS - (now - it) } ?: 0
    }
}
