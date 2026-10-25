package dev.fusion.vault

/**
 * age-encryption recipient/identity STRING recognition (the age spec:
 * recipients are bech32 with HRP "age" carrying 32 bytes; identities
 * use HRP "AGE-SECRET-KEY-" uppercased). Built on [Bech32] plus the
 * standard 5→8 bit regrouping (strict: no leftover nonzero bits).
 *
 * Vault use: recognize-and-classify for redaction and storage hygiene
 * — NO key handling, NO encryption, and an identity match is reported
 * as SECRET-SHAPED so logging layers can refuse to persist it.
 *
 * Declared narrowings: string recognition only; X25519 math and the
 * age header format live nowhere in this repo by design.
 */
object AgeId {
    const val SCOPE = "Recognize age recipient/identity strings via Bech32 + 5->8 regroup; zero cryptography."

    fun convert5to8(data: IntArray): ByteArray {
        var acc = 0; var bits = 0
        val out = ArrayList<Byte>()
        for (v in data) {
            require(v in 0..31) { "5-bit value $v out of range" }
            acc = (acc shl 5) or v; bits += 5
            while (bits >= 8) { out.add(((acc shr (bits - 8)) and 0xFF).toByte()); bits -= 8 }
        }
        require(bits < 5 && (acc and ((1 shl bits) - 1)) == 0) { "nonzero padding bits in 5->8 regroup" }
        return out.toByteArray()
    }

    fun recipientPayload(s: String): ByteArray {
        val d = Bech32.decode(s)
        require(d.hrp == "age") { "HRP '${d.hrp}' is not an age recipient" }
        require(d.variant == Bech32.Variant.BECH32) { "age uses classic bech32" }
        val bytes = convert5to8(d.data)
        require(bytes.size == 32) { "age recipient payload must be 32 bytes, got ${bytes.size}" }
        return bytes
    }

    fun isRecipient(s: String): Boolean =
        try { recipientPayload(s); true } catch (_: IllegalArgumentException) { false }

    /** SECRET-SHAPED detector for log refusal (never decodes further on purpose). */
    fun looksLikeIdentity(s: String): Boolean = s.startsWith("AGE-SECRET-KEY-1")
}
