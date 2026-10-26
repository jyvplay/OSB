// SPDX-License-Identifier: Apache-2.0
package dev.fusion.storage

/**
 * Per-scope byte quotas with explicit allocate/release accounting. Scopes are
 * caller-defined buckets ("drafts", "cache"); this ledger never touches the
 * filesystem, so callers must pair every allocate with real writes and every
 * delete with release, otherwise the ledger drifts from disk. Snapshot frames
 * let the host persist accounting across restarts.
 */
class QuotaLedger private constructor(
    private val quota: LinkedHashMap<String, Long>,
    private val used: LinkedHashMap<String, Long>
) {
    companion object {
        const val MAX_SCOPES = 64
        const val MAX_BYTES = 1024L * 1024 * 1024 * 1024
        const val MAGIC = 0x51554F31
        private val SCOPE = Regex("[a-z][a-z0-9_-]{0,31}")

        fun validScope(s: String) = SCOPE.matches(s)
        fun empty(): QuotaLedger = QuotaLedger(LinkedHashMap(), LinkedHashMap())

        fun readFrame(bytes: ByteArray): QuotaLedger {
            require(bytes.size in 8..100_000)
            val d = java.io.DataInputStream(bytes.inputStream())
            require(d.readInt() == MAGIC)
            val n = d.readInt(); require(n in 0..MAX_SCOPES)
            val q = LinkedHashMap<String, Long>(); val u = LinkedHashMap<String, Long>()
            repeat(n) {
                val sl = d.readInt(); require(sl in 1..64)
                val sb = ByteArray(sl); d.readFully(sb)
                val scope = String(sb, Charsets.UTF_8)
                val qq = d.readLong(); val uu = d.readLong()
                require(validScope(scope) && qq in 0..MAX_BYTES && uu in 0..qq)
                require(!q.containsKey(scope))
                q[scope] = qq; u[scope] = uu
            }
            require(d.available() == 0)
            return QuotaLedger(q, u)
        }
    }

    fun scopes(): Set<String> = quota.keys.toSet()
    fun limit(scope: String): Long? = quota[scope]
    fun usage(scope: String): Long = used[scope] ?: 0L
    fun free(scope: String): Long? = quota[scope]?.minus(used[scope] ?: 0L)

    fun setQuota(scope: String, bytes: Long) {
        require(validScope(scope) && bytes in 0..MAX_BYTES)
        if (!quota.containsKey(scope)) require(quota.size < MAX_SCOPES)
        require(bytes >= (used[scope] ?: 0L)) { "quota below current usage" }
        quota[scope] = bytes; used.putIfAbsent(scope, 0L)
    }

    fun removeScope(scope: String): Boolean {
        require((used[scope] ?: 0L) == 0L) { "scope still in use" }
        return quota.remove(scope) != null
    }

    fun allocate(scope: String, bytes: Long): Boolean {
        require(bytes in 1..MAX_BYTES)
        val q = quota[scope] ?: return false
        val u = used[scope] ?: 0L
        if (u > q - bytes) return false
        used[scope] = u + bytes
        return true
    }

    fun release(scope: String, bytes: Long) {
        require(bytes in 1..MAX_BYTES)
        val u = used[scope] ?: 0L
        used[scope] = maxOf(0L, u - bytes)
    }

    fun writeFrame(): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        val d = java.io.DataOutputStream(o)
        d.writeInt(MAGIC); d.writeInt(quota.size)
        for ((s, q) in quota) {
            val sb = s.toByteArray(Charsets.UTF_8); d.writeInt(sb.size); d.write(sb)
            d.writeLong(q); d.writeLong(used[s] ?: 0L)
        }
        d.flush()
        return o.toByteArray()
    }
}
