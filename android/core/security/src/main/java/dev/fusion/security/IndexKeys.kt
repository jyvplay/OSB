package dev.fusion.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * Device-local index key adapter, not a portable backup key or a biometric gate.
 * Android Keystore does NOT imply TEE/StrongBox; hardware backing must be observed separately.
 * No plaintext/software-key fallback and no key creation on read/decrypt paths.
 * Caller must keep encrypted snapshots in noBackupFilesDir and must not call createForNewIndex
 * to repair missing keys for an existing ciphertext. Preserve those bytes and report unavailable.
 * // [U] verify on API 34/36/37: Keystore generation, invalidation, GCM nonce and KeyInfo policy.
 */
class IndexKeys(private val alias: String = "fusion.search.index.v1") {
    // One trusted owner process. This lock covers all adapter instances, not other processes.
    companion object { private val creationLock = Any() }
    init { require(alias.matches(Regex("[A-Za-z0-9._-]{1,80}"))) }
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun checked(key: SecretKey): SecretKey {
        val info = SecretKeyFactory.getInstance(key.algorithm,"AndroidKeyStore")
            .getKeySpec(key,KeyInfo::class.java) as KeyInfo
        check(key.algorithm == KeyProperties.KEY_ALGORITHM_AES && info.keySize == 256 &&
            KeyProperties.BLOCK_MODE_GCM in info.blockModes &&
            KeyProperties.ENCRYPTION_PADDING_NONE in info.encryptionPaddings) { "Index key policy differs" }
        return key
    }
    @Synchronized fun existing(): SecretKey {
        val entry = store().getEntry(alias,null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Index key is unavailable; ciphertext retained. No replacement key created.")
        return checked(entry.secretKey)
    }
    /** Explicit new-store operation only. An existing key is reused, never silently rotated. */
    fun createForNewIndex(): SecretKey = synchronized(creationLock) {
        if (store().containsAlias(alias)) return@synchronized existing()
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build())
        checked(generator.generateKey())
    }
}
