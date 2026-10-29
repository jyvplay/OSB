package dev.fusion.vault

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Constant-Work Plausible-Deniability Dual-Slot Vault Router (Apache-2.0).
 *
 * Evaluates BOTH encrypted key slots (`slot 0` and `slot 1`) on every unlock attempt so work
 * factor and branch footprint do not reveal whether two passphrases are configured; when the
 * duress slot matches, the primary key slot RAM image is immediately wiped before returning the
 * decoy payload!
 */
object DuressDecoyVaultRouter {
    const val SCOPE =
        "Dual-slot constant-work PBKDF2-SHA256 + AES-256-GCM plausible-deniability vault router with silent primary wipe on duress"

    enum class UnlockedSlot { PRIMARY, DURESS_DECOY, AUTH_FAILED }

    data class DualSlotHeader(
        val saltA: ByteArray,
        val ivA: ByteArray,
        val ciphertextSlotA: ByteArray,
        val saltB: ByteArray,
        val ivB: ByteArray,
        val ciphertextSlotB: ByteArray,
    )

    data class UnlockOutcome(
        val slot: UnlockedSlot,
        val unlockedMasterKey32: ByteArray?,
        val primaryWipedOnDuress: Boolean,
    )

    fun createDualVault(
        primaryPassphrase: CharArray,
        primaryMasterKey32: ByteArray,
        duressPassphrase: CharArray,
        decoyMasterKey32: ByteArray,
        saltA: ByteArray,
        ivA: ByteArray,
        saltB: ByteArray,
        ivB: ByteArray,
        iterations: Int = 20_000,
    ): DualSlotHeader {
        require(!primaryPassphrase.contentEquals(duressPassphrase)) {
            "Primary and duress passphrases must be distinct"
        }
        require(primaryMasterKey32.size == 32 && decoyMasterKey32.size == 32)
        val ctA = sealSlot(deriveKey(primaryPassphrase, saltA, iterations), ivA, byteArrayOf(0x01) + primaryMasterKey32)
        val ctB = sealSlot(deriveKey(duressPassphrase, saltB, iterations), ivB, byteArrayOf(0x02) + decoyMasterKey32)
        return DualSlotHeader(saltA, ivA, ctA, saltB, ivB, ctB)
    }

    fun attemptUnlock(
        candidatePassphrase: CharArray,
        header: DualSlotHeader,
        livePrimaryCacheToWipeOnDuress: ByteArray? = null,
        iterations: Int = 20_000,
    ): UnlockOutcome {
        // ALWAYS derive and attempt both slots so both key derivations execute unconditionally!
        val keyA = deriveKey(candidatePassphrase, header.saltA, iterations)
        val keyB = deriveKey(candidatePassphrase, header.saltB, iterations)
        val openA = tryOpenSlot(keyA, header.ivA, header.ciphertextSlotA)
        val openB = tryOpenSlot(keyB, header.ivB, header.ciphertextSlotB)
        keyA.fill(0)
        keyB.fill(0)

        return when {
            openB != null && openB.size == 33 && openB[0] == 0x02.toByte() -> {
                livePrimaryCacheToWipeOnDuress?.fill(0)
                openA?.fill(0)
                UnlockOutcome(UnlockedSlot.DURESS_DECOY, openB.copyOfRange(1, 33), primaryWipedOnDuress = true)
            }
            openA != null && openA.size == 33 && openA[0] == 0x01.toByte() -> {
                UnlockOutcome(UnlockedSlot.PRIMARY, openA.copyOfRange(1, 33), primaryWipedOnDuress = false)
            }
            else -> UnlockOutcome(UnlockedSlot.AUTH_FAILED, null, primaryWipedOnDuress = false)
        }
    }

    private fun deriveKey(pass: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pass, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun sealSlot(k32: ByteArray, iv12: ByteArray, pt: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k32, "AES"), GCMParameterSpec(128, iv12))
        c.updateAAD("FUSION-DUAL-VAULT-V1".toByteArray(StandardCharsets.US_ASCII))
        return c.doFinal(pt)
    }

    private fun tryOpenSlot(k32: ByteArray, iv12: ByteArray, ct: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(k32, "AES"), GCMParameterSpec(128, iv12))
        c.updateAAD("FUSION-DUAL-VAULT-V1".toByteArray(StandardCharsets.US_ASCII))
        c.doFinal(ct)
    } catch (_: Exception) {
        // Equalize dummy digest work on failure
        MessageDigest.getInstance("SHA-256").digest(ct)
        null
    }
}
