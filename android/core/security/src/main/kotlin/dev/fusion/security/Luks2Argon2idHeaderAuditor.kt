package dev.fusion.security

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Clean-room `LUKS2` (`LUKS\xba\xbe` version 2) Binary Header + JSON Metadata Security Auditor
 * aligned with **Tails 7.15.1** (`argon2id`, `memory >= 1_048_576` KiB [1 GiB], `time >= 4`,
 * `cpus >= 4`, `aes-xts-plain64` 512-bit = 64-byte volume key; PBKDF2 downgrade rejected!).
 */
object Luks2Argon2idHeaderAuditor {
    const val SCOPE =
        "LUKS2 binary header (LUKS\\xba\\xbe v2) + JSON keyslot auditor enforcing Tails 7.15 Argon2id >=1 GiB & 512-bit AES-XTS-plain64"

    private val LUKS_MAGIC = byteArrayOf('L'.code.toByte(), 'U'.code.toByte(), 'K'.code.toByte(), 'S'.code.toByte(), 0xBA.toByte(), 0xBE.toByte())
    const val TAILS_MIN_ARGON2ID_MEMORY_KIB = 1_048_576L // 1 GiB
    const val MIN_ITERATIONS = 4
    const val MIN_XTS_KEY_BYTES = 64 // 512-bit XTS (two 256-bit AES keys)

    data class AuditVerdict(
        val compliant: Boolean,
        val version: Int,
        val hashSpec: String,
        val kdfType: String,
        val argon2MemoryKiB: Long,
        val argon2TimeCost: Int,
        val cipherMode: String,
        val keySizeBytes: Int,
        val findings: List<String>,
    )

    fun buildSyntheticLuks2Header(
        kdfType: String = "argon2id",
        memoryKiB: Long = TAILS_MIN_ARGON2ID_MEMORY_KIB,
        timeCost: Int = 4,
        cpus: Int = 4,
        cipher: String = "aes-xts-plain64",
        keySizeBytes: Int = 64,
        hashAlg: String = "sha256",
    ): ByteArray {
        val json = """{"keyslots":{"0":{"type":"luks2","key_size":$keySizeBytes,"kdf":{"type":"$kdfType","time":$timeCost,"memory":$memoryKiB,"cpus":$cpus},"area":{"type":"raw","encryption":"$cipher","key_size":$keySizeBytes}}},"segments":{"0":{"type":"crypt","encryption":"$cipher","sector_size":4096}}}"""
        val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)
        val hdrSize = 4096 + align512(jsonBytes.size + 1)
        val out = ByteArray(hdrSize)
        val bb = ByteBuffer.wrap(out).order(ByteOrder.BIG_ENDIAN)
        bb.put(LUKS_MAGIC)
        bb.putShort(2) // version 2
        bb.putLong(hdrSize.toLong())
        bb.putLong(1L) // seqid
        bb.position(88)
        val hashPadded = hashAlg.toByteArray(StandardCharsets.US_ASCII).copyOf(32)
        bb.put(hashPadded)
        System.arraycopy(jsonBytes, 0, out, 4096, jsonBytes.size)
        return out
    }

    fun auditHeader(headerBytes: ByteArray): AuditVerdict {
        val findings = mutableListOf<String>()
        if (headerBytes.size < 4160) {
            return AuditVerdict(false, 0, "", "", 0L, 0, "", 0, listOf("TRUNCATED_LUKS2_HEADER"))
        }
        val magicOk = LUKS_MAGIC.indices.all { headerBytes[it] == LUKS_MAGIC[it] }
        if (!magicOk) findings.add("INVALID_LUKS_MAGIC")
        val bb = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
        val version = bb.getShort(6).toInt() and 0xFFFF
        if (version != 2) findings.add("UNSUPPORTED_LUKS_VERSION_$version")
        val hashSpec = String(headerBytes, 88, 32, StandardCharsets.US_ASCII).trimEnd('\u0000')
        if (hashSpec.lowercase() in setOf("sha1", "md5", "ripemd160")) {
            findings.add("WEAK_HEADER_CHECKSUM_$hashSpec")
        }

        val jsonEnd = (4096 until headerBytes.size).firstOrNull { headerBytes[it] == 0.toByte() } ?: headerBytes.size
        val json = String(headerBytes, 4096, jsonEnd - 4096, StandardCharsets.UTF_8)

        val kdfType = Regex(""""kdf"\s*:\s*\{[^}]*?"type"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1).orEmpty()
        val memKiB = Regex(""""memory"\s*:\s*([0-9]+)""").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timeCost = Regex(""""time"\s*:\s*([0-9]+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val cipher = Regex(""""encryption"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1).orEmpty()
        val keySize = Regex(""""key_size"\s*:\s*([0-9]+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        if (kdfType != "argon2id") findings.add("WEAK_OR_DOWNGRADED_KDF_$kdfType")
        if (kdfType == "argon2id" && memKiB < TAILS_MIN_ARGON2ID_MEMORY_KIB) {
            findings.add("ARGON2ID_MEMORY_BELOW_1GIB_${memKiB}KIB")
        }
        if (timeCost < MIN_ITERATIONS) findings.add("KDF_TIME_COST_TOO_LOW_$timeCost")
        if (cipher != "aes-xts-plain64") findings.add("NON_CANONICAL_XTS_CIPHER_$cipher")
        if (keySize < MIN_XTS_KEY_BYTES) findings.add("INSUFFICIENT_XTS_KEY_SIZE_${keySize}B")

        return AuditVerdict(
            compliant = findings.isEmpty(),
            version = version,
            hashSpec = hashSpec,
            kdfType = kdfType,
            argon2MemoryKiB = memKiB,
            argon2TimeCost = timeCost,
            cipherMode = cipher,
            keySizeBytes = keySize,
            findings = findings,
        )
    }

    private fun align512(n: Int): Int = (n + 511) and 511.inv()
}
