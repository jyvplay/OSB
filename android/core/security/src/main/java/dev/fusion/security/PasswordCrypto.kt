package dev.fusion.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based AES-256-GCM for portable backups.
 *
 * Deliberately NOT Keystore-backed: a Keystore key cannot leave the device, so a
 * backup sealed with it could never be restored elsewhere. The user holds the
 * password; there is no escrow and no recovery if it is lost.
 *
 * Container: "FSB1" | salt(16) | iv(12) | ciphertext+tag
 * Pure JDK crypto, so this is unit-testable on the JVM.
 */
object PasswordCrypto {
    private const val MAGIC = "FSB1"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    class BadContainer(message: String) : Exception(message)

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(password: CharArray, plaintext: ByteArray): ByteArray {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(rnd::nextBytes)
        val iv = ByteArray(IV_LEN).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(plaintext)
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        return ByteArray(magic.size + SALT_LEN + IV_LEN + body.size).also { out ->
            magic.copyInto(out, 0)
            salt.copyInto(out, magic.size)
            iv.copyInto(out, magic.size + SALT_LEN)
            body.copyInto(out, magic.size + SALT_LEN + IV_LEN)
        }
    }

    fun decrypt(password: CharArray, container: ByteArray): ByteArray {
        val magic = MAGIC.toByteArray(Charsets.US_ASCII)
        val headerLen = magic.size + SALT_LEN + IV_LEN
        if (container.size <= headerLen) throw BadContainer("File is too small to be a Fusion backup")
        if (!container.copyOfRange(0, magic.size).contentEquals(magic)) {
            throw BadContainer("Not a Fusion backup (bad magic header)")
        }
        val salt = container.copyOfRange(magic.size, magic.size + SALT_LEN)
        val iv = container.copyOfRange(magic.size + SALT_LEN, headerLen)
        val body = container.copyOfRange(headerLen, container.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        // GCM tag failure throws AEADBadTagException: wrong password or tampered file.
        return cipher.doFinal(body)
    }
}
