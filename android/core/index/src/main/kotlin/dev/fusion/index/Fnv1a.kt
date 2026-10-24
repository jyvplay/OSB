package dev.fusion.index

/**
 * FNV-1a hashing for index bucket assignment (Fowler–Noll–Vo, parameters
 * from the published FNV reference: 32-bit offset 2166136261 / prime
 * 16777619; 64-bit offset 14695981039346656037 / prime 1099511628211).
 *
 * FNV-1a order: XOR the byte first, THEN multiply — the tests pin the
 * published zero-hash and single-byte vectors that distinguish 1a from
 * plain FNV-1.
 *
 * Declared narrowings: non-cryptographic by definition — the KDoc and
 * SCOPE say so, and nothing security-adjacent may import this for
 * anything but bucketing/sharding. Strings hash as UTF-8 bytes.
 */
object Fnv1a {
    const val SCOPE = "Non-cryptographic bucket hashing only; never for integrity or authentication."
    const val OFFSET_32 = 0x811C9DC5.toInt()
    const val PRIME_32 = 16777619
    const val OFFSET_64 = -3750763034362895579L   // 14695981039346656037 as signed
    const val PRIME_64 = 1099511628211L

    fun hash32(bytes: ByteArray): Int {
        var h = OFFSET_32
        for (b in bytes) {
            h = h xor (b.toInt() and 0xFF)
            h *= PRIME_32
        }
        return h
    }

    fun hash64(bytes: ByteArray): Long {
        var h = OFFSET_64
        for (b in bytes) {
            h = h xor (b.toLong() and 0xFF)
            h *= PRIME_64
        }
        return h
    }

    fun hash32(text: String): Int = hash32(text.toByteArray(Charsets.UTF_8))
    fun hash64(text: String): Long = hash64(text.toByteArray(Charsets.UTF_8))

    /** Non-negative bucket in [0, buckets). */
    fun bucket(text: String, buckets: Int): Int {
        require(buckets > 0) { "buckets $buckets" }
        return Math.floorMod(hash32(text), buckets)
    }
}
